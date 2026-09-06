package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.event.ExpenseEvent;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.expense.dao.BudgetDao;
import com.family.expensemanager.expense.dao.TransactionDao;
import com.family.expensemanager.expense.domain.entity.Budget;
import com.family.expensemanager.expense.domain.entity.Category;
import com.family.expensemanager.expense.domain.entity.Transaction;
import com.family.expensemanager.expense.domain.entity.Wallet;
import com.family.expensemanager.expense.dto.TransactionRequest;
import com.family.expensemanager.expense.dto.TransactionResponse;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class TransactionService {

    private static final String TYPE_EXPENSE = "EXPENSE";

    private final TransactionDao transactionDao;
    private final BudgetDao budgetDao;
    private final WalletService walletService;
    private final CategoryService categoryService;
    private final ApplicationEventPublisher eventPublisher;
    private final CacheManager cacheManager;

    public TransactionService(TransactionDao transactionDao,
                               BudgetDao budgetDao,
                               WalletService walletService,
                               CategoryService categoryService,
                               ApplicationEventPublisher eventPublisher,
                               CacheManager cacheManager) {
        this.transactionDao = transactionDao;
        this.budgetDao = budgetDao;
        this.walletService = walletService;
        this.categoryService = categoryService;
        this.eventPublisher = eventPublisher;
        this.cacheManager = cacheManager;
    }

    @Transactional
    public TransactionResponse create(Long familyId, Long userId, TransactionRequest request) {
        Wallet wallet = walletService.requireOwnedByFamily(request.walletId(), familyId);
        Category category = categoryService.requireOwnedByFamily(request.categoryId(), familyId);

        Transaction transaction = new Transaction();
        transaction.setWalletId(wallet.getId());
        transaction.setCategoryId(category.getId());
        transaction.setFamilyId(familyId);
        transaction.setUserId(userId);
        transaction.setType(request.type());
        transaction.setAmount(request.amount());
        transaction.setOccurredAt(request.occurredAt());
        transaction.setNote(request.note());

        String periodMonth = periodMonthOf(request.occurredAt());
        BigDecimal totalBefore = TYPE_EXPENSE.equals(request.type())
                ? transactionDao.sumAmountByCategoryPeriodAndType(familyId, category.getId(), periodMonth, TYPE_EXPENSE)
                : BigDecimal.ZERO;

        transactionDao.insert(transaction);
        evictCaches(familyId, periodMonth);

        eventPublisher.publishEvent(new ExpenseEvent(
                ExpenseEvent.EXPENSE_CREATED, familyId, userId, transaction.getId(), category.getId(),
                transaction.getAmount(), null, null, null, Instant.now()));

        if (TYPE_EXPENSE.equals(request.type())) {
            checkBudgetCrossing(familyId, userId, transaction, category.getId(), periodMonth, totalBefore);
        }

        return TransactionResponse.from(transaction);
    }

    public List<TransactionResponse> listByFamily(Long familyId) {
        return transactionDao.selectByFamilyId(familyId).stream().map(TransactionResponse::from).toList();
    }

    public TransactionResponse get(Long familyId, Long transactionId) {
        return TransactionResponse.from(requireOwnedByFamily(transactionId, familyId));
    }

    @Transactional
    public TransactionResponse update(Long familyId, Long transactionId, TransactionRequest request) {
        Transaction transaction = requireOwnedByFamily(transactionId, familyId);
        Wallet wallet = walletService.requireOwnedByFamily(request.walletId(), familyId);
        Category category = categoryService.requireOwnedByFamily(request.categoryId(), familyId);

        String oldPeriodMonth = periodMonthOf(transaction.getOccurredAt());

        transaction.setWalletId(wallet.getId());
        transaction.setCategoryId(category.getId());
        transaction.setType(request.type());
        transaction.setAmount(request.amount());
        transaction.setOccurredAt(request.occurredAt());
        transaction.setNote(request.note());
        transactionDao.update(transaction);

        String newPeriodMonth = periodMonthOf(request.occurredAt());
        evictCaches(familyId, oldPeriodMonth);
        if (!oldPeriodMonth.equals(newPeriodMonth)) {
            evictCaches(familyId, newPeriodMonth);
        }

        return TransactionResponse.from(transaction);
    }

    @Transactional
    public void delete(Long familyId, Long transactionId) {
        Transaction transaction = requireOwnedByFamily(transactionId, familyId);
        transactionDao.delete(transaction);
        evictCaches(familyId, periodMonthOf(transaction.getOccurredAt()));
    }

    private void checkBudgetCrossing(Long familyId, Long userId, Transaction transaction, Long categoryId,
                                      String periodMonth, BigDecimal totalBefore) {
        Optional<Budget> budget = budgetDao.selectByCategoryAndPeriod(categoryId, periodMonth);
        budget.ifPresent(b -> {
            BigDecimal totalAfter = totalBefore.add(transaction.getAmount());
            if (totalBefore.compareTo(b.getLimitAmount()) <= 0 && totalAfter.compareTo(b.getLimitAmount()) > 0) {
                eventPublisher.publishEvent(new ExpenseEvent(
                        ExpenseEvent.BUDGET_EXCEEDED, familyId, userId, transaction.getId(), categoryId,
                        transaction.getAmount(), periodMonth, b.getLimitAmount(), totalAfter, Instant.now()));
            }
        });
    }

    private Transaction requireOwnedByFamily(Long transactionId, Long familyId) {
        Transaction transaction = transactionDao.selectById(transactionId)
                .orElseThrow(() -> new NotFoundException("Giao dịch không tồn tại: " + transactionId));
        if (!transaction.getFamilyId().equals(familyId)) {
            throw new NotFoundException("Giao dịch không tồn tại: " + transactionId);
        }
        return transaction;
    }

    private void evictCaches(Long familyId, String periodMonth) {
        String key = familyId + ":" + periodMonth;
        Cache summaryCache = cacheManager.getCache("expense:summary");
        Cache reportCache = cacheManager.getCache("expense:report:category");
        if (summaryCache != null) {
            summaryCache.evict(key);
        }
        if (reportCache != null) {
            reportCache.evict(key);
        }
    }

    private String periodMonthOf(LocalDateTime occurredAt) {
        return occurredAt.toLocalDate().toString().substring(0, 7);
    }
}
