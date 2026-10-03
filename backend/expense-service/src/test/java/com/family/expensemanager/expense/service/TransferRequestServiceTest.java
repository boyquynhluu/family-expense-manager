package com.family.expensemanager.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import com.family.expensemanager.common.event.ExpenseEvent;
import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.ConflictException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.expense.dao.TransferRequestDao;
import com.family.expensemanager.expense.dao.WalletDao;
import com.family.expensemanager.expense.domain.entity.TransferRequest;
import com.family.expensemanager.expense.domain.entity.Wallet;
import com.family.expensemanager.expense.dto.CreateTransferRequestRequest;
import com.family.expensemanager.expense.dto.CreateWalletTransferRequest;
import com.family.expensemanager.expense.dto.WalletTransferResponse;

/**
 * @author boyquynhluu
 */
@ExtendWith(MockitoExtension.class)
class TransferRequestServiceTest {

    private static final Long FAMILY = 7L;
    private static final Long HUSBAND = 10L; // owns wallet A (1)
    private static final Long WIFE = 20L;    // owns wallet B (2), asks for money

    @Mock private TransferRequestDao transferRequestDao;
    @Mock private WalletDao walletDao;
    @Mock private WalletService walletService;
    @Mock private WalletTransferService walletTransferService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private TransferRequestService service;

    @Test
    void create_storesAPendingRequest_forTheSourceWalletsOwner_andAnnouncesIt() {
        stubWallets();

        var response = service.create(FAMILY, WIFE, "Vợ", request("500000"));

        ArgumentCaptor<TransferRequest> saved = ArgumentCaptor.forClass(TransferRequest.class);
        verify(transferRequestDao).insert(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(TransferRequest.PENDING);
        assertThat(saved.getValue().getApproverUserId()).isEqualTo(HUSBAND);
        assertThat(saved.getValue().getRequesterName()).isEqualTo("Vợ");
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.canDecide()).isFalse();

        ExpenseEvent event = publishedEvent();
        assertThat(event.eventType()).isEqualTo(ExpenseEvent.TRANSFER_REQUESTED);
        assertThat(event.userId()).isEqualTo(WIFE);
        assertThat(event.targetUserId()).isEqualTo(HUSBAND);
        assertThat(event.fromWalletName()).isEqualTo("Ví Chồng");
        assertThat(event.toWalletName()).isEqualTo("Ví Vợ");
    }

    @Test
    void create_rejectsASharedSource_myOwnSource_andAnotherMembersDestination() {
        Wallet shared = wallet(1L, null, "Ví Chung");
        when(walletService.requireOwnedByFamily(1L, FAMILY)).thenReturn(shared);
        assertThatThrownBy(() -> service.create(FAMILY, WIFE, "Vợ", request("500000")))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("ví chung");

        when(walletService.requireOwnedByFamily(1L, FAMILY)).thenReturn(wallet(1L, WIFE, "Ví Vợ 2"));
        assertThatThrownBy(() -> service.create(FAMILY, WIFE, "Vợ", request("500000")))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("ví của bạn");

        when(walletService.requireOwnedByFamily(1L, FAMILY)).thenReturn(wallet(1L, HUSBAND, "Ví Chồng"));
        when(walletService.requireOwnedByFamily(2L, FAMILY)).thenReturn(wallet(2L, 30L, "Ví Con"));
        assertThatThrownBy(() -> service.create(FAMILY, WIFE, "Vợ", request("500000")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(transferRequestDao, never()).insert(any());
    }

    @Test
    void create_rejectsAmountsOutsideTheRange_andTooManyPendingRequests() {
        assertThatThrownBy(() -> service.create(FAMILY, WIFE, "Vợ", request("5000")))
                .isInstanceOf(BadRequestException.class).hasMessage("Số tiền giao dịch tối thiểu là 10.000đ");
        assertThatThrownBy(() -> service.create(FAMILY, WIFE, "Vợ", request("6000000")))
                .isInstanceOf(BadRequestException.class).hasMessage("Số tiền giao dịch tối đa là 5.000.000đ");

        stubWallets();
        when(transferRequestDao.countPendingByRequester(FAMILY, WIFE))
                .thenReturn((long) TransferRequestService.MAX_PENDING_PER_REQUESTER);
        assertThatThrownBy(() -> service.create(FAMILY, WIFE, "Vợ", request("500000")))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("đang chờ");
        verify(transferRequestDao, never()).insert(any());
    }

    @Test
    void approve_byTheWalletOwner_makesTheTransferAsThem_andMarksTheRequestCompleted() {
        when(transferRequestDao.selectById(5L)).thenReturn(Optional.of(pending()));
        stubWallets();
        when(walletTransferService.create(eq(FAMILY), eq(HUSBAND), eq("chong@b.com"), eq("Chồng"), eq("OWNER"),
                any(CreateWalletTransferRequest.class), isNull()))
                .thenReturn(new WalletTransferResponse(99L, FAMILY, 1L, 2L, new BigDecimal("500000"), null,
                        LocalDateTime.now(), HUSBAND, LocalDateTime.now()));
        when(transferRequestDao.decide(eq(5L), eq(TransferRequest.COMPLETED), eq(HUSBAND), eq("Chồng"), any(), eq(99L)))
                .thenReturn(1);

        var response = service.approve(FAMILY, 5L, HUSBAND, "chong@b.com", "Chồng", "OWNER");

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.transferId()).isEqualTo(99L);
        ArgumentCaptor<CreateWalletTransferRequest> transfer = ArgumentCaptor.forClass(CreateWalletTransferRequest.class);
        verify(walletTransferService).create(eq(FAMILY), eq(HUSBAND), any(), any(), any(), transfer.capture(), isNull());
        assertThat(transfer.getValue().fromWalletId()).isEqualTo(1L);
        assertThat(transfer.getValue().toWalletId()).isEqualTo(2L);
        assertThat(transfer.getValue().amount()).isEqualByComparingTo("500000");
        assertThat(transfer.getValue().note()).startsWith("Theo yêu cầu của Vợ");

        ExpenseEvent event = publishedEvent();
        assertThat(event.eventType()).isEqualTo(ExpenseEvent.TRANSFER_REQUEST_APPROVED);
        assertThat(event.targetUserId()).isEqualTo(WIFE);
    }

    @Test
    void approve_thatWasDecidedMeanwhile_isAConflict_soTheTransferRollsBack() {
        when(transferRequestDao.selectById(5L)).thenReturn(Optional.of(pending()));
        stubWallets();
        when(walletTransferService.create(anyLong(), anyLong(), any(), any(), any(), any(), any()))
                .thenReturn(new WalletTransferResponse(99L, FAMILY, 1L, 2L, new BigDecimal("500000"), null,
                        LocalDateTime.now(), HUSBAND, LocalDateTime.now()));
        when(transferRequestDao.decide(anyLong(), any(), any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.approve(FAMILY, 5L, HUSBAND, null, "Chồng", "OWNER"))
                .isInstanceOf(ConflictException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void onlyTheWalletOwner_mayDecide_theRequesterGets403_othersGet404() {
        when(transferRequestDao.selectById(5L)).thenReturn(Optional.of(pending()));

        assertThatThrownBy(() -> service.reject(FAMILY, 5L, WIFE, "Vợ"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.approve(FAMILY, 5L, 30L, null, "Con", "MEMBER"))
                .isInstanceOf(NotFoundException.class);
        verify(walletTransferService, never()).create(anyLong(), anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void decidingAnAlreadyDecidedRequest_isAConflict() {
        TransferRequest done = pending();
        done.setStatus(TransferRequest.REJECTED);
        when(transferRequestDao.selectById(5L)).thenReturn(Optional.of(done));

        assertThatThrownBy(() -> service.approve(FAMILY, 5L, HUSBAND, null, "Chồng", "OWNER"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void reject_marksItRejected_withoutMovingMoney_andTellsTheRequester() {
        when(transferRequestDao.selectById(5L)).thenReturn(Optional.of(pending()));
        when(transferRequestDao.decide(eq(5L), eq(TransferRequest.REJECTED), eq(HUSBAND), eq("Chồng"), any(), isNull()))
                .thenReturn(1);
        when(walletDao.selectByFamilyId(FAMILY)).thenReturn(List.of(wallet(1L, HUSBAND, "Ví Chồng"),
                wallet(2L, WIFE, "Ví Vợ")));

        var response = service.reject(FAMILY, 5L, HUSBAND, "Chồng");

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.decidedByName()).isEqualTo("Chồng");
        verify(walletTransferService, never()).create(anyLong(), anyLong(), any(), any(), any(), any(), any());
        ExpenseEvent event = publishedEvent();
        assertThat(event.eventType()).isEqualTo(ExpenseEvent.TRANSFER_REQUEST_REJECTED);
        assertThat(event.targetUserId()).isEqualTo(WIFE);
    }

    private void stubWallets() {
        when(walletService.requireOwnedByFamily(1L, FAMILY)).thenReturn(wallet(1L, HUSBAND, "Ví Chồng"));
        when(walletService.requireOwnedByFamily(2L, FAMILY)).thenReturn(wallet(2L, WIFE, "Ví Vợ"));
    }

    private ExpenseEvent publishedEvent() {
        ArgumentCaptor<ExpenseEvent> captor = ArgumentCaptor.forClass(ExpenseEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    private static CreateTransferRequestRequest request(String amount) {
        return new CreateTransferRequestRequest(1L, 2L, new BigDecimal(amount), "tiền chợ");
    }

    private static TransferRequest pending() {
        TransferRequest r = new TransferRequest();
        r.setId(5L);
        r.setFamilyId(FAMILY);
        r.setRequesterUserId(WIFE);
        r.setRequesterName("Vợ");
        r.setApproverUserId(HUSBAND);
        r.setFromWalletId(1L);
        r.setToWalletId(2L);
        r.setAmount(new BigDecimal("500000"));
        r.setNote("tiền chợ");
        r.setStatus(TransferRequest.PENDING);
        r.setCreatedAt(LocalDateTime.now());
        return r;
    }

    private static Wallet wallet(Long id, Long owner, String name) {
        Wallet w = new Wallet();
        w.setId(id);
        w.setFamilyId(FAMILY);
        w.setOwnerUserId(owner);
        w.setName(name);
        w.setCurrency("VND");
        w.setInitialBalance(BigDecimal.ZERO);
        return w;
    }
}
