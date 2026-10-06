package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.event.ExpenseEvent;
import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.ConflictException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.expense.dao.WalletAdjustmentDao;
import com.family.expensemanager.expense.domain.entity.Wallet;
import com.family.expensemanager.expense.domain.entity.WalletAdjustment;
import com.family.expensemanager.expense.dto.WalletAdjustmentRequest;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletAdjustmentServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-05T03:00:00Z"), ZoneId.of("UTC"));
    private static final Long FAMILY_ID = 1L;
    private static final Long MEMBER_ID = 7L;
    private static final Long OTHER_MEMBER_ID = 8L;

    @Mock
    private WalletAdjustmentDao walletAdjustmentDao;
    @Mock
    private WalletService walletService;
    @Mock
    private PeriodLockService periodLockService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private WalletAdjustmentService service;

    @BeforeEach
    void setUp() {
        service = new WalletAdjustmentService(walletAdjustmentDao, walletService, periodLockService, eventPublisher, CLOCK);
    }

    @Test
    void create_storesTheDifferenceBetweenTheRealAndTheAppBalance_afterLockingTheWallet() {
        Wallet wallet = wallet(MEMBER_ID);
        when(walletService.requireOwnedByFamily(5L, FAMILY_ID)).thenReturn(wallet);
        when(walletService.currentBalanceOf(wallet)).thenReturn(new BigDecimal("1200000"));

        var response = service.create(FAMILY_ID, MEMBER_ID, "An", false,
                new WalletAdjustmentRequest(5L, new BigDecimal("1150000"), "Quên ghi tiền gửi xe"));

        ArgumentCaptor<WalletAdjustment> saved = ArgumentCaptor.forClass(WalletAdjustment.class);
        verify(walletAdjustmentDao).insert(saved.capture());
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("-50000");
        assertThat(saved.getValue().getBalanceBefore()).isEqualByComparingTo("1200000");
        assertThat(saved.getValue().getBalanceAfter()).isEqualByComparingTo("1150000");
        assertThat(saved.getValue().getCreatedByName()).isEqualTo("An");
        assertThat(saved.getValue().getOccurredAt()).isEqualTo(LocalDateTime.of(2026, 10, 5, 3, 0));
        assertThat(response.amount()).isEqualByComparingTo("-50000");
        // The balance is read only once the wallet row is locked (no concurrent transfer in between).
        InOrder order = inOrder(walletService);
        order.verify(walletService).lockForUpdate(5L);
        order.verify(walletService).currentBalanceOf(wallet);
        // The family is told what changed (emailed to everyone but An — see NotificationType.WALLET_ADJUSTED).
        ArgumentCaptor<ExpenseEvent> event = ArgumentCaptor.forClass(ExpenseEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo(ExpenseEvent.WALLET_ADJUSTED);
        assertThat(event.getValue().userId()).isEqualTo(MEMBER_ID);
        assertThat(event.getValue().message()).startsWith("An đã điều chỉnh số dư ví ")
                .contains(" → ").endsWith("Ghi chú: Quên ghi tiền gửi xe.");
    }

    @Test
    void create_refuses_whenTheBalanceAlreadyMatches() {
        Wallet wallet = wallet(MEMBER_ID);
        when(walletService.requireOwnedByFamily(5L, FAMILY_ID)).thenReturn(wallet);
        when(walletService.currentBalanceOf(wallet)).thenReturn(new BigDecimal("1000.00"));

        assertThatThrownBy(() -> service.create(FAMILY_ID, MEMBER_ID, "An", false,
                new WalletAdjustmentRequest(5L, new BigDecimal("1000"), null)))
                .isInstanceOf(BadRequestException.class);
        verify(walletAdjustmentDao, never()).insert(any());
    }

    @Test
    void create_letsTheOwnerAdjustAnyWallet_includingAShared_one() {
        Wallet shared = wallet(null);
        when(walletService.requireOwnedByFamily(5L, FAMILY_ID)).thenReturn(shared);
        when(walletService.currentBalanceOf(shared)).thenReturn(BigDecimal.ZERO);

        service.create(FAMILY_ID, OTHER_MEMBER_ID, "Chủ hộ", true, new WalletAdjustmentRequest(5L, BigDecimal.TEN, null));

        verify(walletAdjustmentDao).insert(any());
    }

    @Test
    void create_refusesAMember_onASharedWallet_andOnAnotherMembersWallet() {
        when(walletService.requireOwnedByFamily(5L, FAMILY_ID)).thenReturn(wallet(null));
        assertForbidden(() -> service.create(FAMILY_ID, MEMBER_ID, "An", false,
                new WalletAdjustmentRequest(5L, BigDecimal.TEN, null)));

        when(walletService.requireOwnedByFamily(5L, FAMILY_ID)).thenReturn(wallet(OTHER_MEMBER_ID));
        assertForbidden(() -> service.create(FAMILY_ID, MEMBER_ID, "An", false,
                new WalletAdjustmentRequest(5L, BigDecimal.TEN, null)));

        verify(walletAdjustmentDao, never()).insert(any());
    }

    @Test
    void delete_removesTheAdjustment_forItsCreator() {
        WalletAdjustment adjustment = adjustment(MEMBER_ID);
        when(walletAdjustmentDao.selectById(3L)).thenReturn(Optional.of(adjustment));

        service.delete(FAMILY_ID, 3L, MEMBER_ID, false);

        verify(walletAdjustmentDao).delete(adjustment);
    }

    @Test
    void delete_refusesAnotherMember_butNotTheOwner() {
        WalletAdjustment adjustment = adjustment(OTHER_MEMBER_ID);
        when(walletAdjustmentDao.selectById(3L)).thenReturn(Optional.of(adjustment));

        assertForbidden(() -> service.delete(FAMILY_ID, 3L, MEMBER_ID, false));
        verify(walletAdjustmentDao, never()).delete(any());

        service.delete(FAMILY_ID, 3L, MEMBER_ID, true);
        verify(walletAdjustmentDao).delete(adjustment);
    }

    @Test
    void delete_isRefused_whenTheAdjustmentsMonthIsClosed() {
        WalletAdjustment adjustment = adjustment(MEMBER_ID);
        when(walletAdjustmentDao.selectById(3L)).thenReturn(Optional.of(adjustment));
        doThrow(new ConflictException("Tháng 2026-08 đã chốt sổ"))
                .when(periodLockService).requireUnlocked(FAMILY_ID, adjustment.getOccurredAt());

        assertThatThrownBy(() -> service.delete(FAMILY_ID, 3L, MEMBER_ID, false)).isInstanceOf(ConflictException.class);
        verify(walletAdjustmentDao, never()).delete(any());
    }

    @Test
    void delete_answers404_forAnotherFamilysAdjustment() {
        WalletAdjustment adjustment = adjustment(MEMBER_ID);
        adjustment.setFamilyId(2L);
        when(walletAdjustmentDao.selectById(3L)).thenReturn(Optional.of(adjustment));

        assertThatThrownBy(() -> service.delete(FAMILY_ID, 3L, MEMBER_ID, true)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listPaged_validatesPaging() {
        assertThatThrownBy(() -> service.listPaged(FAMILY_ID, null, -1, 5)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.listPaged(FAMILY_ID, null, 0, 101)).isInstanceOf(BadRequestException.class);
    }

    private static void assertForbidden(ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private static Wallet wallet(Long ownerUserId) {
        Wallet wallet = new Wallet();
        wallet.setId(5L);
        wallet.setFamilyId(FAMILY_ID);
        wallet.setName("Ví");
        wallet.setCurrency("VND");
        wallet.setInitialBalance(BigDecimal.ZERO);
        wallet.setOwnerUserId(ownerUserId);
        return wallet;
    }

    private static WalletAdjustment adjustment(Long createdBy) {
        WalletAdjustment adjustment = new WalletAdjustment();
        adjustment.setId(3L);
        adjustment.setFamilyId(FAMILY_ID);
        adjustment.setWalletId(5L);
        adjustment.setAmount(new BigDecimal("-50000"));
        adjustment.setOccurredAt(LocalDateTime.of(2026, 8, 31, 20, 0));
        adjustment.setCreatedByUserId(createdBy);
        return adjustment;
    }
}
