package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.currency.CurrencyUtil;
import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.event.ExpenseEvent;
import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.common.exception.ServiceException;
import com.family.expensemanager.expense.dao.WalletAdjustmentDao;
import com.family.expensemanager.expense.domain.entity.Wallet;
import com.family.expensemanager.expense.domain.entity.WalletAdjustment;
import com.family.expensemanager.expense.dto.WalletAdjustmentRequest;
import com.family.expensemanager.expense.dto.WalletAdjustmentResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.family.expensemanager.common.exception.ExceptionLogger.logged;

/**
 * "Điều chỉnh số dư" (README B1): the member types the balance a wallet REALLY holds and the difference from
 * the app's balance is recorded as an adjustment. Like a transfer it only moves the wallet balance (see
 * {@link WalletService#currentBalanceOf}) — never income/expense, budgets or category reports — so reconciling
 * no longer needs a fake income/expense entry.
 * <p>
 * Who: the family OWNER for any wallet, otherwise only the owner of a private wallet for that wallet. Unlike
 * recording a transaction, a plain member may NOT adjust a shared wallet: an adjustment can set any balance.
 * Every adjustment is announced to the family (in app + email to everyone but the one who made it).
 *
 * @author boyquynhluu
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j(topic = "WalletAdjustmentService")
public class WalletAdjustmentService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_NAME_LENGTH = 100;

    private final WalletAdjustmentDao walletAdjustmentDao;
    private final WalletService walletService;
    private final PeriodLockService periodLockService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public WalletAdjustmentResponse create(Long familyId, Long userId, String userName, boolean callerIsOwner,
                                           WalletAdjustmentRequest request) {
        try {
            log.info("create - start, familyId={}, walletId={}", familyId, request.walletId());
            Wallet wallet = walletService.requireOwnedByFamily(request.walletId(), familyId);
            requireCanAdjust(wallet, userId, callerIsOwner);
            LocalDateTime now = LocalDateTime.now(clock);
            periodLockService.requireUnlocked(familyId, now);

            // Locked first, so a transfer out of this wallet can't change the balance between reading it and
            // storing the difference.
            walletService.lockForUpdate(wallet.getId());
            BigDecimal before = walletService.currentBalanceOf(wallet);
            BigDecimal difference = request.actualBalance().subtract(before);
            if (difference.signum() == 0) {
                throw logged(log, new BadRequestException("Số dư thực tế đã khớp với số dư trong ứng dụng — không cần điều chỉnh"));
            }

            WalletAdjustment adjustment = new WalletAdjustment();
            adjustment.setFamilyId(familyId);
            adjustment.setWalletId(wallet.getId());
            adjustment.setAmount(difference);
            adjustment.setBalanceBefore(before);
            adjustment.setBalanceAfter(request.actualBalance());
            adjustment.setNote(request.note());
            adjustment.setOccurredAt(now);
            adjustment.setCreatedByUserId(userId);
            adjustment.setCreatedByName(truncateName(userName));
            adjustment.setCreatedAt(now);
            walletAdjustmentDao.insert(adjustment);
            String actor = userName != null ? userName : "Một thành viên";
            String message = actor + " đã điều chỉnh số dư ví " + wallet.getName() + ": "
                    + CurrencyUtil.formatCurrency(before) + " → " + CurrencyUtil.formatCurrency(request.actualBalance())
                    + " (" + (difference.signum() > 0 ? "+" : "") + CurrencyUtil.formatCurrency(difference) + ")"
                    + (request.note() != null && !request.note().isBlank() ? ". Ghi chú: " + request.note() : "") + ".";
            eventPublisher.publishEvent(ExpenseEvent.notice(ExpenseEvent.WALLET_ADJUSTED, familyId, userId, userName,
                    null, null, "Số dư ví đã được điều chỉnh", message, "/wallets"));
            return WalletAdjustmentResponse.from(adjustment);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("WalletAdjustmentService.create", e);
        }
    }

    /** {@code walletId} null = adjustments of every wallet of the family. Visible to every member, like transfers. */
    public PageResponse<WalletAdjustmentResponse> listPaged(Long familyId, Long walletId, int page, int size) {
        try {
            log.info("listPaged - start, familyId={}, walletId={}, page={}, size={}", familyId, walletId, page, size);
            if (page < 0) {
                throw logged(log, new BadRequestException("page phải >= 0"));
            }
            if (size < 1 || size > MAX_PAGE_SIZE) {
                throw logged(log, new BadRequestException("size phải trong khoảng 1-" + MAX_PAGE_SIZE));
            }
            long total = walletAdjustmentDao.countByFamilyId(familyId, walletId);
            List<WalletAdjustmentResponse> content =
                    walletAdjustmentDao.selectByFamilyIdPaged(familyId, walletId, size, page * size).stream()
                            .map(WalletAdjustmentResponse::from)
                            .toList();
            return PageResponse.of(content, page, size, total);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("WalletAdjustmentService.listPaged", e);
        }
    }

    /** Undoes a mistaken adjustment (hard delete, like a transfer): its creator or the OWNER. */
    public void delete(Long familyId, Long adjustmentId, Long userId, boolean callerIsOwner) {
        try {
            log.info("delete - start, familyId={}, adjustmentId={}", familyId, adjustmentId);
            WalletAdjustment adjustment = walletAdjustmentDao.selectById(adjustmentId)
                    .filter(a -> a.getFamilyId().equals(familyId))
                    .orElseThrow(() -> logged(log, new NotFoundException("Điều chỉnh số dư không tồn tại: " + adjustmentId)));
            if (!callerIsOwner && !Objects.equals(adjustment.getCreatedByUserId(), userId)) {
                throw logged(log, new ApiException(HttpStatus.FORBIDDEN,
                        "Chỉ chủ hộ hoặc người tạo mới được xoá điều chỉnh số dư"));
            }
            periodLockService.requireUnlocked(familyId, adjustment.getOccurredAt());
            walletAdjustmentDao.delete(adjustment);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("WalletAdjustmentService.delete", e);
        }
    }

    private void requireCanAdjust(Wallet wallet, Long userId, boolean callerIsOwner) {
        if (callerIsOwner || (wallet.getOwnerUserId() != null && wallet.getOwnerUserId().equals(userId))) {
            return;
        }
        throw logged(log, new ApiException(HttpStatus.FORBIDDEN, wallet.getOwnerUserId() == null
                ? "Chỉ chủ hộ mới được điều chỉnh số dư ví chung \"" + wallet.getName() + "\""
                : "Ví \"" + wallet.getName() + "\" là ví riêng của thành viên khác — chỉ chủ ví hoặc chủ hộ được điều chỉnh"));
    }

    private static String truncateName(String name) {
        return name != null && name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }
}
