package com.family.expensemanager.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.ConflictException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.expense.dao.BudgetDao;
import com.family.expensemanager.expense.dao.CategoryDao;
import com.family.expensemanager.expense.dao.RecurringTransactionDao;
import com.family.expensemanager.expense.dao.TransactionDao;
import com.family.expensemanager.expense.dao.WalletDao;
import com.family.expensemanager.expense.dao.WalletTransferDao;
import com.family.expensemanager.expense.domain.entity.Category;
import com.family.expensemanager.expense.domain.entity.Transaction;
import com.family.expensemanager.expense.domain.entity.Wallet;

/**
 * @author boyquynhluu
 */
@ExtendWith(MockitoExtension.class)
class TrashServiceTest {

    private static final Long FAMILY = 1L;
    private static final Long CREATOR = 10L;

    @Mock private WalletDao walletDao;
    @Mock private CategoryDao categoryDao;
    @Mock private TransactionDao transactionDao;
    @Mock private RecurringTransactionDao recurringTransactionDao;
    @Mock private WalletTransferDao walletTransferDao;
    @Mock private BudgetDao budgetDao;
    @Mock private ReceiptStorageService receiptStorageService;
    @Mock private TrashPurger purger;
    @Mock private com.family.expensemanager.expense.dao.LoanDao loanDao;
    @Mock private com.family.expensemanager.expense.dao.SavingsGoalDao savingsGoalDao;
    @Mock private com.family.expensemanager.expense.dao.TransactionSplitDao transactionSplitDao;

    @InjectMocks private TrashService trashService;

    // --- one transaction

    @Test
    void purgeTransaction_byItsCreator_deletesRow_thenItsReceiptFile() {
        Transaction t = trashedTransaction(5L, CREATOR, false);
        t.setReceiptPath("1/5-receipt.jpg");
        when(transactionDao.selectDeletedById(5L)).thenReturn(Optional.of(t));

        trashService.purgeTransaction(FAMILY, 5L, CREATOR, "An", false);

        InOrder order = inOrder(purger, receiptStorageService);
        order.verify(purger).purgeTransaction(t, CREATOR, "An");
        order.verify(receiptStorageService).delete("1/5-receipt.jpg");
    }

    @Test
    void purgeTransaction_byOwner_ofSomeoneElsesPublicEntry_isAllowed() {
        Transaction t = trashedTransaction(5L, CREATOR, false);
        when(transactionDao.selectDeletedById(5L)).thenReturn(Optional.of(t));

        trashService.purgeTransaction(FAMILY, 5L, 99L, "Chủ hộ", true);

        verify(purger).purgeTransaction(t, 99L, "Chủ hộ");
        verify(receiptStorageService, never()).delete(anyString());
    }

    @Test
    void purgeTransaction_byAnotherMember_isForbidden() {
        when(transactionDao.selectDeletedById(5L)).thenReturn(Optional.of(trashedTransaction(5L, CREATOR, false)));

        assertThatThrownBy(() -> trashService.purgeTransaction(FAMILY, 5L, 99L, "Bình", false))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(purger, never()).purgeTransaction(any(), any(), any());
    }

    @Test
    void purgeTransaction_ofSomeoneElsesPrivateEntry_is404_evenForTheOwner() {
        when(transactionDao.selectDeletedById(5L)).thenReturn(Optional.of(trashedTransaction(5L, CREATOR, true)));

        assertThatThrownBy(() -> trashService.purgeTransaction(FAMILY, 5L, 99L, "Chủ hộ", true))
                .isInstanceOf(NotFoundException.class);
        verify(purger, never()).purgeTransaction(any(), any(), any());
    }

    @Test
    void purgeTransaction_fromAnotherFamily_is404() {
        Transaction t = trashedTransaction(5L, CREATOR, false);
        t.setFamilyId(2L);
        when(transactionDao.selectDeletedById(5L)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> trashService.purgeTransaction(FAMILY, 5L, CREATOR, "An", true))
                .isInstanceOf(NotFoundException.class);
    }

    // --- one wallet / category

    @Test
    void purgeWallet_isBlocked_whileATransactionInTheTrashStillPointsAtIt() {
        when(walletDao.selectDeletedById(3L)).thenReturn(Optional.of(trashedWallet(3L)));
        when(transactionDao.countAllByWalletId(3L)).thenReturn(2L);

        assertThatThrownBy(() -> trashService.purgeWallet(FAMILY, 3L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("vẫn còn 2 giao dịch (kể cả trong thùng rác)");
        verify(purger, never()).purgeWallet(any());
    }

    @Test
    void purgeWallet_isBlocked_byTransferHistory() {
        when(walletDao.selectDeletedById(3L)).thenReturn(Optional.of(trashedWallet(3L)));
        when(walletTransferDao.countByWalletId(3L)).thenReturn(1L);

        assertThatThrownBy(() -> trashService.purgeWallet(FAMILY, 3L)).isInstanceOf(ConflictException.class);
        verify(purger, never()).purgeWallet(any());
    }

    @Test
    void purgeWallet_deletesIt_whenNothingReferencesItAnymore() {
        Wallet wallet = trashedWallet(3L);
        when(walletDao.selectDeletedById(3L)).thenReturn(Optional.of(wallet));

        trashService.purgeWallet(FAMILY, 3L);

        verify(purger).purgeWallet(wallet);
    }

    @Test
    void purgeWallet_thatIsNotInTheTrash_is404() {
        when(walletDao.selectDeletedById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> trashService.purgeWallet(FAMILY, 3L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void purgeCategory_isBlocked_byABudget() {
        when(categoryDao.selectDeletedById(4L)).thenReturn(Optional.of(trashedCategory(4L)));
        when(budgetDao.countByCategoryId(4L)).thenReturn(1L);

        assertThatThrownBy(() -> trashService.purgeCategory(FAMILY, 4L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ngân sách");
        verify(purger, never()).purgeCategory(any());
    }

    // --- sweeps

    @Test
    void emptyTrash_purgesTransactionsBeforeWalletsAndCategories_andSkipsWhatIsStillReferenced() {
        Transaction t = trashedTransaction(5L, CREATOR, true); // another member's private entry: included
        Wallet free = trashedWallet(3L);
        Wallet stillUsed = trashedWallet(6L);
        Category category = trashedCategory(4L);
        when(transactionDao.selectDeletedByFamilyId(FAMILY)).thenReturn(List.of(t));
        when(walletDao.selectDeletedByFamilyId(FAMILY)).thenReturn(List.of(free, stillUsed));
        when(categoryDao.selectDeletedByFamilyId(FAMILY)).thenReturn(List.of(category));
        when(recurringTransactionDao.countByWalletId(anyLong())).thenReturn(0L);
        when(recurringTransactionDao.countByWalletId(6L)).thenReturn(1L); // e.g. a rule still uses it

        var result = trashService.emptyTrash(FAMILY, 99L, "Chủ hộ");

        assertThat(result.transactions()).isEqualTo(1);
        assertThat(result.wallets()).isEqualTo(1);
        assertThat(result.categories()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        InOrder order = inOrder(purger);
        order.verify(purger).purgeTransaction(t, 99L, "Chủ hộ");
        order.verify(purger).purgeWallet(free);
        order.verify(purger).purgeCategory(category);
        verify(purger, never()).purgeWallet(stillUsed);
    }

    @Test
    void sweep_keepsGoing_whenOneRowFailsUnexpectedly() {
        Transaction broken = trashedTransaction(5L, CREATOR, false);
        Transaction fine = trashedTransaction(7L, CREATOR, false);
        when(transactionDao.selectDeletedByFamilyId(FAMILY)).thenReturn(List.of(broken, fine));
        when(walletDao.selectDeletedByFamilyId(FAMILY)).thenReturn(List.of());
        when(categoryDao.selectDeletedByFamilyId(FAMILY)).thenReturn(List.of());
        doThrow(new IllegalStateException("db down")).when(purger).purgeTransaction(broken, 99L, "Chủ hộ");

        var result = trashService.emptyTrash(FAMILY, 99L, "Chủ hộ");

        assertThat(result.transactions()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        verify(purger).purgeTransaction(fine, 99L, "Chủ hộ");
    }

    @Test
    void purgeDeletedBefore_runsAsTheSystem_acrossAllFamilies() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 9, 2, 3, 30);
        Transaction t = trashedTransaction(5L, CREATOR, false);
        when(transactionDao.selectDeletedBefore(cutoff)).thenReturn(List.of(t));
        when(walletDao.selectDeletedBefore(cutoff)).thenReturn(List.of());
        when(categoryDao.selectDeletedBefore(cutoff)).thenReturn(List.of());

        var result = trashService.purgeDeletedBefore(cutoff);

        assertThat(result.total()).isEqualTo(1);
        verify(purger).purgeTransaction(t, null, TrashService.RETENTION_ACTOR);
        verify(transactionDao, never()).selectDeletedByFamilyId(anyLong());
    }

    private static Transaction trashedTransaction(Long id, Long creator, boolean isPrivate) {
        Transaction t = new Transaction();
        t.setId(id);
        t.setFamilyId(FAMILY);
        t.setUserId(creator);
        t.setWalletId(3L);
        t.setCategoryId(4L);
        t.setType("EXPENSE");
        t.setAmount(new BigDecimal("50000"));
        t.setOccurredAt(LocalDateTime.of(2026, 9, 1, 10, 0));
        t.setIsPrivate(isPrivate);
        t.setDeletedAt(LocalDateTime.of(2026, 9, 5, 10, 0));
        return t;
    }

    private static Wallet trashedWallet(Long id) {
        Wallet w = new Wallet();
        w.setId(id);
        w.setFamilyId(FAMILY);
        w.setName("Ví " + id);
        w.setDeletedAt(LocalDateTime.of(2026, 9, 5, 10, 0));
        return w;
    }

    private static Category trashedCategory(Long id) {
        Category c = new Category();
        c.setId(id);
        c.setFamilyId(FAMILY);
        c.setName("Danh mục " + id);
        c.setDeletedAt(LocalDateTime.of(2026, 9, 5, 10, 0));
        return c;
    }
}
