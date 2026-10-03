package com.family.expensemanager.expense.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.family.expensemanager.expense.dao.CategoryDao;
import com.family.expensemanager.expense.dao.TransactionDao;
import com.family.expensemanager.expense.dao.WalletDao;
import com.family.expensemanager.expense.domain.entity.Category;
import com.family.expensemanager.expense.domain.entity.Transaction;
import com.family.expensemanager.expense.domain.entity.Wallet;
import com.family.expensemanager.expense.dto.TransactionSnapshot;

import lombok.RequiredArgsConstructor;

/**
 * The actual DELETE of one trashed row, each in its OWN database transaction (REQUIRES_NEW): emptying the trash
 * or the nightly retention job purge many rows, and one that fails (e.g. a wallet someone still uses) must not
 * roll back the ones already gone. Checks (who may, what still references the row) live in {@link TrashService}.
 *
 * @author boyquynhluu
 */
@Component
@Transactional(propagation = Propagation.REQUIRES_NEW)
@RequiredArgsConstructor
public class TrashPurger {

    private final TransactionDao transactionDao;
    private final WalletDao walletDao;
    private final CategoryDao categoryDao;
    private final TransactionAuditService auditService;

    /** The audit row is written in the same transaction, so the history always says who removed it for good. */
    public void purgeTransaction(Transaction transaction, Long actorUserId, String actorName) {
        transactionDao.delete(transaction);
        auditService.record(TransactionAuditService.ACTION_PURGED, transaction.getFamilyId(), transaction.getId(),
                TransactionSnapshot.of(transaction), null, actorUserId, actorName);
    }

    public void purgeWallet(Wallet wallet) {
        walletDao.delete(wallet);
    }

    public void purgeCategory(Category category) {
        categoryDao.delete(category);
    }
}
