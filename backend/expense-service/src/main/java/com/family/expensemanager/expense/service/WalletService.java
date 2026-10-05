package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.ConflictException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.common.exception.ServiceException;
import com.family.expensemanager.expense.dao.LoanDao;
import com.family.expensemanager.expense.dao.RecurringTransactionDao;
import com.family.expensemanager.expense.dao.SavingsGoalDao;
import com.family.expensemanager.expense.dao.TransactionDao;
import com.family.expensemanager.expense.dao.WalletAdjustmentDao;
import com.family.expensemanager.expense.dao.WalletDao;
import com.family.expensemanager.expense.dao.WalletTransferDao;
import com.family.expensemanager.expense.domain.entity.Wallet;
import com.family.expensemanager.expense.dto.CreateWalletRequest;
import com.family.expensemanager.expense.dto.WalletResponse;
import java.io.UncheckedIOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.family.expensemanager.common.exception.ExceptionLogger.logged;

/**
 * @author boyquynhluu
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j(topic = "WalletService")
public class WalletService {

    private static final String TYPE_INCOME = "INCOME";
    private static final String TYPE_EXPENSE = "EXPENSE";
    static final String TYPE_CASH = "CASH";
    static final String TYPE_CREDIT_CARD = "CREDIT_CARD";
    static final String TYPE_SAVINGS = "SAVINGS";
    private static final int MAX_PAGE_SIZE = 100;

    private final WalletDao walletDao;
    private final TransactionDao transactionDao;
    private final RecurringTransactionDao recurringTransactionDao;
    private final WalletTransferDao walletTransferDao;
    private final WalletAdjustmentDao walletAdjustmentDao;
    private final PeriodLockService periodLockService;
    private final EntityAuditService entityAuditService;
    private final LoanDao loanDao;
    private final SavingsGoalDao savingsGoalDao;

    @PreAuthorize("hasRole('OWNER')")
    public WalletResponse create(Long familyId, CreateWalletRequest request) {
        try {
            log.info("create - start, familyId={}, name={}", familyId, request.name());
            String currency = normalizeCurrency(request.currency());
            requireConsistentCurrency(familyId, currency, null);
            requireUniqueName(familyId, request.name(), null);
            Wallet wallet = new Wallet();
            wallet.setFamilyId(familyId);
            wallet.setName(request.name());
            wallet.setCurrency(currency);
            wallet.setInitialBalance(request.initialBalance());
            wallet.setOwnerUserId(request.ownerUserId());
            applyType(wallet, request);
            walletDao.insert(wallet);
            WalletResponse response = WalletResponse.from(wallet);
            entityAuditService.record(familyId, EntityAuditService.WALLET, wallet.getId(),
                    EntityAuditService.ACTION_CREATED, null, response);
            return response;
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("WalletService.create", e);
        }
    }

    public List<WalletResponse> listByFamily(Long familyId) {
        try {
            log.info("listByFamily - start, familyId={}", familyId);
            return walletDao.selectByFamilyId(familyId).stream()
                    .map(wallet -> WalletResponse.from(wallet, currentBalanceOf(wallet)))
                    .toList();
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("WalletService.listByFamily", e);
        }
    }

    @PreAuthorize("hasRole('OWNER')")
    public WalletResponse update(Long walletId, Long familyId, CreateWalletRequest request) {
        try {
            log.info("update - start, walletId={}, familyId={}", walletId, familyId);
            Wallet wallet = requireOwnedByFamily(walletId, familyId);
            // Audit snapshots carry the wallet's own fields only (its balance is derived, not edited).
            WalletResponse before = WalletResponse.from(wallet);
            String currency = normalizeCurrency(request.currency());
            requireConsistentCurrency(familyId, currency, walletId);
            requireUniqueName(familyId, request.name(), walletId);
            // The initial balance feeds every month's opening balance — changing it would silently rewrite the
            // balances of months already closed (README B2). A balance correction goes through an adjustment.
            if (wallet.getInitialBalance().compareTo(request.initialBalance()) != 0
                    && periodLockService.hasAnyLock(familyId)) {
                throw logged(log, new ConflictException("Gia đình đã có tháng chốt sổ nên không đổi được số dư ban đầu của ví"
                        + " — hãy dùng \"Điều chỉnh số dư\" để sửa số dư hiện tại"));
            }
            wallet.setName(request.name());
            wallet.setCurrency(currency);
            wallet.setInitialBalance(request.initialBalance());
            wallet.setOwnerUserId(request.ownerUserId());
            applyType(wallet, request);
            walletDao.update(wallet);
            entityAuditService.record(familyId, EntityAuditService.WALLET, walletId, EntityAuditService.ACTION_UPDATED,
                    before, WalletResponse.from(wallet));
            return WalletResponse.from(wallet, currentBalanceOf(wallet));
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("WalletService.update", e);
        }
    }

    /** Soft-delete (README "10. Xoá là mất vĩnh viễn") — the row stays, just hidden, so {@link #restore} can undo it. */
    @PreAuthorize("hasRole('OWNER')")
    public void delete(Long walletId, Long familyId) {
        try {
            log.info("delete - start, walletId={}, familyId={}", walletId, familyId);
            Wallet wallet = requireOwnedByFamily(walletId, familyId);
            if (transactionDao.countByWalletId(walletId) > 0) {
                throw logged(log, new ConflictException("Không thể xoá ví đã có giao dịch"));
            }
            if (recurringTransactionDao.countByWalletId(walletId) > 0) {
                throw logged(log, new ConflictException("Không thể xoá ví đang có giao dịch định kỳ"));
            }
            if (walletTransferDao.countByWalletId(walletId) > 0) {
                throw logged(log, new ConflictException("Không thể xoá ví đã có giao dịch chuyển tiền"));
            }
            if (walletAdjustmentDao.countByWalletId(walletId) > 0) {
                throw logged(log, new ConflictException("Không thể xoá ví đã có điều chỉnh số dư"));
            }
            if (loanDao.countByWalletId(walletId) > 0) {
                throw logged(log, new ConflictException("Không thể xoá ví đã có khoản vay hoặc lần trả nợ"));
            }
            if (savingsGoalDao.countByWalletId(walletId) > 0) {
                throw logged(log, new ConflictException("Không thể xoá ví đang gắn với mục tiêu tiết kiệm"));
            }
            wallet.setDeletedAt(LocalDateTime.now());
            walletDao.update(wallet);
            entityAuditService.record(familyId, EntityAuditService.WALLET, walletId, EntityAuditService.ACTION_DELETED,
                    WalletResponse.from(wallet), null);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("WalletService.delete", e);
        }
    }

    public PageResponse<WalletResponse> listDeletedPaged(Long familyId, int page, int size) {
        try {
            log.info("listDeletedPaged - start, familyId={}, page={}, size={}", familyId, page, size);
            if (page < 0) {
                throw logged(log, new BadRequestException("page phải >= 0"));
            }
            if (size < 1 || size > MAX_PAGE_SIZE) {
                throw logged(log, new BadRequestException("size phải trong khoảng 1-" + MAX_PAGE_SIZE));
            }
            long totalElements = walletDao.countDeletedByFamilyId(familyId);
            List<WalletResponse> content = walletDao.selectDeletedByFamilyIdPaged(familyId, size, page * size).stream()
                    .map(WalletResponse::from)
                    .toList();
            return PageResponse.of(content, page, size, totalElements);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("WalletService.listDeletedPaged", e);
        }
    }

    @PreAuthorize("hasRole('OWNER')")
    public void restore(Long walletId, Long familyId) {
        try {
            log.info("restore - start, walletId={}, familyId={}", walletId, familyId);
            if (walletDao.restore(walletId, familyId) == 0) {
                throw logged(log, new NotFoundException("Ví đã xoá không tồn tại: " + walletId));
            }
            entityAuditService.record(familyId, EntityAuditService.WALLET, walletId, EntityAuditService.ACTION_RESTORED,
                    null, null);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("WalletService.restore", e);
        }
    }

    /**
     * C3: stores the wallet type and only the fields that belong to it (a CASH wallet carries no credit limit).
     * A CREDIT_CARD needs a credit limit — it is what bounds how far the card may go negative (A1).
     */
    private void applyType(Wallet wallet, CreateWalletRequest request) {
        String type = request.walletType() == null ? TYPE_CASH : request.walletType();
        if (TYPE_CREDIT_CARD.equals(type) && request.creditLimit() == null) {
            throw logged(log, new BadRequestException("Thẻ tín dụng cần có hạn mức tín dụng"));
        }
        wallet.setWalletType(type);
        boolean card = TYPE_CREDIT_CARD.equals(type);
        boolean savings = TYPE_SAVINGS.equals(type);
        wallet.setCreditLimit(card ? request.creditLimit() : null);
        wallet.setStatementDay(card ? request.statementDay() : null);
        wallet.setPaymentDueDay(card ? request.paymentDueDay() : null);
        wallet.setInterestRate(savings ? request.interestRate() : null);
        wallet.setMaturityDate(savings ? request.maturityDate() : null);
    }

    /**
     * README A1: the lowest balance a wallet may reach — 0 for cash, bank and savings, minus the credit limit for a
     * credit card.
     */
    BigDecimal balanceFloorOf(Wallet wallet) {
        if (TYPE_CREDIT_CARD.equals(wallet.getWalletType()) && wallet.getCreditLimit() != null) {
            return wallet.getCreditLimit().negate();
        }
        return BigDecimal.ZERO;
    }

    /**
     * README A1: refuses taking {@code outflow} out of the wallet when that would push it below its floor
     * ({@link #balanceFloorOf}). Locks the wallet row first, so two concurrent expenses can't both pass on the same
     * balance. A zero or negative outflow (money coming in) always passes.
     */
    public void requireAllowedOutflow(Wallet wallet, BigDecimal outflow) {
        if (outflow == null || outflow.signum() <= 0) {
            return;
        }
        lockForUpdate(wallet.getId());
        BigDecimal current = currentBalanceOf(wallet);
        BigDecimal floor = balanceFloorOf(wallet);
        if (current.subtract(outflow).compareTo(floor) >= 0) {
            return;
        }
        String available = com.family.expensemanager.common.currency.CurrencyUtil.formatCurrency(current.subtract(floor));
        throw logged(log, new BadRequestException(TYPE_CREDIT_CARD.equals(wallet.getWalletType())
                ? "Thẻ \"" + wallet.getName() + "\" chỉ còn " + available + " trong hạn mức tín dụng — không đủ cho khoản này"
                : "Ví \"" + wallet.getName() + "\" chỉ còn " + available + " — không đủ cho khoản này (ví tiền mặt, ngân hàng"
                        + " và tiết kiệm không được âm; nếu số dư trong ứng dụng sai, hãy điều chỉnh số dư)"));
    }

    /** "vnd" and "VND " are the same currency — store and compare the ISO code in upper case. */
    private static String normalizeCurrency(String currency) {
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * All wallets in a family must share one currency — the Dashboard/Summary totals
     * simply sum amounts across wallets with no exchange-rate conversion, so mixing
     * currencies would silently produce a meaningless total.
     */
    private void requireConsistentCurrency(Long familyId, String currency, Long excludeWalletId) {
        boolean mismatch = walletDao.selectByFamilyId(familyId).stream()
                .filter(w -> excludeWalletId == null || !w.getId().equals(excludeWalletId))
                .anyMatch(w -> !w.getCurrency().equals(currency));
        if (mismatch) {
            throw logged(log, new ConflictException("Tất cả ví trong gia đình phải dùng chung 1 loại tiền tệ"));
        }
    }

    /** Case/whitespace-insensitive: "Ví chính" and "ví chính " count as the same name within one family. */
    private void requireUniqueName(Long familyId, String name, Long excludeWalletId) {
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        boolean duplicate = walletDao.selectByFamilyId(familyId).stream()
                .filter(w -> excludeWalletId == null || !w.getId().equals(excludeWalletId))
                .anyMatch(w -> w.getName().trim().toLowerCase(Locale.ROOT).equals(normalized));
        if (duplicate) {
            throw logged(log, new ConflictException("Đã có ví tên \"" + name + "\" trong gia đình"));
        }
    }

    /** Package-visible so {@link WalletTransferService} can reuse the exact same calculation (initial balance + income
     *  - expense + transfers in - transfers out + adjustments + loan flows) instead of a separate, drifting copy. */
    BigDecimal currentBalanceOf(Wallet wallet) {
        BigDecimal income = transactionDao.sumAmountByWalletAndType(wallet.getId(), TYPE_INCOME);
        BigDecimal expense = transactionDao.sumAmountByWalletAndType(wallet.getId(), TYPE_EXPENSE);
        BigDecimal transferIn = walletTransferDao.sumAmountIntoWallet(wallet.getId());
        BigDecimal transferOut = walletTransferDao.sumAmountFromWallet(wallet.getId());
        BigDecimal adjustments = walletAdjustmentDao.sumAmountByWalletId(wallet.getId());
        BigDecimal loans = loanDao.sumNetFlowForWallet(wallet.getId());
        return wallet.getInitialBalance().add(income).subtract(expense).add(transferIn).subtract(transferOut)
                .add(adjustments).add(loans);
    }

    /**
     * Row-locks the wallet until the caller's transaction ends, so two concurrent transfers out of it can't
     * both read the same balance, both pass the "enough money" check, and together overdraw it.
     */
    void lockForUpdate(Long walletId) {
        walletDao.selectByIdForUpdate(walletId);
    }

    /**
     * Who may put money into / take money out of a wallet: the family OWNER (any wallet), anyone for a
     * shared wallet (no owner), otherwise only the member it belongs to. Viewing is not restricted —
     * every member still sees every wallet and its balance.
     */
    static boolean canUse(Wallet wallet, Long userId, boolean callerIsOwner) {
        return callerIsOwner || wallet.getOwnerUserId() == null || wallet.getOwnerUserId().equals(userId);
    }

    /** {@link #requireOwnedByFamily} plus {@link #canUse} — for a wallet the caller is about to record money against. */
    Wallet requireUsableBy(Long walletId, Long familyId, Long userId, boolean callerIsOwner) {
        Wallet wallet = requireOwnedByFamily(walletId, familyId);
        requireUsableBy(wallet, userId, callerIsOwner);
        return wallet;
    }

    void requireUsableBy(Wallet wallet, Long userId, boolean callerIsOwner) {
        if (!canUse(wallet, userId, callerIsOwner)) {
            throw logged(log, new ApiException(HttpStatus.FORBIDDEN,
                    "Ví \"" + wallet.getName() + "\" là ví riêng của thành viên khác — bạn chỉ dùng được ví của mình và ví chung"));
        }
    }

    /** Transfers go OUT of the sender's own private wallet only — not a shared one, not another member's. */
    void requireTransferSource(Wallet wallet, Long senderUserId) {
        if (wallet.getOwnerUserId() == null) {
            throw logged(log, new ApiException(HttpStatus.FORBIDDEN,
                    "Không chuyển tiền từ ví chung \"" + wallet.getName() + "\" — chỉ chuyển được từ ví riêng của bạn"));
        }
        if (!wallet.getOwnerUserId().equals(senderUserId)) {
            throw logged(log, new ApiException(HttpStatus.FORBIDDEN,
                    "Ví \"" + wallet.getName() + "\" không phải ví riêng của bạn — chỉ chuyển được từ ví riêng của bạn"));
        }
    }

    /** ...and INTO someone else's private wallet or a shared one — never back into one of the sender's own. */
    void requireTransferDestination(Wallet wallet, Long senderUserId) {
        if (wallet.getOwnerUserId() != null && wallet.getOwnerUserId().equals(senderUserId)) {
            throw logged(log, new BadRequestException(
                    "Không chuyển vào ví của chính bạn (\"" + wallet.getName() + "\") — hãy chọn ví của thành viên khác hoặc ví chung"));
        }
    }

    Wallet requireOwnedByFamily(Long walletId, Long familyId) {
        log.info("requireOwnedByFamily - start, walletId={}, familyId={}", walletId, familyId);
        Wallet wallet = walletDao.selectById(walletId)
                .orElseThrow(() -> logged(log, new NotFoundException("Wallet không tồn tại: " + walletId)));
        if (!wallet.getFamilyId().equals(familyId)) {
            throw logged(log, new NotFoundException("Wallet không tồn tại: " + walletId));
        }
        return wallet;
    }
}
