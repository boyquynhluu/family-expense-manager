package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.ConflictException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.expense.dao.RecurringTransactionDao;
import com.family.expensemanager.expense.dao.TransactionDao;
import com.family.expensemanager.expense.dao.WalletDao;
import com.family.expensemanager.expense.dao.WalletTransferDao;
import com.family.expensemanager.expense.domain.entity.Wallet;
import com.family.expensemanager.expense.dto.CreateWalletRequest;
import com.family.expensemanager.expense.dto.WalletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "WalletService")
public class WalletService {

    private static final String TYPE_INCOME = "INCOME";
    private static final String TYPE_EXPENSE = "EXPENSE";
    private static final int MAX_PAGE_SIZE = 100;

    private final WalletDao walletDao;
    private final TransactionDao transactionDao;
    private final RecurringTransactionDao recurringTransactionDao;
    private final WalletTransferDao walletTransferDao;

    @Transactional
    @PreAuthorize("hasRole('OWNER')")
    public WalletResponse create(Long familyId, CreateWalletRequest request) {
        log.info("create - start, familyId={}, name={}", familyId, request.name());
        requireConsistentCurrency(familyId, request.currency(), null);
        Wallet wallet = new Wallet();
        wallet.setFamilyId(familyId);
        wallet.setName(request.name());
        wallet.setCurrency(request.currency());
        wallet.setInitialBalance(request.initialBalance());
        walletDao.insert(wallet);
        return WalletResponse.from(wallet);
    }

    public List<WalletResponse> listByFamily(Long familyId) {
        log.info("listByFamily - start, familyId={}", familyId);
        return walletDao.selectByFamilyId(familyId).stream()
                .map(wallet -> WalletResponse.from(wallet, currentBalanceOf(wallet)))
                .toList();
    }

    @Transactional
    @PreAuthorize("hasRole('OWNER')")
    public WalletResponse update(Long walletId, Long familyId, CreateWalletRequest request) {
        log.info("update - start, walletId={}, familyId={}", walletId, familyId);
        Wallet wallet = requireOwnedByFamily(walletId, familyId);
        requireConsistentCurrency(familyId, request.currency(), walletId);
        wallet.setName(request.name());
        wallet.setCurrency(request.currency());
        wallet.setInitialBalance(request.initialBalance());
        walletDao.update(wallet);
        return WalletResponse.from(wallet, currentBalanceOf(wallet));
    }

    /** Soft-delete (README "10. Xoá là mất vĩnh viễn") — the row stays, just hidden, so {@link #restore} can undo it. */
    @Transactional
    @PreAuthorize("hasRole('OWNER')")
    public void delete(Long walletId, Long familyId) {
        log.info("delete - start, walletId={}, familyId={}", walletId, familyId);
        Wallet wallet = requireOwnedByFamily(walletId, familyId);
        if (transactionDao.countByWalletId(walletId) > 0) {
            throw new ConflictException("Không thể xoá ví đã có giao dịch");
        }
        if (recurringTransactionDao.countByWalletId(walletId) > 0) {
            throw new ConflictException("Không thể xoá ví đang có giao dịch định kỳ");
        }
        if (walletTransferDao.countByWalletId(walletId) > 0) {
            throw new ConflictException("Không thể xoá ví đã có giao dịch chuyển tiền");
        }
        wallet.setDeletedAt(LocalDateTime.now());
        walletDao.update(wallet);
    }

    public PageResponse<WalletResponse> listDeletedPaged(Long familyId, int page, int size) {
        log.info("listDeletedPaged - start, familyId={}, page={}, size={}", familyId, page, size);
        if (page < 0) {
            throw new BadRequestException("page phải >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size phải trong khoảng 1-" + MAX_PAGE_SIZE);
        }
        long totalElements = walletDao.countDeletedByFamilyId(familyId);
        List<WalletResponse> content = walletDao.selectDeletedByFamilyIdPaged(familyId, size, page * size).stream()
                .map(WalletResponse::from)
                .toList();
        return PageResponse.of(content, page, size, totalElements);
    }

    @Transactional
    @PreAuthorize("hasRole('OWNER')")
    public void restore(Long walletId, Long familyId) {
        log.info("restore - start, walletId={}, familyId={}", walletId, familyId);
        if (walletDao.restore(walletId, familyId) == 0) {
            throw new NotFoundException("Ví đã xoá không tồn tại: " + walletId);
        }
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
            throw new ConflictException("Tất cả ví trong gia đình phải dùng chung 1 loại tiền tệ");
        }
    }

    private BigDecimal currentBalanceOf(Wallet wallet) {
        BigDecimal income = transactionDao.sumAmountByWalletAndType(wallet.getId(), TYPE_INCOME);
        BigDecimal expense = transactionDao.sumAmountByWalletAndType(wallet.getId(), TYPE_EXPENSE);
        BigDecimal transferIn = walletTransferDao.sumAmountIntoWallet(wallet.getId());
        BigDecimal transferOut = walletTransferDao.sumAmountFromWallet(wallet.getId());
        return wallet.getInitialBalance().add(income).subtract(expense).add(transferIn).subtract(transferOut);
    }

    Wallet requireOwnedByFamily(Long walletId, Long familyId) {
        log.info("requireOwnedByFamily - start, walletId={}, familyId={}", walletId, familyId);
        Wallet wallet = walletDao.selectById(walletId)
                .orElseThrow(() -> new NotFoundException("Wallet không tồn tại: " + walletId));
        if (!wallet.getFamilyId().equals(familyId)) {
            throw new NotFoundException("Wallet không tồn tại: " + walletId);
        }
        return wallet;
    }
}
