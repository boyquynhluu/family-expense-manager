package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.event.ExpenseEvent;
import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.common.exception.ServiceException;
import com.family.expensemanager.expense.dao.BudgetDao;
import com.family.expensemanager.expense.dao.TransactionDao;
import com.family.expensemanager.expense.domain.entity.Budget;
import com.family.expensemanager.expense.domain.entity.Category;
import com.family.expensemanager.expense.domain.entity.Transaction;
import com.family.expensemanager.expense.domain.entity.Wallet;
import com.family.expensemanager.expense.dto.BulkDeleteResult;
import com.family.expensemanager.expense.dto.ReceiptFile;
import com.family.expensemanager.expense.dto.TransactionReportFilter;
import com.family.expensemanager.expense.dto.TransactionRequest;
import com.family.expensemanager.expense.dto.TransactionResponse;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.family.expensemanager.common.exception.ExceptionLogger.logged;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j(topic = "TransactionService")
public class TransactionService {

    private static final String TYPE_EXPENSE = "EXPENSE";
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_BULK_DELETE = 100;
    private static final int MAX_CREATOR_NAME_LENGTH = 100;
    private static final Set<String> ALLOWED_RECEIPT_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final TransactionDao transactionDao;
    private final BudgetDao budgetDao;
    private final WalletService walletService;
    private final CategoryService categoryService;
    private final ApplicationEventPublisher eventPublisher;
    private final CacheManager cacheManager;
    private final ReceiptStorageService receiptStorageService;

    public TransactionResponse create(
            Long familyId, Long userId, String userEmail, String userDisplayName, TransactionRequest request) {
        try {
            log.info("create - start, familyId={}, userId={}", familyId, userId);
            Wallet wallet = walletService.requireOwnedByFamily(request.walletId(), familyId);
            Category category = categoryService.requireOwnedByFamily(request.categoryId(), familyId, request.type());

            Transaction transaction = new Transaction();
            transaction.setWalletId(wallet.getId());
            transaction.setCategoryId(category.getId());
            transaction.setFamilyId(familyId);
            transaction.setUserId(userId);
            transaction.setCreatedByName(truncateName(userDisplayName));
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
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransactionService.create", e);
        }
    }

    /**
     * Paginated + filtered transaction list for the Transactions page (see README
     * tech-debt item this replaces: the old endpoint returned every transaction in the
     * family and left filtering/paging to client-side JS, which didn't scale).
     */
    public PageResponse<TransactionResponse> listByFamilyPaged(
            Long familyId, TransactionReportFilter filter, int page, int size) {
        try {
            log.info("listByFamilyPaged - start, familyId={}, page={}, size={}", familyId, page, size);
            if (page < 0) {
                throw logged(log, new BadRequestException("page phải >= 0"));
            }
            if (size < 1 || size > MAX_PAGE_SIZE) {
                throw logged(log, new BadRequestException("size phải trong khoảng 1-" + MAX_PAGE_SIZE));
            }
            filter.validate();
            String notePattern = filter.noteLikePattern();
            long totalElements = transactionDao.countByFamilyIdFiltered(
                    familyId, filter.walletId(), filter.categoryId(), filter.type(), filter.fromDate(), filter.toDate(),
                    notePattern, filter.minAmount(), filter.maxAmount());
            List<TransactionResponse> content = transactionDao.selectByFamilyIdFiltered(
                            familyId, filter.walletId(), filter.categoryId(), filter.type(), filter.fromDate(),
                            filter.toDate(), notePattern, filter.minAmount(), filter.maxAmount(), size, page * size)
                    .stream().map(TransactionResponse::from).toList();
            return PageResponse.of(content, page, size, totalElements);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransactionService.listByFamilyPaged", e);
        }
    }

    public TransactionResponse get(Long familyId, Long transactionId) {
        try {
            log.info("get - start, familyId={}, transactionId={}", familyId, transactionId);
            return TransactionResponse.from(requireOwnedByFamily(transactionId, familyId));
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransactionService.get", e);
        }
    }

    public TransactionResponse update(
            Long familyId, Long transactionId, Long callerUserId, boolean callerIsOwner, TransactionRequest request) {
        try {
            log.info("update - start, familyId={}, transactionId={}", familyId, transactionId);
            Transaction transaction = requireOwnedByFamily(transactionId, familyId);
            requireCanModify(transaction, callerUserId, callerIsOwner);
            Wallet wallet = walletService.requireOwnedByFamily(request.walletId(), familyId);
            Category category = categoryService.requireOwnedByFamily(request.categoryId(), familyId, request.type());

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
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransactionService.update", e);
        }
    }

    /**
     * Soft-delete (README "10. Xoá là mất vĩnh viễn") — the row and its receipt file
     * both stay in place, just hidden from normal queries, so {@link #restore} can bring
     * a mistaken delete back exactly as it was.
     */
    public void delete(Long familyId, Long transactionId, Long callerUserId, boolean callerIsOwner) {
        try {
            log.info("delete - start, familyId={}, transactionId={}", familyId, transactionId);
            Transaction transaction = requireOwnedByFamily(transactionId, familyId);
            requireCanModify(transaction, callerUserId, callerIsOwner);
            transaction.setDeletedAt(LocalDateTime.now());
            transactionDao.update(transaction);
            evictCaches(familyId, periodMonthOf(transaction.getOccurredAt()));
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransactionService.delete", e);
        }
    }

    public BulkDeleteResult bulkDelete(Long familyId, List<Long> ids, Long callerUserId, boolean callerIsOwner) {
        try {
            log.info("bulkDelete - start, familyId={}, count={}", familyId, ids.size());
            if (ids.size() > MAX_BULK_DELETE) {
                throw logged(log, new BadRequestException("Chỉ được xoá tối đa " + MAX_BULK_DELETE + " giao dịch mỗi lần"));
            }
            int deleted = 0;
            int skipped = 0;
            int forbidden = 0;
            for (Long id : new LinkedHashSet<>(ids)) {
                try {
                    delete(familyId, id, callerUserId, callerIsOwner);
                    deleted++;
                } catch (NotFoundException e) {
                    skipped++;
                } catch (ApiException e) {
                    if (e.getStatus() != HttpStatus.FORBIDDEN) {
                        throw e;
                    }
                    forbidden++;
                }
            }
            return new BulkDeleteResult(deleted, skipped, forbidden);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransactionService.bulkDelete", e);
        }
    }

    public PageResponse<TransactionResponse> listDeletedPaged(Long familyId, int page, int size) {
        try {
            log.info("listDeletedPaged - start, familyId={}, page={}, size={}", familyId, page, size);
            if (page < 0) {
                throw logged(log, new BadRequestException("page phải >= 0"));
            }
            if (size < 1 || size > MAX_PAGE_SIZE) {
                throw logged(log, new BadRequestException("size phải trong khoảng 1-" + MAX_PAGE_SIZE));
            }
            long totalElements = transactionDao.countDeletedByFamilyId(familyId);
            List<TransactionResponse> content = transactionDao.selectDeletedByFamilyIdPaged(familyId, size, page * size)
                    .stream().map(TransactionResponse::from).toList();
            return PageResponse.of(content, page, size, totalElements);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransactionService.listDeletedPaged", e);
        }
    }

    public void restore(Long familyId, Long transactionId, Long callerUserId, boolean callerIsOwner) {
        try {
            log.info("restore - start, familyId={}, transactionId={}", familyId, transactionId);
            Transaction deleted = transactionDao.selectDeletedById(transactionId)
                    .filter(t -> t.getFamilyId().equals(familyId))
                    .orElseThrow(() -> logged(log, new NotFoundException("Giao dịch đã xoá không tồn tại: " + transactionId)));
            requireCanModify(deleted, callerUserId, callerIsOwner);
            if (transactionDao.restore(transactionId, familyId) == 0) {
                throw logged(log, new NotFoundException("Giao dịch đã xoá không tồn tại: " + transactionId));
            }
            evictCaches(familyId, periodMonthOf(deleted.getOccurredAt()));
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransactionService.restore", e);
        }
    }

    public TransactionResponse uploadReceipt(
            Long familyId, Long transactionId, Long callerUserId, boolean callerIsOwner, MultipartFile file) {
        try {
            log.info("uploadReceipt - start, familyId={}, transactionId={}", familyId, transactionId);
            if (file.isEmpty()) {
                throw logged(log, new BadRequestException("File ảnh trống"));
            }
            // Content-Type and the original filename are both attacker-controlled — sniff the real
            // format from the file's own magic bytes instead of trusting either one.
            String sniffedType;
            try (InputStream in = file.getInputStream()) {
                sniffedType = ImageMagicBytes.detect(in.readNBytes(16));
            }
            if (sniffedType == null || !ALLOWED_RECEIPT_CONTENT_TYPES.contains(sniffedType)) {
                throw logged(log, new BadRequestException("Chỉ chấp nhận ảnh JPEG, PNG hoặc WEBP"));
            }
            Transaction transaction = requireOwnedByFamily(transactionId, familyId);
            requireCanModify(transaction, callerUserId, callerIsOwner);
            String oldPath = transaction.getReceiptPath();

            String newPath;
            try {
                newPath = receiptStorageService.save(familyId, transactionId, file, sniffedType);
            } catch (IOException e) {
                throw new UncheckedIOException("Không lưu được ảnh hoá đơn", e);
            }
            // The file is already on disk but the row pointing to it isn't committed yet: if the
            // transaction rolls back, the new file is an orphan (remove it) and the old one is still
            // the live receipt (keep it). Only once committed is the old file safe to delete.
            afterRollback(() -> receiptStorageService.delete(newPath));
            transaction.setReceiptPath(newPath);
            transaction.setReceiptContentType(sniffedType);
            transactionDao.update(transaction);

            if (oldPath != null) {
                afterCommit(() -> receiptStorageService.delete(oldPath));
            }
            return TransactionResponse.from(transaction);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransactionService.uploadReceipt", e);
        }
    }

    public ReceiptFile getReceipt(Long familyId, Long transactionId) {
        try {
            log.info("getReceipt - start, familyId={}, transactionId={}", familyId, transactionId);
            Transaction transaction = requireOwnedByFamily(transactionId, familyId);
            if (transaction.getReceiptPath() == null) {
                throw logged(log, new NotFoundException("Giao dịch chưa có ảnh hoá đơn"));
            }
            try {
                byte[] content = receiptStorageService.read(transaction.getReceiptPath());
                return new ReceiptFile(content, transaction.getReceiptContentType());
            } catch (IOException e) {
                throw new UncheckedIOException("Không đọc được ảnh hoá đơn", e);
            }
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransactionService.getReceipt", e);
        }
    }

    public void deleteReceipt(Long familyId, Long transactionId, Long callerUserId, boolean callerIsOwner) {
        try {
            log.info("deleteReceipt - start, familyId={}, transactionId={}", familyId, transactionId);
            Transaction transaction = requireOwnedByFamily(transactionId, familyId);
            requireCanModify(transaction, callerUserId, callerIsOwner);
            if (transaction.getReceiptPath() == null) {
                return;
            }
            String path = transaction.getReceiptPath();
            transaction.setReceiptPath(null);
            transaction.setReceiptContentType(null);
            transactionDao.update(transaction);
            // A rollback would leave the row still pointing at this file — so only delete it once committed.
            afterCommit(() -> receiptStorageService.delete(path));
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransactionService.deleteReceipt", e);
        }
    }

    private void checkBudgetCrossing(Long familyId, Long userId, String userEmail, String userDisplayName,
                                      Transaction transaction, Category category, String periodMonth,
                                      BigDecimal totalBefore) {
        log.info("checkBudgetCrossing - start, familyId={}, categoryId={}, periodMonth={}",
                familyId, category.getId(), periodMonth);
        Optional<Budget> budget = budgetDao.selectByCategoryAndPeriod(category.getId(), periodMonth);
        budget.ifPresent(b -> publishBudgetCrossing(
                b, familyId, userId, userEmail, userDisplayName, transaction, category.getId(), category.getName(),
                periodMonth, totalBefore, totalBefore.add(transaction.getAmount())));

        budgetDao.selectOverallByPeriod(familyId, periodMonth).ifPresent(b -> {
            // The transaction is already inserted (same DB transaction), so this sum includes it.
            BigDecimal overallAfter = transactionDao.sumAmountByFamilyPeriodAndType(
                    familyId, periodMonth, TYPE_EXPENSE);
            publishBudgetCrossing(b, familyId, userId, userEmail, userDisplayName, transaction, null,
                    "Tổng chi tiêu", periodMonth, overallAfter.subtract(transaction.getAmount()), overallAfter);
        });
    }

    private void publishBudgetCrossing(Budget budget, Long familyId, Long userId, String userEmail,
                                        String userDisplayName, Transaction transaction, Long categoryId,
                                        String categoryName, String periodMonth, BigDecimal totalBefore,
                                        BigDecimal totalAfter) {
        BigDecimal limit = budget.getLimitAmount();
        BigDecimal warningThreshold = limit.multiply(new BigDecimal("0.8"));
        String eventType = null;
        if (totalBefore.compareTo(limit) <= 0 && totalAfter.compareTo(limit) > 0) {
            eventType = ExpenseEvent.BUDGET_EXCEEDED;
        } else if (totalBefore.compareTo(warningThreshold) < 0 && totalAfter.compareTo(warningThreshold) >= 0
                && totalAfter.compareTo(limit) <= 0) {
            eventType = ExpenseEvent.BUDGET_WARNING;
        }
        if (eventType == null) {
            return;
        }
        eventPublisher.publishEvent(new ExpenseEvent(
                eventType, familyId, userId, transaction.getId(), categoryId, transaction.getAmount(), periodMonth,
                limit, totalAfter, categoryName, userEmail, userDisplayName, Instant.now()));
    }

    private String truncateName(String name) {
        return name != null && name.length() > MAX_CREATOR_NAME_LENGTH
                ? name.substring(0, MAX_CREATOR_NAME_LENGTH) : name;
    }

    private void requireCanModify(Transaction transaction, Long callerUserId, boolean callerIsOwner) {
        if (!callerIsOwner && !Objects.equals(transaction.getUserId(), callerUserId)) {
            throw logged(log, new ApiException(HttpStatus.FORBIDDEN,
                    "Chỉ chủ hộ hoặc người tạo giao dịch mới có quyền thực hiện thao tác này"));
        }
    }

    private Transaction requireOwnedByFamily(Long transactionId, Long familyId) {
        log.info("requireOwnedByFamily - start, transactionId={}, familyId={}", transactionId, familyId);
        Transaction transaction = transactionDao.selectById(transactionId)
                .orElseThrow(() -> logged(log, new NotFoundException("Giao dịch không tồn tại: " + transactionId)));
        if (!transaction.getFamilyId().equals(familyId)) {
            throw logged(log, new NotFoundException("Giao dịch không tồn tại: " + transactionId));
        }
        return transaction;
    }

    /**
     * Evicts now AND again after commit: between the two, a concurrent read could still see the old,
     * uncommitted-over rows and put them back in the cache, which the second eviction clears.
     */
    private void evictCaches(Long familyId, String periodMonth) {
        log.info("evictCaches - start, familyId={}, periodMonth={}", familyId, periodMonth);
        evictCacheKeys(familyId + ":" + periodMonth);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            afterCommit(() -> evictCacheKeys(familyId + ":" + periodMonth));
        }
    }

    /** Runs {@code action} once the surrounding transaction commits, or right away if there is none. */
    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    /** Runs {@code action} only if the surrounding transaction rolls back (never without a transaction). */
    private static void afterRollback(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    action.run();
                }
            }
        });
    }

    private void evictCacheKeys(String key) {
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
        return occurredAt.toLocalDate().toString().substring(0, 7);
    }
}
