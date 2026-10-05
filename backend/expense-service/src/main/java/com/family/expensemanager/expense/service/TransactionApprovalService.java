package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.currency.CurrencyUtil;
import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.event.ExpenseEvent;
import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.ConflictException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.common.exception.ServiceException;
import com.family.expensemanager.expense.dao.FamilySettingDao;
import com.family.expensemanager.expense.dao.TransactionApprovalDao;
import com.family.expensemanager.expense.domain.TransactionAmounts;
import com.family.expensemanager.expense.domain.entity.FamilySetting;
import com.family.expensemanager.expense.domain.entity.TransactionApproval;
import com.family.expensemanager.expense.domain.entity.Wallet;
import com.family.expensemanager.expense.dto.FamilySettingsRequest;
import com.family.expensemanager.expense.dto.FamilySettingsResponse;
import com.family.expensemanager.expense.dto.TransactionApprovalResponse;
import com.family.expensemanager.expense.dto.TransactionRequest;
import com.family.expensemanager.expense.dto.TransactionResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.family.expensemanager.common.exception.ExceptionLogger.logged;

/**
 * README A5 "duyệt khoản chi vượt ngưỡng": an expense above the family's approval threshold, entered by anyone but
 * the OWNER, is NOT a transaction yet — it waits here (so it moves no balance, budget or report) until the OWNER
 * approves it (it is then recorded as a normal transaction of the requester, with every usual check) or rejects it.
 *
 * @author boyquynhluu
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j(topic = "TransactionApprovalService")
public class TransactionApprovalService {

    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_APPROVED = "APPROVED";
    static final String STATUS_REJECTED = "REJECTED";
    private static final String ROLE_OWNER = "OWNER";
    private static final String TYPE_EXPENSE = "EXPENSE";
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_NAME_LENGTH = 100;

    private final TransactionApprovalDao approvalDao;
    private final FamilySettingDao familySettingDao;
    private final TransactionService transactionService;
    private final WalletService walletService;
    private final CategoryService categoryService;
    private final PeriodLockService periodLockService;
    private final SpendingLimitService spendingLimitService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public FamilySettingsResponse settings(Long familyId) {
        return new FamilySettingsResponse(threshold(familyId));
    }

    @PreAuthorize("hasRole('OWNER')")
    public FamilySettingsResponse updateSettings(Long familyId, FamilySettingsRequest request) {
        try {
            log.info("updateSettings - start, familyId={}", familyId);
            FamilySetting setting = familySettingDao.selectByFamilyId(familyId).orElse(null);
            boolean exists = setting != null;
            if (!exists) {
                setting = new FamilySetting();
                setting.setFamilyId(familyId);
            }
            setting.setApprovalThreshold(request.approvalThreshold());
            setting.setUpdatedAt(LocalDateTime.now(clock));
            if (exists) {
                familySettingDao.update(setting);
            } else {
                familySettingDao.insert(setting);
            }
            return new FamilySettingsResponse(setting.getApprovalThreshold());
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransactionApprovalService.updateSettings", e);
        }
    }

    /** Whether this expense must wait for the OWNER instead of being recorded right away. */
    public boolean requiresApproval(Long familyId, String role, TransactionRequest request) {
        if (ROLE_OWNER.equals(role) || !TYPE_EXPENSE.equals(request.type())) {
            return false;
        }
        BigDecimal threshold = threshold(familyId);
        return threshold != null && request.amount() != null && request.amount().compareTo(threshold) > 0;
    }

    /**
     * Files the request after the checks that do not depend on the moment of approval (amount range, closed
     * month, wallet the requester may use, category, the requester's own spending caps), so an obviously invalid
     * request is refused now rather than after the OWNER's click.
     */
    public TransactionApprovalResponse submit(Long familyId, Long userId, String userEmail, String userName,
                                              TransactionRequest request) {
        try {
            log.info("submit - start, familyId={}, userId={}", familyId, userId);
            TransactionAmounts.problem(request.amount()).ifPresent(message -> {
                throw logged(log, new BadRequestException(message));
            });
            periodLockService.requireUnlocked(familyId, request.occurredAt());
            Wallet wallet = walletService.requireOwnedByFamily(request.walletId(), familyId);
            walletService.requireUsableBy(wallet, userId, false);
            categoryService.requireOwnedByFamily(request.categoryId(), familyId, TYPE_EXPENSE);
            spendingLimitService.requireWithinLimits(familyId, userId, request.amount(), request.occurredAt(), null);

            TransactionApproval a = new TransactionApproval();
            a.setFamilyId(familyId);
            a.setRequesterUserId(userId);
            a.setRequesterName(truncate(userName));
            a.setRequesterEmail(userEmail);
            a.setWalletId(request.walletId());
            a.setCategoryId(request.categoryId());
            a.setAmount(request.amount());
            a.setOccurredAt(request.occurredAt());
            a.setNote(request.note());
            a.setIsPrivate(Boolean.TRUE.equals(request.isPrivate()));
            a.setStatus(STATUS_PENDING);
            a.setCreatedAt(LocalDateTime.now(clock));
            approvalDao.insert(a);

            String actor = userName != null ? userName : "Một thành viên";
            eventPublisher.publishEvent(ExpenseEvent.notice(ExpenseEvent.APPROVAL_REQUESTED, familyId, userId, userName,
                    null, ROLE_OWNER, "Khoản chi chờ duyệt",
                    actor + " muốn chi " + CurrencyUtil.formatCurrency(request.amount())
                            + (Boolean.TRUE.equals(request.isPrivate()) ? "" : noteSuffix(request.note()))
                            + " — vượt ngưỡng cần chủ hộ duyệt. Vào trang Giao dịch để duyệt hoặc từ chối.",
                    "/transactions"));
            return TransactionApprovalResponse.from(a);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransactionApprovalService.submit", e);
        }
    }

    /** The OWNER sees every request; anyone else only their own. */
    public PageResponse<TransactionApprovalResponse> list(Long familyId, Long userId, String role, int page, int size) {
        try {
            log.info("list - start, familyId={}, page={}, size={}", familyId, page, size);
            if (page < 0) {
                throw logged(log, new BadRequestException("page phải >= 0"));
            }
            if (size < 1 || size > MAX_PAGE_SIZE) {
                throw logged(log, new BadRequestException("size phải trong khoảng 1-" + MAX_PAGE_SIZE));
            }
            Long requester = ROLE_OWNER.equals(role) ? null : userId;
            long total = approvalDao.countByFamily(familyId, requester);
            List<TransactionApprovalResponse> content = approvalDao.selectByFamilyPaged(familyId, requester, size,
                    page * size).stream().map(TransactionApprovalResponse::from).toList();
            return PageResponse.of(content, page, size, total);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransactionApprovalService.list", e);
        }
    }

    public long countPending(Long familyId) {
        return approvalDao.countPendingByFamilyId(familyId);
    }

    /** Records the expense as the requester's own transaction — the OWNER already vouched for it (no caps). */
    @PreAuthorize("hasRole('OWNER')")
    public TransactionApprovalResponse approve(Long familyId, Long approvalId, Long ownerUserId, String ownerName) {
        try {
            log.info("approve - start, familyId={}, approvalId={}", familyId, approvalId);
            TransactionApproval a = requirePending(familyId, approvalId);
            TransactionResponse created = transactionService.create(familyId, a.getRequesterUserId(),
                    a.getRequesterEmail(), a.getRequesterName(), new TransactionRequest(a.getWalletId(),
                            a.getCategoryId(), TYPE_EXPENSE, a.getAmount(), a.getOccurredAt(), a.getNote(), a.getIsPrivate()),
                    null);
            decide(a, STATUS_APPROVED, ownerUserId, ownerName, null);
            a.setTransactionId(created.id());
            approvalDao.update(a);
            notifyRequester(a, ownerUserId, ownerName, "Khoản chi đã được duyệt",
                    (ownerName != null ? ownerName : "Chủ hộ") + " đã duyệt khoản chi "
                            + CurrencyUtil.formatCurrency(a.getAmount()) + " của bạn — đã ghi vào giao dịch.");
            return TransactionApprovalResponse.from(a);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransactionApprovalService.approve", e);
        }
    }

    @PreAuthorize("hasRole('OWNER')")
    public TransactionApprovalResponse reject(Long familyId, Long approvalId, Long ownerUserId, String ownerName,
                                              String reason) {
        try {
            log.info("reject - start, familyId={}, approvalId={}", familyId, approvalId);
            TransactionApproval a = requirePending(familyId, approvalId);
            decide(a, STATUS_REJECTED, ownerUserId, ownerName, reason);
            approvalDao.update(a);
            notifyRequester(a, ownerUserId, ownerName, "Khoản chi bị từ chối",
                    (ownerName != null ? ownerName : "Chủ hộ") + " đã từ chối khoản chi "
                            + CurrencyUtil.formatCurrency(a.getAmount()) + " của bạn"
                            + (reason != null && !reason.isBlank() ? ": " + reason.trim() : "") + ".");
            return TransactionApprovalResponse.from(a);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransactionApprovalService.reject", e);
        }
    }

    private TransactionApproval requirePending(Long familyId, Long approvalId) {
        TransactionApproval a = approvalDao.selectById(approvalId)
                .filter(x -> x.getFamilyId().equals(familyId))
                .orElseThrow(() -> logged(log, new NotFoundException("Yêu cầu duyệt không tồn tại: " + approvalId)));
        if (!STATUS_PENDING.equals(a.getStatus())) {
            throw logged(log, new ConflictException("Yêu cầu này đã được xử lý"));
        }
        return a;
    }

    private void decide(TransactionApproval a, String status, Long userId, String userName, String reason) {
        a.setStatus(status);
        a.setDecidedByUserId(userId);
        a.setDecidedByName(truncate(userName));
        a.setDecidedAt(LocalDateTime.now(clock));
        a.setRejectReason(reason);
    }

    private void notifyRequester(TransactionApproval a, Long ownerUserId, String ownerName, String title, String message) {
        eventPublisher.publishEvent(ExpenseEvent.notice(ExpenseEvent.APPROVAL_DECIDED, a.getFamilyId(), ownerUserId,
                ownerName, a.getRequesterUserId(), null, title, message, "/transactions"));
    }

    private BigDecimal threshold(Long familyId) {
        return familySettingDao.selectByFamilyId(familyId).map(FamilySetting::getApprovalThreshold).orElse(null);
    }

    private static String noteSuffix(String note) {
        return note != null && !note.isBlank() ? " (\"" + note.trim() + "\")" : "";
    }

    private static String truncate(String name) {
        return name != null && name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }
}
