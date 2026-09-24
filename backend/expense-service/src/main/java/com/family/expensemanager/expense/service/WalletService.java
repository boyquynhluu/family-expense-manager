package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.ConflictException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.common.exception.ServiceException;
import com.family.expensemanager.expense.dao.RecurringTransactionDao;
import com.family.expensemanager.expense.dao.TransactionDao;
import com.family.expensemanager.expense.dao.WalletDao;
import com.family.expensemanager.expense.dao.WalletTransferDao;
import com.family.expensemanager.expense.domain.entity.Wallet;
import com.family.expensemanager.expense.dto.CreateWalletRequest;
import com.family.expensemanager.expense.dto.WalletResponse;
import java.io.UncheckedIOException;
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

@Service
@Transactional
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

    @PreAuthorize("hasRole('OWNER')")
    public WalletResponse create(Long familyId, CreateWalletRequest request) {
        try {
            log.info("create - start, familyId={}, name={}", familyId, request.name());
            String currency = normalizeCurrency(request.currency());
            requireConsistentCurrency(familyId, currency, null);
            Wallet wallet = new Wallet();
            wallet.setFamilyId(familyId);
            wallet.setName(request.name());
            wallet.setCurrency(currency);
            wallet.setInitialBalance(request.initialBalance());
            walletDao.insert(wallet);
            return WalletResponse.from(wallet);
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
            String currency = normalizeCurrency(request.currency());
            requireConsistentCurrency(familyId, currency, walletId);
            wallet.setName(request.name());
            wallet.setCurrency(currency);
            wallet.setInitialBalance(request.initialBalance());
            walletDao.update(wallet);
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
            wallet.setDeletedAt(LocalDateTime.now());
            walletDao.update(wallet);
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
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("WalletService.restore", e);
        }
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

    /** Package-visible so {@link WalletTransferService} can reuse the exact same calculation (initial
     *  balance + income - expense + transfers in - transfers out) instead of a separate, drifting copy. */
    BigDecimal currentBalanceOf(Wallet wallet) {
        BigDecimal income = transactionDao.sumAmountByWalletAndType(wallet.getId(), TYPE_INCOME);
        BigDecimal expense = transactionDao.sumAmountByWalletAndType(wallet.getId(), TYPE_EXPENSE);
        BigDecimal transferIn = walletTransferDao.sumAmountIntoWallet(wallet.getId());
        BigDecimal transferOut = walletTransferDao.sumAmountFromWallet(wallet.getId());
        return wallet.getInitialBalance().add(income).subtract(expense).add(transferIn).subtract(transferOut);
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
