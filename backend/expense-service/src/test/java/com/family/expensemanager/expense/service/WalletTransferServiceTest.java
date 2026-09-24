package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.expense.dao.WalletTransferDao;
import com.family.expensemanager.expense.domain.entity.Wallet;
import com.family.expensemanager.expense.domain.entity.WalletTransfer;
import com.family.expensemanager.expense.dto.CreateWalletTransferRequest;
import com.family.expensemanager.common.event.ExpenseEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletTransferServiceTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 9, 20, 10, 30);

    @Mock
    private WalletTransferDao walletTransferDao;
    @Mock
    private WalletService walletService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private WalletTransferService service;

    // requireValidWallets() checks the transfer amount against the SOURCE wallet's current balance
    // (via WalletService.currentBalanceOf); default every test to a wallet that's "rich enough" so
    // that check doesn't trip up tests that aren't specifically exercising it.
    @BeforeEach
    void setUpDefaultBalance() {
        lenient().when(walletService.currentBalanceOf(any())).thenReturn(new BigDecimal("999999999"));
    }

    @Test
    void create_insertsTransfer_withAllColumnsSet() {
        when(walletService.requireOwnedByFamily(1L, 7L)).thenReturn(wallet(1L, 7L, "VND"));
        when(walletService.requireOwnedByFamily(2L, 7L)).thenReturn(wallet(2L, 7L, "VND"));

        var response = service.create(7L, 42L, "a@b.com", "An", request(1L, 2L, "150000"));

        ArgumentCaptor<WalletTransfer> captor = ArgumentCaptor.forClass(WalletTransfer.class);
        verify(walletTransferDao).insert(captor.capture());
        WalletTransfer saved = captor.getValue();
        assertThat(saved.getFamilyId()).isEqualTo(7L);
        assertThat(saved.getFromWalletId()).isEqualTo(1L);
        assertThat(saved.getToWalletId()).isEqualTo(2L);
        assertThat(saved.getAmount()).isEqualByComparingTo("150000");
        assertThat(saved.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(saved.getCreatedByUserId()).isEqualTo(42L);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(response.fromWalletId()).isEqualTo(1L);

        ArgumentCaptor<ExpenseEvent> eventCaptor = ArgumentCaptor.forClass(ExpenseEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ExpenseEvent event = eventCaptor.getValue();
        assertThat(event.eventType()).isEqualTo(ExpenseEvent.WALLET_TRANSFERRED);
        assertThat(event.familyId()).isEqualTo(7L);
        assertThat(event.userId()).isEqualTo(42L);
        assertThat(event.userEmail()).isEqualTo("a@b.com");
        assertThat(event.userDisplayName()).isEqualTo("An");
        assertThat(event.amount()).isEqualByComparingTo("150000");
        assertThat(event.fromWalletName()).isEqualTo("Wallet 1");
        assertThat(event.toWalletName()).isEqualTo("Wallet 2");
        assertThat(event.occurredOn()).isEqualTo(OCCURRED_AT.toLocalDate());
        assertThat(event.note()).isEqualTo("note");
        assertThat(event.transactionId()).isNull();
        assertThat(event.categoryId()).isNull();
    }

    @Test
    void create_doesNotPublishEvent_whenValidationFailsBeforeInsert() {
        assertThatThrownBy(() -> service.create(7L, 42L, "a@b.com", "An", request(1L, 1L, "10")))
                .isInstanceOf(BadRequestException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void create_throwsBadRequest_whenSourceAndTargetAreSameWallet() {
        assertThatThrownBy(() -> service.create(7L, 42L, "a@b.com", "An", request(1L, 1L, "10")))
                .isInstanceOf(BadRequestException.class);
        verify(walletTransferDao, never()).insert(any());
    }

    @Test
    void create_throwsBadRequest_whenAmountBelowMinimum() {
        assertThatThrownBy(() -> service.create(7L, 42L, "a@b.com", "An", request(1L, 2L, "0.001")))
                .isInstanceOf(BadRequestException.class);
        verify(walletTransferDao, never()).insert(any());
    }

    @Test
    void create_throwsNotFound_whenWalletBelongsToAnotherFamilyOrIsDeleted() {
        when(walletService.requireOwnedByFamily(1L, 7L)).thenReturn(wallet(1L, 7L, "VND"));
        when(walletService.requireOwnedByFamily(2L, 7L)).thenThrow(new NotFoundException("Wallet không tồn tại: 2"));

        assertThatThrownBy(() -> service.create(7L, 42L, "a@b.com", "An", request(1L, 2L, "10")))
                .isInstanceOf(NotFoundException.class);
        verify(walletTransferDao, never()).insert(any());
    }

    @Test
    void create_throwsBadRequest_whenCurrenciesDiffer() {
        when(walletService.requireOwnedByFamily(1L, 7L)).thenReturn(wallet(1L, 7L, "VND"));
        when(walletService.requireOwnedByFamily(2L, 7L)).thenReturn(wallet(2L, 7L, "USD"));

        assertThatThrownBy(() -> service.create(7L, 42L, "a@b.com", "An", request(1L, 2L, "10")))
                .isInstanceOf(BadRequestException.class);
        verify(walletTransferDao, never()).insert(any());
    }

    @Test
    void create_throwsBadRequest_whenAmountExceedsSourceWalletBalance() {
        Wallet from = wallet(1L, 7L, "VND");
        when(walletService.requireOwnedByFamily(1L, 7L)).thenReturn(from);
        when(walletService.requireOwnedByFamily(2L, 7L)).thenReturn(wallet(2L, 7L, "VND"));
        when(walletService.currentBalanceOf(from)).thenReturn(new BigDecimal("100"));

        assertThatThrownBy(() -> service.create(7L, 42L, "a@b.com", "An", request(1L, 2L, "150")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ví nguồn");
        verify(walletTransferDao, never()).insert(any());
    }

    // Regression test for a bug where the balance check was wrongly run against the DESTINATION
    // wallet instead of the source: this transfer must succeed even though the destination wallet
    // has zero balance, because it's the source wallet that has enough money to send.
    @Test
    void create_succeeds_whenDestinationWalletHasLowerBalanceThanSource() {
        Wallet from = wallet(1L, 7L, "VND");
        Wallet to = wallet(2L, 7L, "VND");
        when(walletService.requireOwnedByFamily(1L, 7L)).thenReturn(from);
        when(walletService.requireOwnedByFamily(2L, 7L)).thenReturn(to);
        when(walletService.currentBalanceOf(from)).thenReturn(new BigDecimal("1000000"));

        service.create(7L, 42L, "a@b.com", "An", request(1L, 2L, "150000"));

        verify(walletTransferDao).insert(any());
    }

    @Test
    void listByFamilyPaged_returnsPageWithOffset() {
        when(walletTransferDao.countByFamilyId(7L)).thenReturn(12L);
        when(walletTransferDao.selectByFamilyIdPaged(7L, 5, 10))
                .thenReturn(List.of(transfer(11L, 7L, 42L), transfer(12L, 7L, 42L)));

        var result = service.listByFamilyPaged(7L, 2, 5);

        assertThat(result.content()).hasSize(2);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(12L);
        assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    void listByFamilyPaged_rejectsInvalidPageOrSize() {
        assertThatThrownBy(() -> service.listByFamilyPaged(7L, -1, 5)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.listByFamilyPaged(7L, 0, 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.listByFamilyPaged(7L, 0, 101)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void delete_succeeds_forCreator() {
        WalletTransfer transfer = transfer(5L, 7L, 42L);
        when(walletTransferDao.selectById(5L)).thenReturn(Optional.of(transfer));

        service.delete(7L, 42L, "MEMBER", 5L);

        verify(walletTransferDao).delete(transfer);
    }

    @Test
    void delete_succeeds_forOwnerWhoIsNotTheCreator() {
        WalletTransfer transfer = transfer(5L, 7L, 42L);
        when(walletTransferDao.selectById(5L)).thenReturn(Optional.of(transfer));

        service.delete(7L, 99L, "OWNER", 5L);

        verify(walletTransferDao).delete(transfer);
    }

    @Test
    void delete_throwsAccessDenied_forOtherMember() {
        when(walletTransferDao.selectById(5L)).thenReturn(Optional.of(transfer(5L, 7L, 42L)));

        assertThatThrownBy(() -> service.delete(7L, 99L, "MEMBER", 5L)).isInstanceOf(AccessDeniedException.class);
        verify(walletTransferDao, never()).delete(any());
    }

    @Test
    void delete_throwsNotFound_whenTransferBelongsToAnotherFamily() {
        when(walletTransferDao.selectById(5L)).thenReturn(Optional.of(transfer(5L, 8L, 42L)));

        assertThatThrownBy(() -> service.delete(7L, 42L, "OWNER", 5L)).isInstanceOf(NotFoundException.class);
        verify(walletTransferDao, never()).delete(any());
    }

    private static CreateWalletTransferRequest request(Long from, Long to, String amount) {
        return new CreateWalletTransferRequest(from, to, new BigDecimal(amount), OCCURRED_AT, "note");
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

    private static WalletTransfer transfer(Long id, Long familyId, Long createdBy) {
        WalletTransfer transfer = new WalletTransfer();
        transfer.setId(id);
        transfer.setFamilyId(familyId);
        transfer.setFromWalletId(1L);
        transfer.setToWalletId(2L);
        transfer.setAmount(BigDecimal.TEN);
        transfer.setOccurredAt(OCCURRED_AT);
        transfer.setCreatedByUserId(createdBy);
        transfer.setCreatedAt(OCCURRED_AT);
        return transfer;
    }

    @Test
    void update_savesAllColumns_forCreator() {
        WalletTransfer existing = transfer(5L, 7L, 42L);
        when(walletTransferDao.selectById(5L)).thenReturn(Optional.of(existing));
        when(walletService.requireOwnedByFamily(3L, 7L)).thenReturn(wallet(3L, 7L, "VND"));
        when(walletService.requireOwnedByFamily(4L, 7L)).thenReturn(wallet(4L, 7L, "VND"));

        var response = service.update(7L, 42L, "MEMBER", 5L, request(3L, 4L, "250000"));

        verify(walletTransferDao).update(existing);
        assertThat(existing.getFromWalletId()).isEqualTo(3L);
        assertThat(existing.getToWalletId()).isEqualTo(4L);
        assertThat(existing.getAmount()).isEqualByComparingTo("250000");
        assertThat(existing.getNote()).isEqualTo("note");
        assertThat(existing.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(existing.getFamilyId()).isEqualTo(7L);
        assertThat(existing.getCreatedByUserId()).isEqualTo(42L);
        assertThat(existing.getCreatedAt()).isNotNull();
        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.toWalletId()).isEqualTo(4L);
    }

    // The edited transfer's own 10 is already deducted from the source wallet's balance (0 left), so editing
    // it must not be judged against that already-reduced balance.
    @Test
    void update_allowsSameAmount_whenSourceBalanceOnlyCoversTheTransferBeingEdited() {
        WalletTransfer existing = transfer(5L, 7L, 42L);
        Wallet from = wallet(1L, 7L, "VND");
        when(walletTransferDao.selectById(5L)).thenReturn(Optional.of(existing));
        when(walletService.requireOwnedByFamily(1L, 7L)).thenReturn(from);
        when(walletService.requireOwnedByFamily(2L, 7L)).thenReturn(wallet(2L, 7L, "VND"));
        when(walletService.currentBalanceOf(from)).thenReturn(BigDecimal.ZERO);

        service.update(7L, 42L, "MEMBER", 5L, request(1L, 2L, "10"));

        verify(walletTransferDao).update(existing);
    }

    @Test
    void update_rejectsAmountAboveSourceBalancePlusTheTransferBeingEdited() {
        WalletTransfer existing = transfer(5L, 7L, 42L);
        Wallet from = wallet(1L, 7L, "VND");
        when(walletTransferDao.selectById(5L)).thenReturn(Optional.of(existing));
        when(walletService.requireOwnedByFamily(1L, 7L)).thenReturn(from);
        when(walletService.requireOwnedByFamily(2L, 7L)).thenReturn(wallet(2L, 7L, "VND"));
        when(walletService.currentBalanceOf(from)).thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> service.update(7L, 42L, "MEMBER", 5L, request(1L, 2L, "11")))
                .isInstanceOf(BadRequestException.class);
        verify(walletTransferDao, never()).update(any());
    }

    // Edited transfer 1 -> 2 (10) becomes 2 -> 3: wallet 2 currently holds that incoming 10, which disappears
    // with the edit, so only the balance without it (0) counts.
    @Test
    void update_excludesTheEditedTransfersIncomingAmount_whenItsDestinationBecomesTheNewSource() {
        WalletTransfer existing = transfer(5L, 7L, 42L);
        Wallet newSource = wallet(2L, 7L, "VND");
        when(walletTransferDao.selectById(5L)).thenReturn(Optional.of(existing));
        when(walletService.requireOwnedByFamily(2L, 7L)).thenReturn(newSource);
        when(walletService.requireOwnedByFamily(3L, 7L)).thenReturn(wallet(3L, 7L, "VND"));
        when(walletService.currentBalanceOf(newSource)).thenReturn(new BigDecimal("10"));

        assertThatThrownBy(() -> service.update(7L, 42L, "MEMBER", 5L, request(2L, 3L, "5")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void update_succeeds_forOwnerWhoIsNotTheCreator() {
        WalletTransfer existing = transfer(5L, 7L, 42L);
        when(walletTransferDao.selectById(5L)).thenReturn(Optional.of(existing));
        when(walletService.requireOwnedByFamily(1L, 7L)).thenReturn(wallet(1L, 7L, "VND"));
        when(walletService.requireOwnedByFamily(2L, 7L)).thenReturn(wallet(2L, 7L, "VND"));

        service.update(7L, 99L, "OWNER", 5L, request(1L, 2L, "10"));

        verify(walletTransferDao).update(existing);
    }

    @Test
    void update_throwsAccessDenied_forOtherMember() {
        when(walletTransferDao.selectById(5L)).thenReturn(Optional.of(transfer(5L, 7L, 42L)));

        assertThatThrownBy(() -> service.update(7L, 99L, "MEMBER", 5L, request(1L, 2L, "10")))
                .isInstanceOf(AccessDeniedException.class);
        verify(walletTransferDao, never()).update(any());
    }

    @Test
    void update_throwsNotFound_whenTransferMissingOrInAnotherFamily() {
        when(walletTransferDao.selectById(5L)).thenReturn(Optional.of(transfer(5L, 8L, 42L)));
        when(walletTransferDao.selectById(6L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(7L, 42L, "OWNER", 5L, request(1L, 2L, "10")))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.update(7L, 42L, "OWNER", 6L, request(1L, 2L, "10")))
                .isInstanceOf(NotFoundException.class);
        verify(walletTransferDao, never()).update(any());
    }

    @Test
    void update_throwsBadRequest_whenSameWalletAmountTooSmallOrCurrenciesDiffer() {
        when(walletTransferDao.selectById(5L)).thenReturn(Optional.of(transfer(5L, 7L, 42L)));
        when(walletService.requireOwnedByFamily(1L, 7L)).thenReturn(wallet(1L, 7L, "VND"));
        when(walletService.requireOwnedByFamily(2L, 7L)).thenReturn(wallet(2L, 7L, "USD"));

        assertThatThrownBy(() -> service.update(7L, 42L, "OWNER", 5L, request(1L, 1L, "10")))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.update(7L, 42L, "OWNER", 5L, request(1L, 2L, "0.001")))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.update(7L, 42L, "OWNER", 5L, request(1L, 2L, "10")))
                .isInstanceOf(BadRequestException.class);
        verify(walletTransferDao, never()).update(any());
    }
}
