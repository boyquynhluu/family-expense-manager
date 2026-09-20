package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.ConflictException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.expense.dao.RecurringTransactionDao;
import com.family.expensemanager.expense.dao.TransactionDao;
import com.family.expensemanager.expense.dao.WalletDao;
import com.family.expensemanager.expense.dao.WalletTransferDao;
import com.family.expensemanager.expense.domain.entity.Wallet;
import com.family.expensemanager.expense.dto.CreateWalletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletDao walletDao;
    @Mock
    private TransactionDao transactionDao;
    @Mock
    private RecurringTransactionDao recurringTransactionDao;
    @Mock
    private WalletTransferDao walletTransferDao;

    private WalletService walletService;

    @BeforeEach
    void setUp() {
        walletService = new WalletService(walletDao, transactionDao, recurringTransactionDao, walletTransferDao);
    }

    @Test
    void create_succeeds_whenFamilyHasNoWalletsYet() {
        when(walletDao.selectByFamilyId(1L)).thenReturn(List.of());

        var response = walletService.create(1L, new CreateWalletRequest("Ví chính", "VND", BigDecimal.TEN));

        assertThat(response.currency()).isEqualTo("VND");
        assertThat(response.currentBalance()).isEqualByComparingTo(BigDecimal.TEN);
        verify(walletDao).insert(any(Wallet.class));
    }

    @Test
    void create_throwsConflict_whenCurrencyDiffersFromExistingWallets() {
        Wallet existing = wallet(1L, 1L, "VND");
        when(walletDao.selectByFamilyId(1L)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> walletService.create(1L, new CreateWalletRequest("Ví phụ", "USD", BigDecimal.TEN)))
                .isInstanceOf(ConflictException.class);
        verify(walletDao, never()).insert(any());
    }

    @Test
    void create_succeeds_whenCurrencyMatchesExistingWallets() {
        Wallet existing = wallet(1L, 1L, "VND");
        when(walletDao.selectByFamilyId(1L)).thenReturn(List.of(existing));

        var response = walletService.create(1L, new CreateWalletRequest("Ví phụ", "VND", BigDecimal.ONE));

        assertThat(response.currency()).isEqualTo("VND");
    }

    @Test
    void update_throwsConflict_whenNewCurrencyDiffersFromOtherWallets() {
        Wallet target = wallet(1L, 1L, "VND");
        Wallet other = wallet(2L, 1L, "VND");
        when(walletDao.selectById(1L)).thenReturn(Optional.of(target));
        when(walletDao.selectByFamilyId(1L)).thenReturn(List.of(target, other));

        assertThatThrownBy(() -> walletService.update(1L, 1L, new CreateWalletRequest("Ví chính", "USD", BigDecimal.TEN)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void update_ignoresOwnCurrentCurrency_whenCheckingConsistency() {
        Wallet target = wallet(1L, 1L, "VND");
        when(walletDao.selectById(1L)).thenReturn(Optional.of(target));
        when(walletDao.selectByFamilyId(1L)).thenReturn(List.of(target));
        when(transactionDao.sumAmountByWalletAndType(eq(1L), any())).thenReturn(BigDecimal.ZERO);
        when(walletTransferDao.sumAmountIntoWallet(1L)).thenReturn(BigDecimal.ZERO);
        when(walletTransferDao.sumAmountFromWallet(1L)).thenReturn(BigDecimal.ZERO);

        var response = walletService.update(1L, 1L, new CreateWalletRequest("Ví chính (đổi tên)", "VND", BigDecimal.TEN));

        assertThat(response.name()).isEqualTo("Ví chính (đổi tên)");
    }

    @Test
    void update_throwsNotFound_whenWalletBelongsToAnotherFamily() {
        Wallet target = wallet(1L, 2L, "VND");
        when(walletDao.selectById(1L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> walletService.update(1L, 1L, new CreateWalletRequest("X", "VND", BigDecimal.TEN)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void delete_throwsConflict_whenWalletHasTransactions() {
        Wallet target = wallet(1L, 1L, "VND");
        when(walletDao.selectById(1L)).thenReturn(Optional.of(target));
        when(transactionDao.countByWalletId(1L)).thenReturn(3L);

        assertThatThrownBy(() -> walletService.delete(1L, 1L)).isInstanceOf(ConflictException.class);
        verify(walletDao, never()).update(any());
    }

    @Test
    void delete_throwsConflict_whenWalletHasRecurringTransactions() {
        Wallet target = wallet(1L, 1L, "VND");
        when(walletDao.selectById(1L)).thenReturn(Optional.of(target));
        when(transactionDao.countByWalletId(1L)).thenReturn(0L);
        when(recurringTransactionDao.countByWalletId(1L)).thenReturn(1L);

        assertThatThrownBy(() -> walletService.delete(1L, 1L)).isInstanceOf(ConflictException.class);
        verify(walletDao, never()).update(any());
    }

    @Test
    void delete_succeeds_whenWalletHasNoTransactions() {
        Wallet target = wallet(1L, 1L, "VND");
        when(walletDao.selectById(1L)).thenReturn(Optional.of(target));
        when(transactionDao.countByWalletId(1L)).thenReturn(0L);
        when(recurringTransactionDao.countByWalletId(1L)).thenReturn(0L);

        walletService.delete(1L, 1L);

        assertThat(target.getDeletedAt()).isNotNull();
        verify(walletDao).update(target);
    }

    @Test
    void restore_clearsDeletedAt_whenRowExists() {
        when(walletDao.restore(1L, 1L)).thenReturn(1);

        walletService.restore(1L, 1L);

        verify(walletDao).restore(1L, 1L);
    }

    @Test
    void restore_throwsNotFound_whenRowMissingOrNotDeleted() {
        when(walletDao.restore(1L, 1L)).thenReturn(0);

        assertThatThrownBy(() -> walletService.restore(1L, 1L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listDeletedPaged_returnsPageWithOffset() {
        when(walletDao.countDeletedByFamilyId(1L)).thenReturn(12L);
        when(walletDao.selectDeletedByFamilyIdPaged(1L, 5, 10))
                .thenReturn(List.of(wallet(11L, 1L, "VND"), wallet(12L, 1L, "VND")));

        var result = walletService.listDeletedPaged(1L, 2, 5);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).id()).isEqualTo(11L);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(5);
        assertThat(result.totalElements()).isEqualTo(12L);
        assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    void listDeletedPaged_rejectsNegativePage() {
        assertThatThrownBy(() -> walletService.listDeletedPaged(1L, -1, 5))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void listDeletedPaged_rejectsOutOfRangeSize() {
        assertThatThrownBy(() -> walletService.listDeletedPaged(1L, 0, 0))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> walletService.listDeletedPaged(1L, 0, 101))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void listByFamily_computesCurrentBalance_fromInitialBalancePlusIncomeMinusExpense() {
        Wallet target = wallet(1L, 1L, "VND");
        target.setInitialBalance(BigDecimal.valueOf(100));
        when(walletDao.selectByFamilyId(1L)).thenReturn(List.of(target));
        when(transactionDao.sumAmountByWalletAndType(1L, "INCOME")).thenReturn(BigDecimal.valueOf(50));
        when(transactionDao.sumAmountByWalletAndType(1L, "EXPENSE")).thenReturn(BigDecimal.valueOf(30));
        when(walletTransferDao.sumAmountIntoWallet(1L)).thenReturn(BigDecimal.ZERO);
        when(walletTransferDao.sumAmountFromWallet(1L)).thenReturn(BigDecimal.ZERO);

        var responses = walletService.listByFamily(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).currentBalance()).isEqualByComparingTo(BigDecimal.valueOf(120));
    }

    @Test
    void listByFamily_addsIncomingTransfers_andSubtractsOutgoingTransfers() {
        Wallet target = wallet(1L, 1L, "VND");
        target.setInitialBalance(BigDecimal.valueOf(100));
        when(walletDao.selectByFamilyId(1L)).thenReturn(List.of(target));
        when(transactionDao.sumAmountByWalletAndType(1L, "INCOME")).thenReturn(BigDecimal.valueOf(50));
        when(transactionDao.sumAmountByWalletAndType(1L, "EXPENSE")).thenReturn(BigDecimal.valueOf(30));
        when(walletTransferDao.sumAmountIntoWallet(1L)).thenReturn(BigDecimal.valueOf(200));
        when(walletTransferDao.sumAmountFromWallet(1L)).thenReturn(BigDecimal.valueOf(70));

        var responses = walletService.listByFamily(1L);

        assertThat(responses.get(0).currentBalance()).isEqualByComparingTo(BigDecimal.valueOf(250));
    }

    @Test
    void listByFamily_allowsNegativeBalance_whenOutgoingTransfersExceedFunds() {
        Wallet target = wallet(1L, 1L, "VND");
        when(walletDao.selectByFamilyId(1L)).thenReturn(List.of(target));
        when(transactionDao.sumAmountByWalletAndType(1L, "INCOME")).thenReturn(BigDecimal.ZERO);
        when(transactionDao.sumAmountByWalletAndType(1L, "EXPENSE")).thenReturn(BigDecimal.ZERO);
        when(walletTransferDao.sumAmountIntoWallet(1L)).thenReturn(BigDecimal.ZERO);
        when(walletTransferDao.sumAmountFromWallet(1L)).thenReturn(BigDecimal.valueOf(40));

        var responses = walletService.listByFamily(1L);

        assertThat(responses.get(0).currentBalance()).isEqualByComparingTo(BigDecimal.valueOf(-40));
    }

    @Test
    void delete_throwsConflict_whenWalletHasTransfers() {
        Wallet target = wallet(1L, 1L, "VND");
        when(walletDao.selectById(1L)).thenReturn(Optional.of(target));
        when(transactionDao.countByWalletId(1L)).thenReturn(0L);
        when(recurringTransactionDao.countByWalletId(1L)).thenReturn(0L);
        when(walletTransferDao.countByWalletId(1L)).thenReturn(2L);

        assertThatThrownBy(() -> walletService.delete(1L, 1L)).isInstanceOf(ConflictException.class);
        verify(walletDao, never()).update(any());
    }

    private static Wallet wallet(Long id, Long familyId, String currency) {
        Wallet wallet = new Wallet();
        wallet.setId(id);
        wallet.setFamilyId(familyId);
        wallet.setName("Wallet " + id);
        wallet.setCurrency(currency);
        wallet.setInitialBalance(BigDecimal.ZERO);
        return wallet;
    }

    @Test
    void mutatingMethods_requireOwnerRole() {
        for (String name : List.of("create", "update", "delete", "restore")) {
            var methods = Arrays.stream(WalletService.class.getDeclaredMethods())
                    .filter(m -> m.getName().equals(name)).toList();
            assertThat(methods).as(name).isNotEmpty();
            assertThat(methods).as(name).allSatisfy(m -> {
                PreAuthorize annotation = m.getAnnotation(PreAuthorize.class);
                assertThat(annotation).isNotNull();
                assertThat(annotation.value()).isEqualTo("hasRole('OWNER')");
            });
        }
    }
}
