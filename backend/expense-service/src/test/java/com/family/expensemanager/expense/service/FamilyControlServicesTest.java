package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.event.ExpenseEvent;
import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.ConflictException;
import com.family.expensemanager.expense.config.ViewerReadOnlyInterceptorAccess;
import com.family.expensemanager.expense.dao.FamilySettingDao;
import com.family.expensemanager.expense.dao.MemberSpendingLimitDao;
import com.family.expensemanager.expense.dao.TransactionApprovalDao;
import com.family.expensemanager.expense.dao.TransactionDao;
import com.family.expensemanager.expense.domain.entity.FamilySetting;
import com.family.expensemanager.expense.domain.entity.MemberSpendingLimit;
import com.family.expensemanager.expense.domain.entity.TransactionApproval;
import com.family.expensemanager.expense.domain.entity.Wallet;
import com.family.expensemanager.expense.dto.TransactionRequest;
import com.family.expensemanager.expense.dto.TransactionResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * README A5: spending caps, the approval threshold / approval flow and the VIEWER read-only guard.
 */
class FamilyControlServicesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-05T03:00:00Z"), ZoneId.of("UTC"));
    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 5, 9, 0);

    @Nested
    @ExtendWith(MockitoExtension.class)
    class SpendingLimits {

        @Mock
        private MemberSpendingLimitDao limitDao;
        @Mock
        private TransactionDao transactionDao;

        private SpendingLimitService service() {
            return new SpendingLimitService(limitDao, transactionDao, CLOCK);
        }

        @Test
        void passes_whenTheMemberHasNoLimits() {
            when(limitDao.selectByFamilyAndUser(1L, 7L)).thenReturn(Optional.empty());

            assertThatCode(() -> service().requireWithinLimits(1L, 7L, new BigDecimal("999999"), AT, null))
                    .doesNotThrowAnyException();
        }

        @Test
        void refuses_whenTheDayWouldPassTheDailyCap_andSaysWhatIsLeft() {
            when(limitDao.selectByFamilyAndUser(1L, 7L)).thenReturn(Optional.of(limit("100000", null)));
            when(transactionDao.sumUserExpenseBetween(1L, 7L, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6), null))
                    .thenReturn(new BigDecimal("70000"));

            assertThatThrownBy(() -> service().requireWithinLimits(1L, 7L, new BigDecimal("40000"), AT, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("30.000");
        }

        @Test
        void editLeavesItsOwnOldAmountOut_andChecksTheMonth() {
            when(limitDao.selectByFamilyAndUser(1L, 7L)).thenReturn(Optional.of(limit(null, "500000")));
            when(transactionDao.sumUserExpenseBetween(1L, 7L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1), 9L))
                    .thenReturn(new BigDecimal("450000"));

            assertThatCode(() -> service().requireWithinLimits(1L, 7L, new BigDecimal("50000"), AT, 9L))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> service().requireWithinLimits(1L, 7L, new BigDecimal("50001"), AT, 9L))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void set_withBothLimitsEmpty_removesTheRow() {
            MemberSpendingLimit existing = limit("1", "2");
            when(limitDao.selectByFamilyAndUser(1L, 7L)).thenReturn(Optional.of(existing));

            service().set(1L, 7L, new com.family.expensemanager.expense.dto.SpendingLimitRequest(null, null));

            verify(limitDao).delete(existing);
        }

        @Test
        void set_refusesADailyCapAboveTheMonthlyOne() {
            assertThatThrownBy(() -> service().set(1L, 7L, new com.family.expensemanager.expense.dto.SpendingLimitRequest(
                    new BigDecimal("200"), new BigDecimal("100")))).isInstanceOf(BadRequestException.class);
        }

        private MemberSpendingLimit limit(String daily, String monthly) {
            MemberSpendingLimit l = new MemberSpendingLimit();
            l.setFamilyId(1L);
            l.setUserId(7L);
            l.setDailyLimit(daily == null ? null : new BigDecimal(daily));
            l.setMonthlyLimit(monthly == null ? null : new BigDecimal(monthly));
            return l;
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Approvals {

        @Mock
        private TransactionApprovalDao approvalDao;
        @Mock
        private FamilySettingDao familySettingDao;
        @Mock
        private TransactionService transactionService;
        @Mock
        private WalletService walletService;
        @Mock
        private CategoryService categoryService;
        @Mock
        private PeriodLockService periodLockService;
        @Mock
        private SpendingLimitService spendingLimitService;
        @Mock
        private ApplicationEventPublisher eventPublisher;
        @Mock
        private IdempotencyGuard idempotencyGuard;

        private TransactionApprovalService service() {
            return new TransactionApprovalService(approvalDao, familySettingDao, transactionService, walletService,
                    categoryService, periodLockService, spendingLimitService, eventPublisher, CLOCK, idempotencyGuard);
        }

        private void threshold(String value) {
            FamilySetting setting = new FamilySetting();
            setting.setFamilyId(1L);
            setting.setApprovalThreshold(value == null ? null : new BigDecimal(value));
            when(familySettingDao.selectByFamilyId(1L)).thenReturn(Optional.of(setting));
        }

        @Test
        void requiresApproval_onlyForANonOwnersExpenseAboveTheThreshold() {
            threshold("1000000");
            TransactionRequest big = new TransactionRequest(5L, 7L, "EXPENSE", new BigDecimal("1500000"), AT, null);
            TransactionRequest small = new TransactionRequest(5L, 7L, "EXPENSE", new BigDecimal("1000000"), AT, null);
            TransactionRequest income = new TransactionRequest(5L, 7L, "INCOME", new BigDecimal("5000000"), AT, null);

            assertThat(service().requiresApproval(1L, "CHILD", big)).isTrue();
            assertThat(service().requiresApproval(1L, "MEMBER", small)).isFalse();
            assertThat(service().requiresApproval(1L, "MEMBER", income)).isFalse();
            assertThat(service().requiresApproval(1L, "OWNER", big)).isFalse();
        }

        @Test
        void submit_filesAPendingRequest_andTellsTheOwner() {
            when(walletService.requireOwnedByFamily(5L, 1L)).thenReturn(new Wallet());

            var response = service().submit(1L, 7L, "be@b.com", "Bé Na",
                    new TransactionRequest(5L, 7L, "EXPENSE", new BigDecimal("2000000"), AT, "Xe đạp"));

            assertThat(response.status()).isEqualTo("PENDING");
            ArgumentCaptor<ExpenseEvent> event = ArgumentCaptor.forClass(ExpenseEvent.class);
            verify(eventPublisher).publishEvent(event.capture());
            assertThat(event.getValue().eventType()).isEqualTo(ExpenseEvent.APPROVAL_REQUESTED);
            assertThat(event.getValue().targetRole()).isEqualTo("OWNER");
            assertThat(event.getValue().message()).contains("Bé Na").contains("2.000.000");
            verify(transactionService, never()).create(any(), any(), any(), any(), any(), any());
        }

        @Test
        void approve_recordsTheRequestersTransaction_andTellsThem() {
            TransactionApproval pending = pending();
            when(approvalDao.selectById(3L)).thenReturn(Optional.of(pending));
            when(transactionService.create(eq(1L), eq(7L), eq("be@b.com"), eq("Bé Na"), any(TransactionRequest.class), eq(null)))
                    .thenReturn(new TransactionResponse(99L, 5L, 7L, 1L, 7L, "Bé Na", "EXPENSE",
                            new BigDecimal("2000000"), AT, null, false, null, null));

            var response = service().approve(1L, 3L, 1L, "Mẹ");

            assertThat(response.status()).isEqualTo("APPROVED");
            assertThat(response.transactionId()).isEqualTo(99L);
            ArgumentCaptor<ExpenseEvent> event = ArgumentCaptor.forClass(ExpenseEvent.class);
            verify(eventPublisher).publishEvent(event.capture());
            assertThat(event.getValue().targetUserId()).isEqualTo(7L);
        }

        @Test
        void aDecidedRequest_cannotBeDecidedAgain() {
            TransactionApproval done = pending();
            done.setStatus("REJECTED");
            when(approvalDao.selectById(3L)).thenReturn(Optional.of(done));

            assertThatThrownBy(() -> service().approve(1L, 3L, 1L, "Mẹ")).isInstanceOf(ConflictException.class);
        }

        private TransactionApproval pending() {
            TransactionApproval a = new TransactionApproval();
            a.setId(3L);
            a.setFamilyId(1L);
            a.setRequesterUserId(7L);
            a.setRequesterName("Bé Na");
            a.setRequesterEmail("be@b.com");
            a.setWalletId(5L);
            a.setCategoryId(7L);
            a.setAmount(new BigDecimal("2000000"));
            a.setOccurredAt(AT);
            a.setStatus("PENDING");
            return a;
        }
    }

    @Nested
    class Viewer {

        @Test
        void refusesWrites_butLetsReadsThrough() {
            assertThat(ViewerReadOnlyInterceptorAccess.allows("GET", "ROLE_VIEWER")).isTrue();
            assertThat(ViewerReadOnlyInterceptorAccess.allows("POST", "ROLE_MEMBER")).isTrue();
            assertThatThrownBy(() -> ViewerReadOnlyInterceptorAccess.allows("POST", "ROLE_VIEWER"))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
            assertThatThrownBy(() -> ViewerReadOnlyInterceptorAccess.allows("DELETE", "ROLE_VIEWER"))
                    .isInstanceOf(ApiException.class);
        }
    }
}
