package com.family.expensemanager.expense.service;

import static com.family.expensemanager.common.exception.ExceptionLogger.logged;

import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.ConflictException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.common.exception.ServiceException;
import com.family.expensemanager.expense.dao.BudgetDao;
import com.family.expensemanager.expense.dao.CategoryDao;
import com.family.expensemanager.expense.dao.LoanDao;
import com.family.expensemanager.expense.dao.RecurringTransactionDao;
import com.family.expensemanager.expense.dao.SavingsGoalDao;
import com.family.expensemanager.expense.dao.TransactionDao;
import com.family.expensemanager.expense.dao.TransactionSplitDao;
import com.family.expensemanager.expense.dao.WalletDao;
import com.family.expensemanager.expense.dao.WalletTransferDao;
import com.family.expensemanager.expense.domain.entity.Category;
import com.family.expensemanager.expense.domain.entity.Transaction;
import com.family.expensemanager.expense.domain.entity.Wallet;
import com.family.expensemanager.expense.dto.TrashCountResponse;
import com.family.expensemanager.expense.dto.TrashPurgeResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Trash counter plus permanent deletion. The single-row buttons, "Dọn sạch thùng rác" and the nightly
 * retention job ({@link TrashRetentionScheduler}) all go through the same per-row steps below, so the rules
 * (what still blocks a wallet/category, receipt files, the PURGED audit entry) can't drift apart.
 *
 * <p>Order matters when sweeping: transactions first, then wallets and categories — a trashed transaction
 * still holds its wallet/category foreign key, so purging it is what frees them.
 *
 * @author boyquynhluu
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j(topic = "TrashService")
public class TrashService {

    /** Audit actor name for rows removed by the retention job rather than by a person. */
    static final String RETENTION_ACTOR = "Hệ thống (tự dọn thùng rác)";

    private final WalletDao walletDao;
    private final CategoryDao categoryDao;
    private final TransactionDao transactionDao;
    private final RecurringTransactionDao recurringTransactionDao;
    private final WalletTransferDao walletTransferDao;
    private final BudgetDao budgetDao;
    private final LoanDao loanDao;
    private final TransactionSplitDao transactionSplitDao;
    private final SavingsGoalDao savingsGoalDao;
    private final ReceiptStorageService receiptStorageService;
    private final TrashPurger purger;

    public TrashCountResponse getTotalTrash(Long familyId) {
        Long total;
        try {
            total = walletDao.countDeletedByFamilyId(familyId)
                + categoryDao.countDeletedByFamilyId(familyId)
                + transactionDao.countDeletedByFamilyId(familyId);

        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TrashService.getTotalTrash", e);
        }
        return new TrashCountResponse(total);
    }

    /**
     * Same rights as restoring it: the creator or the OWNER — except another member's PRIVATE transaction,
     * which answers 404 even to the OWNER (exactly like restore and every other read of it).
     */
    public void purgeTransaction(Long familyId, Long transactionId, Long callerUserId, String callerName,
                                 boolean callerIsOwner) {
        try {
            log.info("purgeTransaction - start, familyId={}, transactionId={}", familyId, transactionId);
            Transaction transaction = transactionDao.selectDeletedById(transactionId)
                    .filter(t -> t.getFamilyId().equals(familyId) && t.isVisibleTo(callerUserId))
                    .orElseThrow(() -> logged(log, new NotFoundException(
                            "Giao dịch trong thùng rác không tồn tại: " + transactionId)));
            if (!callerIsOwner && !Objects.equals(transaction.getUserId(), callerUserId)) {
                throw logged(log, new ApiException(HttpStatus.FORBIDDEN,
                        "Chỉ chủ hộ hoặc người tạo giao dịch mới có quyền xoá vĩnh viễn giao dịch này"));
            }
            purgeTransactionRow(transaction, callerUserId, callerName);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TrashService.purgeTransaction", e);
        }
    }

    @PreAuthorize("hasRole('OWNER')")
    public void purgeWallet(Long familyId, Long walletId) {
        try {
            log.info("purgeWallet - start, familyId={}, walletId={}", familyId, walletId);
            Wallet wallet = walletDao.selectDeletedById(walletId)
                    .filter(w -> w.getFamilyId().equals(familyId))
                    .orElseThrow(() -> logged(log, new NotFoundException("Ví trong thùng rác không tồn tại: " + walletId)));
            purgeWalletRow(wallet);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TrashService.purgeWallet", e);
        }
    }

    @PreAuthorize("hasRole('OWNER')")
    public void purgeCategory(Long familyId, Long categoryId) {
        try {
            log.info("purgeCategory - start, familyId={}, categoryId={}", familyId, categoryId);
            Category category = categoryDao.selectDeletedById(categoryId)
                    .filter(c -> c.getFamilyId().equals(familyId))
                    .orElseThrow(() -> logged(log, new NotFoundException(
                            "Danh mục trong thùng rác không tồn tại: " + categoryId)));
            purgeCategoryRow(category);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TrashService.purgeCategory", e);
        }
    }

    /**
     * "Dọn sạch thùng rác" — OWNER only, and it includes other members' private transactions: only their
     * creator could have put them in the trash, so removing them reveals nothing and follows that intent.
     */
    @PreAuthorize("hasRole('OWNER')")
    public TrashPurgeResult emptyTrash(Long familyId, Long callerUserId, String callerName) {
        try {
            log.info("emptyTrash - start, familyId={}", familyId);
            TrashPurgeResult result = sweep(transactionDao.selectDeletedByFamilyId(familyId),
                    walletDao.selectDeletedByFamilyId(familyId), categoryDao.selectDeletedByFamilyId(familyId),
                    callerUserId, callerName);
            log.info("emptyTrash - done, familyId={}, result={}", familyId, result);
            return result;
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TrashService.emptyTrash", e);
        }
    }

    /** Every family: whatever has been in the trash since before {@code cutoff}. Called by the scheduler only. */
    public TrashPurgeResult purgeDeletedBefore(LocalDateTime cutoff) {
        log.info("purgeDeletedBefore - start, cutoff={}", cutoff);
        TrashPurgeResult result = sweep(transactionDao.selectDeletedBefore(cutoff),
                walletDao.selectDeletedBefore(cutoff), categoryDao.selectDeletedBefore(cutoff), null, RETENTION_ACTOR);
        log.info("purgeDeletedBefore - done, result={}", result);
        return result;
    }

    /**
     * Each row is tried on its own: a wallet/category still referenced is skipped (it stays in the trash and is
     * retried next time), and an unexpected failure on one row is logged without stopping the rest.
     */
    private TrashPurgeResult sweep(List<Transaction> transactions, List<Wallet> wallets, List<Category> categories,
                                   Long actorUserId, String actorName) {
        int purgedTransactions = 0;
        int purgedWallets = 0;
        int purgedCategories = 0;
        int skipped = 0;
        for (Transaction t : transactions) {
            if (attempt("transaction", t.getId(), () -> purgeTransactionRow(t, actorUserId, actorName))) {
                purgedTransactions++;
            } else {
                skipped++;
            }
        }
        for (Wallet w : wallets) {
            if (attempt("wallet", w.getId(), () -> purgeWalletRow(w))) {
                purgedWallets++;
            } else {
                skipped++;
            }
        }
        for (Category c : categories) {
            if (attempt("category", c.getId(), () -> purgeCategoryRow(c))) {
                purgedCategories++;
            } else {
                skipped++;
            }
        }
        return new TrashPurgeResult(purgedTransactions, purgedWallets, purgedCategories, skipped);
    }

    private boolean attempt(String kind, Long id, Runnable purge) {
        try {
            purge.run();
            return true;
        } catch (ConflictException e) {
            log.info("sweep - skipped {} id={}: {}", kind, id, e.getMessage());
            return false;
        } catch (RuntimeException e) {
            log.error("sweep - failed to purge {} id={}", kind, id, e);
            return false;
        }
    }

    /** The receipt file goes only after the row's deletion has committed — a leftover file beats a broken row. */
    private void purgeTransactionRow(Transaction transaction, Long actorUserId, String actorName) {
        if (transactionDao.countAllRefundsOf(transaction.getId()) > 0) {
            throw logged(log, new ConflictException("Giao dịch vẫn còn khoản hoàn tiền (kể cả trong thùng rác). "
                    + "Hãy xoá vĩnh viễn các khoản hoàn tiền đó trước."));
        }
        purger.purgeTransaction(transaction, actorUserId, actorName);
        if (transaction.getReceiptPath() != null) {
            receiptStorageService.delete(transaction.getReceiptPath());
        }
    }

    /** Anything still pointing at the wallet — even from the trash — would trip its foreign key; say what instead. */
    private void purgeWalletRow(Wallet wallet) {
        long transactions = transactionDao.countAllByWalletId(wallet.getId());
        if (transactions > 0) {
            throw logged(log, new ConflictException("Ví \"" + wallet.getName() + "\" vẫn còn " + transactions
                    + " giao dịch (kể cả trong thùng rác). Hãy xoá vĩnh viễn các giao dịch đó trước."));
        }
        if (recurringTransactionDao.countByWalletId(wallet.getId()) > 0) {
            throw logged(log, new ConflictException(
                    "Ví \"" + wallet.getName() + "\" vẫn đang được dùng trong giao dịch định kỳ"));
        }
        if (walletTransferDao.countByWalletId(wallet.getId()) > 0) {
            throw logged(log, new ConflictException(
                    "Ví \"" + wallet.getName() + "\" vẫn còn trong lịch sử chuyển tiền"));
        }
        if (loanDao.countByWalletId(wallet.getId()) > 0 || savingsGoalDao.countByWalletId(wallet.getId()) > 0
                || budgetDao.countByWalletId(wallet.getId()) > 0) {
            throw logged(log, new ConflictException("Ví \"" + wallet.getName()
                    + "\" vẫn được dùng bởi khoản vay, mục tiêu tiết kiệm hoặc ngân sách theo ví"));
        }
        purger.purgeWallet(wallet);
    }

    private void purgeCategoryRow(Category category) {
        long transactions = transactionDao.countAllByCategoryId(category.getId());
        if (transactions > 0) {
            throw logged(log, new ConflictException("Danh mục \"" + category.getName() + "\" vẫn còn " + transactions
                    + " giao dịch (kể cả trong thùng rác). Hãy xoá vĩnh viễn các giao dịch đó trước."));
        }
        if (budgetDao.countByCategoryId(category.getId()) > 0) {
            throw logged(log, new ConflictException(
                    "Danh mục \"" + category.getName() + "\" vẫn đang có ngân sách"));
        }
        if (recurringTransactionDao.countByCategoryId(category.getId()) > 0) {
            throw logged(log, new ConflictException(
                    "Danh mục \"" + category.getName() + "\" vẫn đang được dùng trong giao dịch định kỳ"));
        }
        if (transactionSplitDao.countByCategoryId(category.getId()) > 0 || !categoryDao.selectChildIds(category.getId()).isEmpty()) {
            throw logged(log, new ConflictException("Danh mục \"" + category.getName()
                    + "\" vẫn có danh mục con hoặc phần tách của giao dịch"));
        }
        purger.purgeCategory(category);
    }
}
