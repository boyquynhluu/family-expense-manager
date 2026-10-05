package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.event.ExpenseEvent;
import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.ConflictException;
import com.family.expensemanager.expense.dao.CategoryDao;
import com.family.expensemanager.expense.dao.LoanDao;
import com.family.expensemanager.expense.dao.LoanPaymentDao;
import com.family.expensemanager.expense.dao.RecurringDraftDao;
import com.family.expensemanager.expense.dao.RecurringTransactionDao;
import com.family.expensemanager.expense.dao.SavingsGoalDao;
import com.family.expensemanager.expense.dao.TransactionDao;
import com.family.expensemanager.expense.dao.WalletAdjustmentDao;
import com.family.expensemanager.expense.dao.WalletDao;
import com.family.expensemanager.expense.dao.WalletTransferDao;
import com.family.expensemanager.expense.domain.entity.Category;
import com.family.expensemanager.expense.domain.entity.Loan;
import com.family.expensemanager.expense.domain.entity.LoanPayment;
import com.family.expensemanager.expense.domain.entity.RecurringDraft;
import com.family.expensemanager.expense.domain.entity.SavingsGoal;
import com.family.expensemanager.expense.domain.entity.Wallet;
import com.family.expensemanager.expense.dto.ConfirmDraftRequest;
import com.family.expensemanager.expense.dto.CreateCategoryRequest;
import com.family.expensemanager.expense.dto.CreateWalletRequest;
import com.family.expensemanager.expense.dto.LoanPaymentRequest;
import com.family.expensemanager.expense.dto.LoanRequest;
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
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * README A1, A4, B3, C1, C2, C3, C6: one nested class per service.
 */
class NewFeatureServicesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-05T03:00:00Z"), ZoneId.of("UTC"));

    private static Wallet wallet(String type, String creditLimit) {
        Wallet w = new Wallet();
        w.setId(5L);
        w.setFamilyId(1L);
        w.setName("Ví");
        w.setCurrency("VND");
        w.setInitialBalance(BigDecimal.ZERO);
        w.setWalletType(type);
        w.setCreditLimit(creditLimit == null ? null : new BigDecimal(creditLimit));
        return w;
    }

    // ------------------------------------------------------------------ A1 + C3
    @Nested
    @ExtendWith(MockitoExtension.class)
    class NegativeBalancePolicy {

        @Mock private WalletDao walletDao;
        @Mock private TransactionDao transactionDao;
        @Mock private RecurringTransactionDao recurringTransactionDao;
        @Mock private WalletTransferDao walletTransferDao;
        @Mock private WalletAdjustmentDao walletAdjustmentDao;
        @Mock private PeriodLockService periodLockService;
        @Mock private EntityAuditService entityAuditService;
        @Mock private LoanDao loanDao;
        @Mock private SavingsGoalDao savingsGoalDao;

        private WalletService service(String balance) {
            lenient().when(transactionDao.sumAmountByWalletAndType(any(), any())).thenReturn(BigDecimal.ZERO);
            lenient().when(walletTransferDao.sumAmountIntoWallet(any())).thenReturn(BigDecimal.ZERO);
            lenient().when(walletTransferDao.sumAmountFromWallet(any())).thenReturn(BigDecimal.ZERO);
            lenient().when(walletAdjustmentDao.sumAmountByWalletId(any())).thenReturn(new BigDecimal(balance));
            lenient().when(loanDao.sumNetFlowForWallet(any())).thenReturn(BigDecimal.ZERO);
            return new WalletService(walletDao, transactionDao, recurringTransactionDao, walletTransferDao,
                    walletAdjustmentDao, periodLockService, entityAuditService, loanDao, savingsGoalDao);
        }

        @Test
        void cashWallet_cannotGoBelowZero() {
            WalletService service = service("100000");
            assertThatCode(() -> service.requireAllowedOutflow(wallet("CASH", null), new BigDecimal("100000")))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> service.requireAllowedOutflow(wallet("CASH", null), new BigDecimal("100001")))
                    .isInstanceOf(BadRequestException.class).hasMessageContaining("100.000");
            verify(walletDao, org.mockito.Mockito.atLeastOnce()).selectByIdForUpdate(5L);
        }

        @Test
        void creditCard_mayGoDownToMinusItsLimit() {
            WalletService service = service("0");
            assertThatCode(() -> service.requireAllowedOutflow(wallet("CREDIT_CARD", "5000000"), new BigDecimal("5000000")))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> service.requireAllowedOutflow(wallet("CREDIT_CARD", "5000000"), new BigDecimal("5000001")))
                    .isInstanceOf(BadRequestException.class).hasMessageContaining("hạn mức");
        }

        @Test
        void moneyComingIn_alwaysPasses() {
            assertThatCode(() -> service("-50").requireAllowedOutflow(wallet("CASH", null), new BigDecimal("-10")))
                    .doesNotThrowAnyException();
        }

        @Test
        void creditCard_needsALimit_andOnlyKeepsItsOwnFields() {
            WalletService service = service("0");
            assertThatThrownBy(() -> service.create(1L, new CreateWalletRequest("Thẻ", "VND", BigDecimal.ZERO, null,
                    "CREDIT_CARD", null, 5, 20, null, null))).isInstanceOf(BadRequestException.class);

            var cash = service.create(1L, new CreateWalletRequest("Tiền mặt", "VND", BigDecimal.ZERO, null,
                    "CASH", new BigDecimal("100"), 5, 20, new BigDecimal("6"), LocalDate.of(2027, 1, 1)));
            assertThat(cash.walletType()).isEqualTo("CASH");
            assertThat(cash.creditLimit()).isNull();
            assertThat(cash.interestRate()).isNull();
        }
    }

    // ------------------------------------------------------------------ B3
    @Nested
    @ExtendWith(MockitoExtension.class)
    class Loans {

        @Mock private LoanDao loanDao;
        @Mock private LoanPaymentDao loanPaymentDao;
        @Mock private WalletService walletService;
        @Mock private PeriodLockService periodLockService;

        private LoanService service() {
            return new LoanService(loanDao, loanPaymentDao, walletService, periodLockService, CLOCK);
        }

        private LoanRequest request(String direction) {
            return new LoanRequest(direction, "Anh Hùng", null, new BigDecimal("3000000"), 5L,
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 1), null);
        }

        @Test
        void lending_takesMoneyOutOfTheWallet_soTheBalancePolicyApplies() {
            Wallet w = wallet("CASH", null);
            when(walletService.requireOwnedByFamily(5L, 1L)).thenReturn(w);

            service().create(1L, 7L, "An", false, request("LENT"));

            verify(walletService).requireAllowedOutflow(w, new BigDecimal("3000000"));
            verify(loanDao).insert(any(Loan.class));
        }

        @Test
        void borrowing_bringsMoneyIn_soNoBalanceCheck() {
            when(walletService.requireOwnedByFamily(5L, 1L)).thenReturn(wallet("CASH", null));

            service().create(1L, 7L, "An", false, request("BORROWED"));

            verify(walletService, never()).requireAllowedOutflow(any(), any());
        }

        @Test
        void theLoanBelongsToTheCaller_unlessTheOwnerNamesAnotherMember() {
            when(walletService.requireOwnedByFamily(5L, 1L)).thenReturn(wallet("CASH", null));
            LoanRequest forSpouse = new LoanRequest("BORROWED", "Anh Hùng", null, new BigDecimal("3000000"), 5L,
                    LocalDate.of(2026, 10, 1), null, null, 8L);

            assertThat(service().create(1L, 7L, "An", false, request("BORROWED")).memberUserId()).isEqualTo(7L);
            assertThat(service().create(1L, 7L, "Mẹ", true, forSpouse).memberUserId()).isEqualTo(8L);
            assertThatThrownBy(() -> service().create(1L, 7L, "An", false, forSpouse))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        }

        private LoanRequest fromFamilyWallet(Long otherWalletId) {
            return new LoanRequest("BORROWED", null, null, new BigDecimal("2000000"), 5L,
                    LocalDate.of(2026, 10, 1), null, null, null, otherWalletId);
        }

        @Test
        void borrowingFromAFamilyWallet_takesTheMoneyOutOfIt_andUsesItsNameAsTheCounterparty() {
            Wallet mine = wallet("CASH", null);
            mine.setOwnerUserId(7L);
            Wallet mom = wallet("CASH", null);
            mom.setId(6L);
            mom.setName("Ví mẹ");
            mom.setOwnerUserId(8L);
            when(walletService.requireOwnedByFamily(5L, 1L)).thenReturn(mine);
            when(walletService.requireOwnedByFamily(6L, 1L)).thenReturn(mom);

            var response = service().create(1L, 7L, "Bố", false, fromFamilyWallet(6L));

            assertThat(response.counterpartyWalletId()).isEqualTo(6L);
            assertThat(response.counterpartyName()).isEqualTo("Ví mẹ");
            verify(walletService).requireUsableBy(mom, 7L, false);
            verify(walletService).requireAllowedOutflow(mom, new BigDecimal("2000000"));
        }

        @Test
        void aFamilyLoan_needsAnotherPersonsOrASharedWallet_andAnOutsideOneNeedsAName() {
            Wallet mine = wallet("CASH", null);
            Wallet alsoMine = wallet("BANK", null);
            alsoMine.setId(6L);
            alsoMine.setOwnerUserId(7L);
            when(walletService.requireOwnedByFamily(5L, 1L)).thenReturn(mine);
            when(walletService.requireOwnedByFamily(6L, 1L)).thenReturn(alsoMine);

            assertThatThrownBy(() -> service().create(1L, 7L, "Bố", false, fromFamilyWallet(5L)))
                    .isInstanceOf(BadRequestException.class);
            assertThatThrownBy(() -> service().create(1L, 7L, "Bố", false, fromFamilyWallet(6L)))
                    .isInstanceOf(BadRequestException.class);
            assertThatThrownBy(() -> service().create(1L, 7L, "Bố", false, fromFamilyWallet(null)))
                    .isInstanceOf(BadRequestException.class);
            verify(loanDao, never()).insert(any());
        }

        @Test
        void dueDate_cannotBeBeforeTheStart() {
            LoanRequest bad = new LoanRequest("LENT", "A", null, BigDecimal.TEN, 5L, LocalDate.of(2026, 10, 1),
                    LocalDate.of(2026, 9, 1), null);
            assertThatThrownBy(() -> service().create(1L, 7L, "An", false, bad)).isInstanceOf(BadRequestException.class);
        }

        @Test
        void lastRepayment_closesTheLoan_andOverpayingIsRefused() {
            Loan loan = loan();
            when(loanDao.selectById(3L)).thenReturn(Optional.of(loan));
            when(loanPaymentDao.sumByLoanId(3L)).thenReturn(new BigDecimal("2000000"));
            when(walletService.requireOwnedByFamily(5L, 1L)).thenReturn(wallet("CASH", null));

            assertThatThrownBy(() -> service().addPayment(1L, 3L, 7L, "An", true,
                    new LoanPaymentRequest(new BigDecimal("1000001"), 5L, LocalDateTime.of(2026, 10, 5, 9, 0), null)))
                    .isInstanceOf(BadRequestException.class);

            service().addPayment(1L, 3L, 7L, "An", true,
                    new LoanPaymentRequest(new BigDecimal("1000000"), 5L, LocalDateTime.of(2026, 10, 5, 9, 0), null));
            assertThat(loan.getStatus()).isEqualTo("CLOSED");
            verify(loanPaymentDao).insert(any(LoanPayment.class));
        }

        @Test
        void aLoanWithRepayments_cannotBeDeleted() {
            when(loanDao.selectById(3L)).thenReturn(Optional.of(loan()));
            when(loanPaymentDao.selectByLoanId(3L)).thenReturn(List.of(new LoanPayment()));

            assertThatThrownBy(() -> service().delete(1L, 3L, 7L, true)).isInstanceOf(ConflictException.class);
        }

        private Loan loan() {
            Loan l = new Loan();
            l.setId(3L);
            l.setFamilyId(1L);
            l.setDirection("BORROWED");
            l.setPrincipal(new BigDecimal("3000000"));
            l.setWalletId(5L);
            l.setStartDate(LocalDate.of(2026, 10, 1));
            l.setStatus("OPEN");
            l.setCreatedByUserId(7L);
            return l;
        }
    }

    // ------------------------------------------------------------------ C1
    @Nested
    @ExtendWith(MockitoExtension.class)
    class SavingsGoals {

        @Mock private SavingsGoalDao savingsGoalDao;
        @Mock private WalletService walletService;
        @Mock private ApplicationEventPublisher eventPublisher;

        @Test
        void announcesEachNewlyReachedMilestoneOnce() {
            SavingsGoal goal = new SavingsGoal();
            goal.setId(1L);
            goal.setFamilyId(1L);
            goal.setName("Mua xe");
            goal.setTargetAmount(new BigDecimal("10000000"));
            goal.setWalletId(5L);
            goal.setMilestoneReached(50);
            when(savingsGoalDao.selectInProgress()).thenReturn(List.of(goal));
            Wallet w = wallet("SAVINGS", null);
            when(walletService.requireOwnedByFamily(5L, 1L)).thenReturn(w);
            when(walletService.currentBalanceOf(w)).thenReturn(new BigDecimal("8500000"));
            SavingsGoalService service = new SavingsGoalService(savingsGoalDao, walletService, eventPublisher, CLOCK);

            service.checkMilestones();

            assertThat(goal.getMilestoneReached()).isEqualTo(80);
            ArgumentCaptor<ExpenseEvent> event = ArgumentCaptor.forClass(ExpenseEvent.class);
            verify(eventPublisher).publishEvent(event.capture());
            assertThat(event.getValue().eventType()).isEqualTo(ExpenseEvent.SAVINGS_MILESTONE);
            assertThat(event.getValue().title()).contains("80%");
        }
    }

    // ------------------------------------------------------------------ A4
    @Nested
    @ExtendWith(MockitoExtension.class)
    class RecurringDrafts {

        @Mock private RecurringDraftDao recurringDraftDao;
        @Mock private TransactionService transactionService;

        @Test
        void confirm_recordsTheRealAmount_onTheDueDay() {
            RecurringDraft draft = new RecurringDraft();
            draft.setId(2L);
            draft.setFamilyId(1L);
            draft.setWalletId(5L);
            draft.setCategoryId(7L);
            draft.setType("EXPENSE");
            draft.setSuggestedAmount(new BigDecimal("300000"));
            draft.setDueDate(LocalDate.of(2026, 10, 5));
            draft.setCreatedByUserId(7L);
            draft.setStatus("PENDING");
            when(recurringDraftDao.selectById(2L)).thenReturn(Optional.of(draft));
            ArgumentCaptor<TransactionRequest> request = ArgumentCaptor.forClass(TransactionRequest.class);
            when(transactionService.create(eq(1L), eq(7L), any(), any(), eq(false), request.capture(), eq(null)))
                    .thenReturn(new TransactionResponse(99L, 5L, 7L, 1L, 7L, "An", "EXPENSE", new BigDecimal("356000"),
                            LocalDateTime.of(2026, 10, 5, 12, 0), null, false, null, null));

            var response = new RecurringDraftService(recurringDraftDao, transactionService, CLOCK)
                    .confirm(1L, 2L, 7L, "a@b.com", "An", false, new ConfirmDraftRequest(new BigDecimal("356000"), null));

            assertThat(response.status()).isEqualTo("CONFIRMED");
            assertThat(response.transactionId()).isEqualTo(99L);
            assertThat(request.getValue().amount()).isEqualByComparingTo("356000");
            assertThat(request.getValue().occurredAt().toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 5));
        }

        @Test
        void onlyTheRuleCreatorOrTheOwner_mayDecide() {
            RecurringDraft draft = new RecurringDraft();
            draft.setFamilyId(1L);
            draft.setCreatedByUserId(8L);
            draft.setStatus("PENDING");
            when(recurringDraftDao.selectById(2L)).thenReturn(Optional.of(draft));

            assertThatThrownBy(() -> new RecurringDraftService(recurringDraftDao, transactionService, CLOCK)
                    .skip(1L, 2L, 7L, "An", false))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        }
    }

    // ------------------------------------------------------------------ C2
    @Nested
    @ExtendWith(MockitoExtension.class)
    class Reminders {

        @Mock private RecurringTransactionDao recurringTransactionDao;
        @Mock private WalletDao walletDao;
        @Mock private LoanDao loanDao;
        @Mock private WalletService walletService;
        @Mock private SavingsGoalService savingsGoalService;
        @Mock private ApplicationEventPublisher eventPublisher;
        @Mock private TransactionTemplate transactionTemplate;

        @Test
        void nextDueDate_staysInTheMonth_orMovesToTheNext_clampedToItsLastDay() {
            assertThat(ReminderService.nextDueDate(LocalDate.of(2026, 10, 5), 20)).isEqualTo(LocalDate.of(2026, 10, 20));
            assertThat(ReminderService.nextDueDate(LocalDate.of(2026, 10, 25), 20)).isEqualTo(LocalDate.of(2026, 11, 20));
            assertThat(ReminderService.nextDueDate(LocalDate.of(2027, 2, 1), 31)).isEqualTo(LocalDate.of(2027, 2, 28));
        }

        @Test
        void creditCard_inDebt_isRemindedOnce_threeDaysBeforeItsDeadline() {
            Wallet card = wallet("CREDIT_CARD", "5000000");
            card.setPaymentDueDay(7);
            card.setOwnerUserId(7L);
            when(walletDao.selectCreditCardsWithDueDay()).thenReturn(List.of(card));
            when(walletService.currentBalanceOf(card)).thenReturn(new BigDecimal("-1200000"));
            doAnswer(inv -> {
                inv.<Consumer<TransactionStatus>>getArgument(0).accept(null);
                return null;
            }).when(transactionTemplate).executeWithoutResult(any());
            ReminderService service = new ReminderService(recurringTransactionDao, walletDao, loanDao, walletService,
                    savingsGoalService, eventPublisher, transactionTemplate, CLOCK);

            service.remindCards(LocalDate.of(2026, 10, 5));

            assertThat(card.getLastPaymentReminderOn()).isEqualTo(LocalDate.of(2026, 10, 7));
            ArgumentCaptor<ExpenseEvent> event = ArgumentCaptor.forClass(ExpenseEvent.class);
            verify(eventPublisher).publishEvent(event.capture());
            assertThat(event.getValue().eventType()).isEqualTo(ExpenseEvent.BILL_DUE_SOON);
            assertThat(event.getValue().message()).contains("1.200.000");

            // Same due date again: nothing new.
            org.mockito.Mockito.clearInvocations(eventPublisher);
            service.remindCards(LocalDate.of(2026, 10, 6));
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    // ------------------------------------------------------------------ C6
    @Nested
    @ExtendWith(MockitoExtension.class)
    class SubCategories {

        @Mock private CategoryDao categoryDao;
        @Mock private TransactionDao transactionDao;
        @Mock private com.family.expensemanager.expense.dao.BudgetDao budgetDao;
        @Mock private RecurringTransactionDao recurringTransactionDao;

        private CategoryService service() {
            return new CategoryService(categoryDao, transactionDao, budgetDao, recurringTransactionDao);
        }

        private Category category(Long id, Long parentId, String type) {
            Category c = new Category();
            c.setId(id);
            c.setFamilyId(1L);
            c.setParentId(parentId);
            c.setName("C" + id);
            c.setType(type);
            return c;
        }

        @Test
        void aChildNeedsATopLevelParentOfTheSameType() {
            when(categoryDao.selectById(10L)).thenReturn(Optional.of(category(10L, null, "EXPENSE")));
            when(categoryDao.selectById(11L)).thenReturn(Optional.of(category(11L, 10L, "EXPENSE")));

            var child = service().create(1L, new CreateCategoryRequest("Cà phê", "EXPENSE", null, null, 10L));
            assertThat(child.parentId()).isEqualTo(10L);

            assertThatThrownBy(() -> service().create(1L, new CreateCategoryRequest("Sâu", "EXPENSE", null, null, 11L)))
                    .isInstanceOf(BadRequestException.class);
            assertThatThrownBy(() -> service().create(1L, new CreateCategoryRequest("Lương", "INCOME", null, null, 10L)))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void aParentWithChildren_cannotBeDeleted() {
            when(categoryDao.selectById(10L)).thenReturn(Optional.of(category(10L, null, "EXPENSE")));
            when(categoryDao.countActiveChildren(10L)).thenReturn(2L);

            assertThatThrownBy(() -> service().delete(10L, 1L)).isInstanceOf(ConflictException.class);
        }
    }

    // ------------------------------------------------------------------ C6 tags
    @Test
    void tags_areTrimmedAndDeduplicatedIgnoringCase() {
        assertThat(TransactionService.normalizeTags(List.of(" Du lịch  Đà Lạt ", "du lịch đà lạt", "", "Tết")))
                .containsExactly("Du lịch Đà Lạt", "Tết");
        assertThat(TransactionService.normalizeTags(null)).isEmpty();
    }
}
