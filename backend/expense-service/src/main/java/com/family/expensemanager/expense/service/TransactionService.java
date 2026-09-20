package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.event.ExpenseEvent;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.expense.dao.BudgetDao;
import com.family.expensemanager.expense.dao.TransactionDao;
import com.family.expensemanager.expense.domain.entity.Budget;
import com.family.expensemanager.expense.domain.entity.Category;
import com.family.expensemanager.expense.domain.entity.Transaction;
import com.family.expensemanager.expense.domain.entity.Wallet;
import com.family.expensemanager.expense.dto.ReceiptFile;
import com.family.expensemanager.expense.dto.TransactionReportFilter;
import com.family.expensemanager.expense.dto.TransactionRequest;
import com.family.expensemanager.expense.dto.TransactionResponse;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "TransactionService")
public class TransactionService {

    private static final String TYPE_EXPENSE = "EXPENSE";
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_RECEIPT_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final TransactionDao transactionDao;
    private final BudgetDao budgetDao;
    private final WalletService walletService;
    private final CategoryService categoryService;
    private final ApplicationEventPublisher eventPublisher;
    private final CacheManager cacheManager;
    private final ReceiptStorageService receiptStorageService;

    @Transactional
    public TransactionResponse create(
            Long familyId, Long userId, String userEmail, String userDisplayName, TransactionRequest request) {
        log.info("create - start, familyId={}, userId={}", familyId, userId);
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
                transaction.getAmount(), null, null, null, null, null, null, Instant.now()));

        if (TYPE_EXPENSE.equals(request.type())) {
            checkBudgetCrossing(familyId, userId, userEmail, userDisplayName, transaction, category, periodMonth,
                    totalBefore);
        }

        return TransactionResponse.from(transaction);
    }

    /**
     * Paginated + filtered transaction list for the Transactions page (see README
     * tech-debt item this replaces: the old endpoint returned every transaction in the
     * family and left filtering/paging to client-side JS, which didn't scale).
     */
    public PageResponse<TransactionResponse> listByFamilyPaged(
            Long familyId, TransactionReportFilter filter, int page, int size) {
        log.info("listByFamilyPaged - start, familyId={}, page={}, size={}", familyId, page, size);
        if (page < 0) {
            throw new BadRequestException("page phải >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size phải trong khoảng 1-" + MAX_PAGE_SIZE);
        }
        long totalElements = transactionDao.countByFamilyIdFiltered(
                familyId, filter.walletId(), filter.categoryId(), filter.type(), filter.fromDate(), filter.toDate());
        List<TransactionResponse> content = transactionDao.selectByFamilyIdFiltered(
                        familyId, filter.walletId(), filter.categoryId(), filter.type(), filter.fromDate(),
                        filter.toDate(), size, page * size)
                .stream().map(TransactionResponse::from).toList();
        return PageResponse.of(content, page, size, totalElements);
    }

    public TransactionResponse get(Long familyId, Long transactionId) {
        log.info("get - start, familyId={}, transactionId={}", familyId, transactionId);
        return TransactionResponse.from(requireOwnedByFamily(transactionId, familyId));
    }

    @Transactional
    public TransactionResponse update(Long familyId, Long transactionId, TransactionRequest request) {
        log.info("update - start, familyId={}, transactionId={}", familyId, transactionId);
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

    /**
     * Soft-delete (README "10. Xoá là mất vĩnh viễn") — the row and its receipt file
     * both stay in place, just hidden from normal queries, so {@link #restore} can bring
     * a mistaken delete back exactly as it was.
     */
    @Transactional
    public void delete(Long familyId, Long transactionId) {
        log.info("delete - start, familyId={}, transactionId={}", familyId, transactionId);
        Transaction transaction = requireOwnedByFamily(transactionId, familyId);
        transaction.setDeletedAt(LocalDateTime.now());
        transactionDao.update(transaction);
        evictCaches(familyId, periodMonthOf(transaction.getOccurredAt()));
    }

    public PageResponse<TransactionResponse> listDeletedPaged(Long familyId, int page, int size) {
        log.info("listDeletedPaged - start, familyId={}, page={}, size={}", familyId, page, size);
        if (page < 0) {
            throw new BadRequestException("page phải >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size phải trong khoảng 1-" + MAX_PAGE_SIZE);
        }
        long totalElements = transactionDao.countDeletedByFamilyId(familyId);
        List<TransactionResponse> content = transactionDao.selectDeletedByFamilyIdPaged(familyId, size, page * size)
                .stream().map(TransactionResponse::from).toList();
        return PageResponse.of(content, page, size, totalElements);
    }

    @Transactional
    public void restore(Long familyId, Long transactionId) {
        log.info("restore - start, familyId={}, transactionId={}", familyId, transactionId);
        if (transactionDao.restore(transactionId, familyId) == 0) {
            throw new NotFoundException("Giao dịch đã xoá không tồn tại: " + transactionId);
        }
        Transaction restored = requireOwnedByFamily(transactionId, familyId);
        evictCaches(familyId, periodMonthOf(restored.getOccurredAt()));
    }

    @Transactional
    public TransactionResponse uploadReceipt(Long familyId, Long transactionId, MultipartFile file) {
        log.info("uploadReceipt - start, familyId={}, transactionId={}", familyId, transactionId);
        if (file.isEmpty()) {
            throw new BadRequestException("File ảnh trống");
        }
        if (!ALLOWED_RECEIPT_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BadRequestException("Chỉ chấp nhận ảnh JPEG, PNG hoặc WEBP");
        }
        Transaction transaction = requireOwnedByFamily(transactionId, familyId);
        String oldPath = transaction.getReceiptPath();

        String newPath;
        try {
            newPath = receiptStorageService.save(familyId, transactionId, file);
        } catch (IOException e) {
            throw new UncheckedIOException("Không lưu được ảnh hoá đơn", e);
        }
        transaction.setReceiptPath(newPath);
        transaction.setReceiptContentType(file.getContentType());
        transactionDao.update(transaction);

        // Only remove the old file once the new one — and the DB row pointing to it —
        // are both committed, so a mid-upload failure never leaves a transaction with
        // no receipt at all.
        if (oldPath != null) {
            receiptStorageService.delete(oldPath);
        }
        return TransactionResponse.from(transaction);
    }

    public ReceiptFile getReceipt(Long familyId, Long transactionId) {
        log.info("getReceipt - start, familyId={}, transactionId={}", familyId, transactionId);
        Transaction transaction = requireOwnedByFamily(transactionId, familyId);
        if (transaction.getReceiptPath() == null) {
            throw new NotFoundException("Giao dịch chưa có ảnh hoá đơn");
        }
        try {
            byte[] content = receiptStorageService.read(transaction.getReceiptPath());
            return new ReceiptFile(content, transaction.getReceiptContentType());
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được ảnh hoá đơn", e);
        }
    }

    @Transactional
    public void deleteReceipt(Long familyId, Long transactionId) {
        log.info("deleteReceipt - start, familyId={}, transactionId={}", familyId, transactionId);
        Transaction transaction = requireOwnedByFamily(transactionId, familyId);
        if (transaction.getReceiptPath() == null) {
            return;
        }
        String path = transaction.getReceiptPath();
        transaction.setReceiptPath(null);
        transaction.setReceiptContentType(null);
        transactionDao.update(transaction);
        receiptStorageService.delete(path);
    }

    private void checkBudgetCrossing(Long familyId, Long userId, String userEmail, String userDisplayName,
                                      Transaction transaction, Category category, String periodMonth,
                                      BigDecimal totalBefore) {
        log.info("checkBudgetCrossing - start, familyId={}, categoryId={}, periodMonth={}",
                familyId, category.getId(), periodMonth);
        Optional<Budget> budget = budgetDao.selectByCategoryAndPeriod(category.getId(), periodMonth);
        budget.ifPresent(b -> {
            BigDecimal totalAfter = totalBefore.add(transaction.getAmount());
            if (totalBefore.compareTo(b.getLimitAmount()) <= 0 && totalAfter.compareTo(b.getLimitAmount()) > 0) {
                eventPublisher.publishEvent(new ExpenseEvent(
                        ExpenseEvent.BUDGET_EXCEEDED, familyId, userId, transaction.getId(), category.getId(),
                        transaction.getAmount(), periodMonth, b.getLimitAmount(), totalAfter, category.getName(),
                        userEmail, userDisplayName, Instant.now()));
            }
        });
    }

    private Transaction requireOwnedByFamily(Long transactionId, Long familyId) {
        log.info("requireOwnedByFamily - start, transactionId={}, familyId={}", transactionId, familyId);
        Transaction transaction = transactionDao.selectById(transactionId)
                .orElseThrow(() -> new NotFoundException("Giao dịch không tồn tại: " + transactionId));
        if (!transaction.getFamilyId().equals(familyId)) {
            throw new NotFoundException("Giao dịch không tồn tại: " + transactionId);
        }
        return transaction;
    }

    private void evictCaches(Long familyId, String periodMonth) {
        log.info("evictCaches - start, familyId={}, periodMonth={}", familyId, periodMonth);
        String key = familyId + ":" + periodMonth;
        Cache summaryCache = cacheManager.getCache("expense:summary");
        Cache reportCache = cacheManager.getCache("expense:report:category");
        Cache walletCategoryCache = cacheManager.getCache("expense:report:wallet-category");
        if (summaryCache != null) {
            summaryCache.evict(key);
        }
        if (reportCache != null) {
            reportCache.evict(key);
        }
        if (walletCategoryCache != null) {
            walletCategoryCache.evict(key);
        }
    }

    private String periodMonthOf(LocalDateTime occurredAt) {
        log.info("periodMonthOf - start");
        return occurredAt.toLocalDate().toString().substring(0, 7);
    }
}
