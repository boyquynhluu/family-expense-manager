package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.event.ExpenseEvent;
import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.common.exception.ServiceException;
import com.family.expensemanager.expense.dao.TagDao;
import com.family.expensemanager.expense.dao.TransactionDao;
import com.family.expensemanager.expense.dao.TransactionSplitDao;
import com.family.expensemanager.expense.domain.TransactionAmounts;
import com.family.expensemanager.expense.domain.entity.Category;
import com.family.expensemanager.expense.domain.entity.Transaction;
import com.family.expensemanager.expense.domain.entity.Wallet;
import com.family.expensemanager.expense.dto.BulkDeleteResult;
import com.family.expensemanager.expense.dto.ReceiptFile;
import com.family.expensemanager.expense.dto.RefundRequest;
import com.family.expensemanager.expense.dto.TransactionReportFilter;
import com.family.expensemanager.expense.dto.TransactionRequest;
import com.family.expensemanager.expense.dto.TransactionAuditLogResponse;
import com.family.expensemanager.expense.dto.TransactionLocation;
import com.family.expensemanager.expense.dto.TransactionResponse;
import com.family.expensemanager.expense.dto.TransactionSnapshot;
import com.family.expensemanager.expense.dto.TransactionSplitPart;
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
import org.seasar.doma.jdbc.OptimisticLockException;
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

/**
 * @author boyquynhluu
 */
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

    private static final String IDEMPOTENCY_SCOPE = "CREATE_TRANSACTION";

    private final TransactionDao transactionDao;
    private final BudgetMonitor budgetMonitor;
    private final WalletService walletService;
    private final CategoryService categoryService;
    private final ApplicationEventPublisher eventPublisher;
    private final CacheManager cacheManager;
    private final ReceiptStorageService receiptStorageService;
    private final IdempotencyGuard idempotencyGuard;
    private final TransactionAuditService auditService;
    private final PeriodLockService periodLockService;
    private final SpendingLimitService spendingLimitService;
    private final TransactionSplitDao transactionSplitDao;
    private final TagDao tagDao;

    /**
     * @param idempotencyKey optional {@code Idempotency-Key} request header (see {@link IdempotencyGuard}) —
     *                        a retry with the same key returns the same {@link TransactionResponse} instead
     *                        of creating a second transaction; null/blank opts out, same as before this existed.
     */
    public TransactionResponse create(Long familyId, Long userId, String userEmail, String userDisplayName,
                                       TransactionRequest request, String idempotencyKey) {
        // Trusted, non-interactive callers only (RecurringTransactionScheduler running a rule that was
        // already checked when it was saved) — no wallet-ownership check. User requests must go through
        // the overload below.
        return create(familyId, userId, userEmail, userDisplayName, true, request, idempotencyKey);
    }

    /**
     * @param callerIsOwner whether the caller is the family OWNER — a plain member may only record into
     *                      their own wallet or a shared one ({@link WalletService#canUse}).
     */
    public TransactionResponse create(Long familyId, Long userId, String userEmail, String userDisplayName,
                                       boolean callerIsOwner, TransactionRequest request, String idempotencyKey) {
        return idempotencyGuard.runOnce(familyId, IDEMPOTENCY_SCOPE, idempotencyKey, request, TransactionResponse.class,
                () -> doCreate(familyId, userId, userEmail, userDisplayName, callerIsOwner, request));
    }

    private TransactionResponse doCreate(Long familyId, Long userId, String userEmail, String userDisplayName,
                                          boolean callerIsOwner, TransactionRequest request) {
        try {
            log.info("create - start, familyId={}, userId={}", familyId, userId);
            // Here rather than on the request: import and the recurring scheduler create through this path too.
            TransactionAmounts.problem(request.amount()).ifPresent(message -> {
                throw logged(log, new BadRequestException(message));
            });
            periodLockService.requireUnlocked(familyId, request.occurredAt());
            Wallet wallet = walletService.requireOwnedByFamily(request.walletId(), familyId);
            walletService.requireUsableBy(wallet, userId, callerIsOwner);
            List<TransactionSplitPart> parts = validSplits(familyId, request);
            Long mainCategoryId = parts.isEmpty() ? request.categoryId() : parts.get(0).categoryId();
            Category category = categoryService.requireOwnedByFamily(mainCategoryId, familyId, request.type());
            if (TYPE_EXPENSE.equals(request.type())) {
                // README A5: a member's own daily/monthly cap (trusted callers — the scheduler — pass as OWNER).
                if (!callerIsOwner) {
                    spendingLimitService.requireWithinLimits(familyId, userId, request.amount(), request.occurredAt(), null);
                }
                // README A1: cash/bank/savings never below 0, a credit card never below -its limit.
                walletService.requireAllowedOutflow(wallet, request.amount());
            }

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
            transaction.setIsPrivate(Boolean.TRUE.equals(request.isPrivate()));

            String periodMonth = periodMonthOf(request.occurredAt());

            transactionDao.insert(transaction);
            saveSplits(transaction.getId(), parts);
            List<String> tagNames = saveTags(familyId, transaction.getId(), request.tags());
            evictCaches(familyId, periodMonth);
            auditService.record(TransactionAuditService.ACTION_CREATED, familyId, transaction.getId(), null,
                    TransactionSnapshot.of(transaction), userId, userDisplayName);

            eventPublisher.publishEvent(new ExpenseEvent(
                    ExpenseEvent.EXPENSE_CREATED, familyId, userId, transaction.getId(), category.getId(),
                    transaction.getAmount(), null, null, null, null, null, null, Instant.now()));

            if (TYPE_EXPENSE.equals(request.type())) {
                budgetMonitor.onExpenseRecorded(familyId, userId, userEmail, userDisplayName, transaction,
                        parts.isEmpty()
                                ? List.of(new BudgetMonitor.Line(category.getId(), transaction.getAmount()))
                                : parts.stream().map(p -> new BudgetMonitor.Line(p.categoryId(), p.amount())).toList());
            }

            return TransactionResponse.from(transaction).withDetails(parts, tagNames);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException
                | OptimisticLockException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransactionService.doCreate", e);
        }
    }

    /**
     * Paginated + filtered transaction list for the Transactions page (see README
     * tech-debt item this replaces: the old endpoint returned every transaction in the
     * family and left filtering/paging to client-side JS, which didn't scale).
     */
    /**
     * @param viewerUserId other members' private transactions appear MASKED (who made them and when — nothing
     *                     else), in their normal date position, but only while the list is filtered by date at
     *                     most: any other filter would leak through what it matched (a note search hitting a
     *                     "***" row reveals its note, a wallet filter its wallet...).
     */
    public PageResponse<TransactionResponse> listByFamilyPaged(
            Long familyId, Long viewerUserId, TransactionReportFilter filter, int page, int size) {
        try {
            log.info("listByFamilyPaged - start, familyId={}, page={}, size={}", familyId, page, size);
            if (page < 0) {
                throw logged(log, new BadRequestException("page phải >= 0"));
            }
            if (size < 1 || size > MAX_PAGE_SIZE) {
                throw logged(log, new BadRequestException("size phải trong khoảng 1-" + MAX_PAGE_SIZE));
            }
            validateFilter(filter);
            String notePattern = filter.noteLikePattern();
            boolean showOthersPrivate = filter.filtersOnlyByDate();
            long totalElements = transactionDao.countByFamilyIdFiltered(
                    familyId, viewerUserId, showOthersPrivate, filter.walletId(), filter.categoryId(), filter.type(),
                    filter.fromDate(), filter.toDate(), notePattern, filter.minAmount(), filter.maxAmount(), filter.tagId());
            List<TransactionResponse> content = transactionDao.selectByFamilyIdFiltered(
                            familyId, viewerUserId, showOthersPrivate, filter.walletId(), filter.categoryId(),
                            filter.type(), filter.fromDate(), filter.toDate(), notePattern, filter.minAmount(),
                            filter.maxAmount(), filter.tagId(), size, page * size)
                    .stream()
                    .map(t -> t.isVisibleTo(viewerUserId) ? TransactionResponse.from(t) : TransactionResponse.masked(t))
                    .toList();
            return PageResponse.of(withDetails(content), page, size, totalElements);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException
                | OptimisticLockException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransactionService.listByFamilyPaged", e);
        }
    }

    /**
     * Page of the filtered list holding {@code transactionId} (one the viewer sees in full — e.g. the entry they
     * just saved). Mirrors {@link #listByFamilyPaged}: same order, and other members' masked rows count as
     * "ahead" exactly when that list shows them.
     */
    public TransactionLocation locate(Long familyId, Long viewerUserId, TransactionReportFilter filter,
                                      Long transactionId, int size) {
        try {
            log.info("locate - start, familyId={}, transactionId={}", familyId, transactionId);
            if (size < 1 || size > MAX_PAGE_SIZE) {
                throw logged(log, new BadRequestException("size phải trong khoảng 1-" + MAX_PAGE_SIZE));
            }
            validateFilter(filter);
            Transaction transaction = requireOwnedByFamily(transactionId, familyId, viewerUserId);
            String notePattern = filter.noteLikePattern();
            boolean inList = transactionDao.countFilteredMatchingId(
                    familyId, viewerUserId, filter.walletId(), filter.categoryId(), filter.type(), filter.fromDate(),
                    filter.toDate(), notePattern, filter.minAmount(), filter.maxAmount(), filter.tagId(), transactionId) > 0;
            if (!inList) {
                return new TransactionLocation(false, 0);
            }
            long ahead = transactionDao.countFilteredAhead(
                    familyId, viewerUserId, filter.filtersOnlyByDate(), filter.walletId(), filter.categoryId(), filter.type(), filter.fromDate(),
                    filter.toDate(), notePattern, filter.minAmount(), filter.maxAmount(), filter.tagId(),
                    transaction.getOccurredAt(), transactionId);
            return new TransactionLocation(true, (int) (ahead / size));
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException
                | OptimisticLockException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransactionService.locate", e);
        }
    }

    private void validateFilter(TransactionReportFilter filter) {
        if (filter.q() != null && filter.q().length() > TransactionReportFilter.MAX_QUERY_LENGTH) {
            throw logged(log, new BadRequestException(
                    "Từ khoá tìm kiếm tối đa " + TransactionReportFilter.MAX_QUERY_LENGTH + " ký tự"));
        }
        if (filter.minAmount() != null && filter.maxAmount() != null
                && filter.minAmount().compareTo(filter.maxAmount()) > 0) {
            throw logged(log, new BadRequestException("Số tiền tối thiểu không được lớn hơn số tiền tối đa"));
        }
    }

    public TransactionResponse get(Long familyId, Long transactionId, Long viewerUserId) {
        try {
            log.info("get - start, familyId={}, transactionId={}", familyId, transactionId);
            return withDetails(List.of(TransactionResponse.from(requireOwnedByFamily(transactionId, familyId, viewerUserId))))
                    .get(0);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException
                | OptimisticLockException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransactionService.get", e);
        }
    }

    public TransactionResponse update(Long familyId, Long transactionId, Long callerUserId, String callerName,
                                      boolean callerIsOwner, TransactionRequest request) {
        try {
            log.info("update - start, familyId={}, transactionId={}", familyId, transactionId);
            Transaction transaction = requireOwnedByFamily(transactionId, familyId, callerUserId);
            requireCanModify(transaction, callerUserId, callerIsOwner);
            // Old AND new date: nothing may be moved into or out of a closed month either.
            periodLockService.requireUnlocked(familyId, transaction.getOccurredAt(), request.occurredAt());
            TransactionSnapshot before = TransactionSnapshot.of(transaction);
            // Always, even when the amount is unchanged: an older entry outside 10.000đ–5.000.000đ must be
            // brought into the range before anything else on it can be saved.
            TransactionAmounts.problem(request.amount()).ifPresent(message -> {
                throw logged(log, new BadRequestException(message));
            });
            Wallet wallet = walletService.requireOwnedByFamily(request.walletId(), familyId);
            // Only when MOVING the transaction to another wallet: a member can still fix the amount/note of
            // their own past entry in a wallet that has since been assigned to someone else.
            if (!request.walletId().equals(transaction.getWalletId())) {
                walletService.requireUsableBy(wallet, callerUserId, callerIsOwner);
            }
            List<TransactionSplitPart> parts = validSplits(familyId, request);
            Long mainCategoryId = parts.isEmpty() ? request.categoryId() : parts.get(0).categoryId();
            Category category = categoryService.requireOwnedByFamily(mainCategoryId, familyId, request.type());
            if (transaction.getRefundOfId() != null) {
                throw logged(log, new BadRequestException("Không sửa được khoản hoàn tiền — hãy xoá và tạo lại"));
            }
            if (TYPE_EXPENSE.equals(request.type()) && !callerIsOwner) {
                spendingLimitService.requireWithinLimits(familyId, transaction.getUserId(), request.amount(),
                        request.occurredAt(), transactionId);
            }
            requireAllowedEdit(transaction, wallet, request);

            String oldPeriodMonth = periodMonthOf(transaction.getOccurredAt());

            transaction.setWalletId(wallet.getId());
            transaction.setCategoryId(category.getId());
            transaction.setType(request.type());
            transaction.setAmount(request.amount());
            transaction.setOccurredAt(request.occurredAt());
            transaction.setNote(request.note());
            // Only the creator decides whether their entry is private — an OWNER editing someone
            // else's (non-private) transaction can't flip it, which would hide it from the OWNER too.
            if (Objects.equals(transaction.getUserId(), callerUserId)) {
                transaction.setIsPrivate(Boolean.TRUE.equals(request.isPrivate()));
            }
            transactionDao.update(transaction);
            auditService.record(TransactionAuditService.ACTION_UPDATED, familyId, transactionId, before,
                    TransactionSnapshot.of(transaction), callerUserId, callerName);
            publishUpdated(familyId, callerUserId, callerName, transaction, before);
            transactionSplitDao.deleteByTransactionId(transactionId);
            saveSplits(transactionId, parts);
            tagDao.deleteLinksByTransactionId(transactionId);
            List<String> tagNames = saveTags(familyId, transactionId, request.tags());

            String newPeriodMonth = periodMonthOf(request.occurredAt());
            evictCaches(familyId, oldPeriodMonth);
            if (!oldPeriodMonth.equals(newPeriodMonth)) {
                evictCaches(familyId, newPeriodMonth);
            }

            return TransactionResponse.from(transaction).withDetails(parts, tagNames);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException
                | OptimisticLockException e) {
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
    public void delete(Long familyId, Long transactionId, Long callerUserId, String callerName,
                       boolean callerIsOwner) {
        try {
            log.info("delete - start, familyId={}, transactionId={}", familyId, transactionId);
            Transaction transaction = softDelete(familyId, transactionId, callerUserId, callerName, callerIsOwner);
            // Notifications are listed to the whole family: a private transaction's event carries no
            // details at all (they'd otherwise also sit in notification-service's stored payload).
            boolean hidden = Boolean.TRUE.equals(transaction.getIsPrivate());
            eventPublisher.publishEvent(new ExpenseEvent(
                    ExpenseEvent.EXPENSE_DELETED, familyId, callerUserId, hidden ? null : transaction.getId(),
                    hidden ? null : transaction.getCategoryId(), hidden ? null : transaction.getAmount(),
                    null, null, null, null, null, callerName, Instant.now(),
                    hidden ? null : transaction.getOccurredAt().toLocalDate(), hidden ? null : transaction.getNote(),
                    null, null, 1, hidden ? Boolean.TRUE : null));
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException
                | OptimisticLockException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransactionService.delete", e);
        }
    }

    /** Marks one transaction deleted (who + when) and logs it; no notification — callers decide how to announce it. */
    private Transaction softDelete(Long familyId, Long transactionId, Long callerUserId, String callerName,
                                   boolean callerIsOwner) {
        Transaction transaction = requireOwnedByFamily(transactionId, familyId, callerUserId);
        requireCanModify(transaction, callerUserId, callerIsOwner);
        periodLockService.requireUnlocked(familyId, transaction.getOccurredAt());
        if (transactionDao.countActiveRefundsOf(transactionId) > 0) {
            throw logged(log, new BadRequestException("Giao dịch đã có khoản hoàn tiền — hãy xoá các khoản hoàn tiền trước"));
        }
        transaction.setDeletedAt(LocalDateTime.now());
        transaction.setDeletedByUserId(callerUserId);
        transaction.setDeletedByName(truncateName(callerName));
        transactionDao.update(transaction);
        auditService.record(TransactionAuditService.ACTION_DELETED, familyId, transactionId,
                TransactionSnapshot.of(transaction), null, callerUserId, callerName);
        evictCaches(familyId, periodMonthOf(transaction.getOccurredAt()));
        return transaction;
    }

    /** Publishes ONE notification for the whole batch (itemCount = how many), not one per row. */
    public BulkDeleteResult bulkDelete(Long familyId, List<Long> ids, Long callerUserId, String callerName,
                                       boolean callerIsOwner) {
        try {
            log.info("bulkDelete - start, familyId={}, count={}", familyId, ids.size());
            if (ids.size() > MAX_BULK_DELETE) {
                throw logged(log, new BadRequestException("Chỉ được xoá tối đa " + MAX_BULK_DELETE + " giao dịch mỗi lần"));
            }
            int deleted = 0;
            int skipped = 0;
            int forbidden = 0;
            int locked = 0;
            for (Long id : new LinkedHashSet<>(ids)) {
                try {
                    softDelete(familyId, id, callerUserId, callerName, callerIsOwner);
                    deleted++;
                } catch (NotFoundException e) {
                    skipped++;
                } catch (ApiException e) {
                    if (e.getStatus() == HttpStatus.FORBIDDEN) {
                        forbidden++;
                    } else if (e.getStatus() == HttpStatus.CONFLICT) {
                        // The only 409 softDelete raises: the row's month is closed (PeriodLockService).
                        locked++;
                    } else {
                        throw e;
                    }
                }
            }
            if (deleted > 0) {
                eventPublisher.publishEvent(new ExpenseEvent(
                        ExpenseEvent.EXPENSE_DELETED, familyId, callerUserId, null, null, null, null, null, null,
                        null, null, callerName, Instant.now(), null, null, null, null, deleted));
            }
            return new BulkDeleteResult(deleted, skipped, forbidden, locked);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException
                | OptimisticLockException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransactionService.bulkDelete", e);
        }
    }

    public PageResponse<TransactionResponse> listDeletedPaged(Long familyId, Long viewerUserId, int page, int size) {
        try {
            log.info("listDeletedPaged - start, familyId={}, page={}, size={}", familyId, page, size);
            if (page < 0) {
                throw logged(log, new BadRequestException("page phải >= 0"));
            }
            if (size < 1 || size > MAX_PAGE_SIZE) {
                throw logged(log, new BadRequestException("size phải trong khoảng 1-" + MAX_PAGE_SIZE));
            }
            long totalElements = transactionDao.countDeletedByFamilyId(familyId);
            // Another member's private transaction is listed (the family sees that SOMETHING was deleted)
            // but with every detail masked — and restoring it still answers 404 to anyone but its creator.
            List<TransactionResponse> content = transactionDao.selectDeletedByFamilyIdPaged(familyId, size, page * size)
                    .stream()
                    .map(t -> t.isVisibleTo(viewerUserId) ? TransactionResponse.from(t) : TransactionResponse.masked(t))
                    .toList();
            return PageResponse.of(content, page, size, totalElements);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException
                | OptimisticLockException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransactionService.listDeletedPaged", e);
        }
    }

    public void restore(Long familyId, Long transactionId, Long callerUserId, String callerName,
                        boolean callerIsOwner) {
        try {
            log.info("restore - start, familyId={}, transactionId={}", familyId, transactionId);
            Transaction deleted = transactionDao.selectDeletedById(transactionId)
                    .filter(t -> t.getFamilyId().equals(familyId) && t.isVisibleTo(callerUserId))
                    .orElseThrow(() -> logged(log, new NotFoundException("Giao dịch đã xoá không tồn tại: " + transactionId)));
            requireCanModify(deleted, callerUserId, callerIsOwner);
            periodLockService.requireUnlocked(familyId, deleted.getOccurredAt());
            if (deleted.getRefundOfId() != null && transactionDao.selectById(deleted.getRefundOfId()).isEmpty()) {
                throw logged(log, new BadRequestException("Hãy khôi phục giao dịch gốc trước khi khôi phục khoản hoàn tiền"));
            }
            if (TYPE_EXPENSE.equals(deleted.getType()) && deleted.getAmount().signum() > 0) {
                walletService.requireAllowedOutflow(
                        walletService.requireOwnedByFamily(deleted.getWalletId(), familyId), deleted.getAmount());
            }
            if (transactionDao.restore(transactionId, familyId) == 0) {
                throw logged(log, new NotFoundException("Giao dịch đã xoá không tồn tại: " + transactionId));
            }
            auditService.record(TransactionAuditService.ACTION_RESTORED, familyId, transactionId, null,
                    TransactionSnapshot.of(deleted), callerUserId, callerName);
            evictCaches(familyId, periodMonthOf(deleted.getOccurredAt()));
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException
                | OptimisticLockException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransactionService.restore", e);
        }
    }

    /** Who created/changed/deleted/restored this transaction and what it looked like before and after, oldest first. */
    public List<TransactionAuditLogResponse> history(Long familyId, Long transactionId, Long viewerUserId) {
        // Works for deleted transactions too, so look in both places before deciding on visibility.
        transactionDao.selectById(transactionId)
                .or(() -> transactionDao.selectDeletedById(transactionId))
                .filter(t -> t.getFamilyId().equals(familyId) && !t.isVisibleTo(viewerUserId))
                .ifPresent(t -> {
                    throw logged(log, new NotFoundException("Giao dịch không tồn tại: " + transactionId));
                });
        return auditService.listHistory(familyId, transactionId);
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
            Transaction transaction = requireOwnedByFamily(transactionId, familyId, callerUserId);
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
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException
                | OptimisticLockException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransactionService.uploadReceipt", e);
        }
    }

    public ReceiptFile getReceipt(Long familyId, Long transactionId, Long viewerUserId) {
        try {
            log.info("getReceipt - start, familyId={}, transactionId={}", familyId, transactionId);
            Transaction transaction = requireOwnedByFamily(transactionId, familyId, viewerUserId);
            if (transaction.getReceiptPath() == null) {
                throw logged(log, new NotFoundException("Giao dịch chưa có ảnh hoá đơn"));
            }
            try {
                byte[] content = receiptStorageService.read(transaction.getReceiptPath());
                return new ReceiptFile(content, transaction.getReceiptContentType());
            } catch (IOException e) {
                throw new UncheckedIOException("Không đọc được ảnh hoá đơn", e);
            }
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException
                | OptimisticLockException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransactionService.getReceipt", e);
        }
    }

    public void deleteReceipt(Long familyId, Long transactionId, Long callerUserId, boolean callerIsOwner) {
        try {
            log.info("deleteReceipt - start, familyId={}, transactionId={}", familyId, transactionId);
            Transaction transaction = requireOwnedByFamily(transactionId, familyId, callerUserId);
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
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException
                | OptimisticLockException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransactionService.deleteReceipt", e);
        }
    }

    /**
     * README C4 "Hoàn tiền / trả hàng": records money coming back for an expense as a NEGATIVE expense in the same
     * category and wallet, linked to it — so the category's spending, the budgets and the wallet balance all go
     * down by it, while income stays untouched. Its creator or the OWNER; at most what is left to refund.
     */
    public TransactionResponse refund(Long familyId, Long originalId, Long callerUserId, String callerName,
                                      boolean callerIsOwner, RefundRequest request) {
        try {
            log.info("refund - start, familyId={}, originalId={}", familyId, originalId);
            Transaction original = requireOwnedByFamily(originalId, familyId, callerUserId);
            requireCanModify(original, callerUserId, callerIsOwner);
            if (!TYPE_EXPENSE.equals(original.getType()) || original.getRefundOfId() != null) {
                throw logged(log, new BadRequestException("Chỉ hoàn tiền được cho một khoản chi"));
            }
            BigDecimal left = original.getAmount().subtract(transactionDao.sumRefundedOf(originalId));
            if (request.amount().compareTo(left) > 0) {
                throw logged(log, new BadRequestException("Số tiền hoàn vượt số còn có thể hoàn: " + money(left)));
            }
            if (request.occurredAt().isBefore(original.getOccurredAt())) {
                throw logged(log, new BadRequestException("Ngày hoàn tiền không được trước ngày chi"));
            }
            periodLockService.requireUnlocked(familyId, request.occurredAt());

            Transaction refund = new Transaction();
            refund.setWalletId(original.getWalletId());
            refund.setCategoryId(original.getCategoryId());
            refund.setRefundOfId(originalId);
            refund.setFamilyId(familyId);
            refund.setUserId(callerUserId);
            refund.setCreatedByName(truncateName(callerName));
            refund.setType(TYPE_EXPENSE);
            refund.setAmount(request.amount().negate());
            refund.setOccurredAt(request.occurredAt());
            refund.setNote(request.note() != null && !request.note().isBlank() ? request.note()
                    : "Hoàn tiền" + (original.getNote() != null ? ": " + original.getNote() : ""));
            refund.setIsPrivate(original.getIsPrivate());
            transactionDao.insert(refund);
            auditService.record(TransactionAuditService.ACTION_CREATED, familyId, refund.getId(), null,
                    TransactionSnapshot.of(refund), callerUserId, callerName);
            evictCaches(familyId, periodMonthOf(request.occurredAt()));
            return TransactionResponse.from(refund);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException
                | OptimisticLockException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransactionService.refund", e);
        }
    }

    /** Tags names: trimmed, case-insensitively de-duplicated; empty or null = no tags. */
    static List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        java.util.Map<String, String> unique = new java.util.LinkedHashMap<>();
        for (String tag : tags) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String name = tag.trim().replaceAll("\\s+", " ");
            unique.putIfAbsent(name.toLowerCase(java.util.Locale.ROOT), name);
        }
        return List.copyOf(unique.values());
    }

    /** README C6: links the transaction to its tags, creating the family's new ones on the fly. */
    private List<String> saveTags(Long familyId, Long transactionId, List<String> tags) {
        List<String> names = normalizeTags(tags);
        if (names.isEmpty()) {
            return names;
        }
        List<com.family.expensemanager.expense.domain.entity.TransactionTag> links = new java.util.ArrayList<>();
        for (String name : names) {
            com.family.expensemanager.expense.domain.entity.Tag tag = tagDao.selectByFamilyAndName(familyId, name)
                    .orElseGet(() -> {
                        com.family.expensemanager.expense.domain.entity.Tag created =
                                new com.family.expensemanager.expense.domain.entity.Tag();
                        created.setFamilyId(familyId);
                        created.setName(name);
                        created.setCreatedAt(LocalDateTime.now());
                        tagDao.insert(created);
                        return created;
                    });
            com.family.expensemanager.expense.domain.entity.TransactionTag link =
                    new com.family.expensemanager.expense.domain.entity.TransactionTag();
            link.setTransactionId(transactionId);
            link.setTagId(tag.getId());
            links.add(link);
        }
        tagDao.insertLinks(links);
        return names;
    }

    /**
     * README C5: no parts, or 2..10 parts of the transaction's type, in distinct categories, adding up exactly to
     * its amount. The first part's category becomes the transaction's main category.
     */
    private List<TransactionSplitPart> validSplits(Long familyId, TransactionRequest request) {
        if (request.splits() == null || request.splits().isEmpty()) {
            return List.of();
        }
        List<TransactionSplitPart> parts = request.splits();
        if (parts.size() < 2) {
            throw logged(log, new BadRequestException("Tách giao dịch cần ít nhất 2 phần"));
        }
        if (parts.stream().map(TransactionSplitPart::categoryId).distinct().count() != parts.size()) {
            throw logged(log, new BadRequestException("Mỗi phần tách phải thuộc một danh mục khác nhau"));
        }
        BigDecimal total = parts.stream().map(TransactionSplitPart::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(request.amount()) != 0) {
            throw logged(log, new BadRequestException("Tổng các phần tách (" + money(total)
                    + ") phải bằng số tiền giao dịch (" + money(request.amount()) + ")"));
        }
        for (TransactionSplitPart part : parts) {
            categoryService.requireOwnedByFamily(part.categoryId(), familyId, request.type());
        }
        return parts;
    }

    private void saveSplits(Long transactionId, List<TransactionSplitPart> parts) {
        if (parts.isEmpty()) {
            return;
        }
        transactionSplitDao.insertAll(parts.stream().map(p -> {
            com.family.expensemanager.expense.domain.entity.TransactionSplit split =
                    new com.family.expensemanager.expense.domain.entity.TransactionSplit();
            split.setTransactionId(transactionId);
            split.setCategoryId(p.categoryId());
            split.setAmount(p.amount());
            return split;
        }).toList());
    }

    /** Attaches split parts and tags to listed transactions (two queries for the whole page). */
    private List<TransactionResponse> withDetails(List<TransactionResponse> rows) {
        List<Long> ids = rows.stream().filter(r -> r.walletId() != null).map(TransactionResponse::id).toList();
        if (ids.isEmpty()) {
            return rows;
        }
        java.util.Map<Long, List<TransactionSplitPart>> splits = new java.util.HashMap<>();
        for (com.family.expensemanager.expense.domain.entity.TransactionSplit s
                : transactionSplitDao.selectByTransactionIds(ids)) {
            splits.computeIfAbsent(s.getTransactionId(), k -> new java.util.ArrayList<>())
                    .add(new TransactionSplitPart(s.getCategoryId(), s.getAmount()));
        }
        java.util.Map<Long, List<String>> tags = new java.util.HashMap<>();
        for (java.util.Map<String, Object> row : tagDao.selectNamesByTransactionIds(ids)) {
            tags.computeIfAbsent(((Number) row.get("transactionId")).longValue(), k -> new java.util.ArrayList<>())
                    .add((String) row.get("name"));
        }
        return rows.stream()
                .map(r -> r.walletId() == null ? r : r.withDetails(splits.get(r.id()), tags.get(r.id())))
                .toList();
    }

    /**
     * README A1 on an edit: works out how much LESS money each affected wallet holds after the change (an expense
     * raised, an income lowered, the entry moved to another wallet...) and refuses it when that pushes a wallet
     * below its floor. Edits that only put money back always pass.
     */
    private void requireAllowedEdit(Transaction existing, Wallet newWallet, TransactionRequest request) {
        BigDecimal oldEffect = signedEffect(existing.getType(), existing.getAmount());
        BigDecimal newEffect = signedEffect(request.type(), request.amount());
        if (existing.getWalletId().equals(newWallet.getId())) {
            walletService.requireAllowedOutflow(newWallet, oldEffect.subtract(newEffect));
            return;
        }
        // Moved: the new wallet takes the new entry; the old wallet loses the old one (an income leaving it).
        walletService.requireAllowedOutflow(newWallet, newEffect.negate());
        if (oldEffect.signum() > 0) {
            walletService.requireAllowedOutflow(
                    walletService.requireOwnedByFamily(existing.getWalletId(), existing.getFamilyId()), oldEffect);
        }
    }

    /** What an entry does to its wallet's balance: + for income, - for expense (a refund's negative amount adds). */
    private static BigDecimal signedEffect(String type, BigDecimal amount) {
        return TYPE_EXPENSE.equals(type) ? amount.negate() : amount;
    }

    /**
     * README A2: tells the family what changed ("50.000 → 5.000.000"). A private entry's notice carries no details,
     * like its deletion notice.
     */
    private void publishUpdated(Long familyId, Long callerUserId, String callerName, Transaction after,
                                TransactionSnapshot before) {
        String actor = callerName != null ? callerName : "Một thành viên";
        String message;
        if (Boolean.TRUE.equals(after.getIsPrivate())) {
            message = actor + " đã sửa 1 giao dịch riêng tư (***)";
        } else {
            List<String> changes = new java.util.ArrayList<>();
            if (before.amount().compareTo(after.getAmount()) != 0) {
                changes.add("số tiền " + money(before.amount()) + " → " + money(after.getAmount()));
            }
            if (!before.type().equals(after.getType())) {
                changes.add("loại " + typeLabel(before.type()) + " → " + typeLabel(after.getType()));
            }
            if (!before.walletId().equals(after.getWalletId())) {
                changes.add("ví " + walletName(familyId, before.walletId()) + " → " + walletName(familyId, after.getWalletId()));
            }
            if (!before.categoryId().equals(after.getCategoryId())) {
                changes.add("danh mục " + categoryName(familyId, before.categoryId()) + " → "
                        + categoryName(familyId, after.getCategoryId()));
            }
            if (!before.occurredAt().toLocalDate().equals(after.getOccurredAt().toLocalDate())) {
                changes.add("ngày " + before.occurredAt().toLocalDate() + " → " + after.getOccurredAt().toLocalDate());
            }
            if (!Objects.equals(blankToNull(before.note()), blankToNull(after.getNote()))) {
                changes.add("ghi chú \"" + Objects.toString(blankToNull(after.getNote()), "") + "\"");
            }
            if (changes.isEmpty()) {
                return;
            }
            message = actor + " đã sửa giao dịch " + money(after.getAmount()) + " (ngày "
                    + after.getOccurredAt().toLocalDate() + "): " + String.join("; ", changes);
        }
        eventPublisher.publishEvent(ExpenseEvent.notice(ExpenseEvent.EXPENSE_UPDATED, familyId, callerUserId, callerName,
                null, null, "Giao dịch đã được sửa", message, "/transactions"));
    }

    private static String money(BigDecimal amount) {
        return com.family.expensemanager.common.currency.CurrencyUtil.formatCurrency(amount);
    }

    private static String typeLabel(String type) {
        return TYPE_EXPENSE.equals(type) ? "chi" : "thu";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String walletName(Long familyId, Long walletId) {
        try {
            return "\"" + walletService.requireOwnedByFamily(walletId, familyId).getName() + "\"";
        } catch (RuntimeException e) {
            return "#" + walletId;
        }
    }

    private String categoryName(Long familyId, Long categoryId) {
        try {
            return "\"" + categoryService.requireOwnedByFamily(categoryId, familyId).getName() + "\"";
        } catch (RuntimeException e) {
            return "#" + categoryId;
        }
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

    /**
     * Also enforces privacy: another member's private transaction answers exactly like a missing one
     * (404, not 403 — no confirmation it even exists), for the family OWNER as well.
     */
    private Transaction requireOwnedByFamily(Long transactionId, Long familyId, Long viewerUserId) {
        log.info("requireOwnedByFamily - start, transactionId={}, familyId={}", transactionId, familyId);
        Transaction transaction = transactionDao.selectById(transactionId)
                .orElseThrow(() -> logged(log, new NotFoundException("Giao dịch không tồn tại: " + transactionId)));
        if (!transaction.getFamilyId().equals(familyId) || !transaction.isVisibleTo(viewerUserId)) {
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
