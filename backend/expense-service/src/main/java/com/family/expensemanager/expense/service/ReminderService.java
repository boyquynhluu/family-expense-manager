package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.currency.CurrencyUtil;
import com.family.expensemanager.common.event.ExpenseEvent;
import com.family.expensemanager.expense.dao.LoanDao;
import com.family.expensemanager.expense.dao.RecurringTransactionDao;
import com.family.expensemanager.expense.dao.WalletDao;
import com.family.expensemanager.expense.domain.entity.Loan;
import com.family.expensemanager.expense.domain.entity.RecurringTransaction;
import com.family.expensemanager.expense.domain.entity.Wallet;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The daily reminders (in app + email to the family, each type switchable in the notification settings):
 * <ul>
 *   <li>README C2 — a recurring bill due in N days (the rule's remind_days_before);</li>
 *   <li>README C2 — a credit card's payment deadline in {@value #CARD_REMIND_DAYS} days, with its current debt;</li>
 *   <li>README B3 — an open loan due in {@value #LOAN_REMIND_DAYS} days;</li>
 *   <li>README C1 — newly reached savings milestones.</li>
 * </ul>
 * Each item remembers the date it was reminded for, so a second run the same day sends nothing twice. One item
 * failing is logged and skipped — the others still go out.
 *
 * @author boyquynhluu
 */
@Service
@RequiredArgsConstructor
@Slf4j(topic = "ReminderService")
public class ReminderService {

    static final int CARD_REMIND_DAYS = 3;
    static final int LOAN_REMIND_DAYS = 3;

    private final RecurringTransactionDao recurringTransactionDao;
    private final WalletDao walletDao;
    private final LoanDao loanDao;
    private final WalletService walletService;
    private final SavingsGoalService savingsGoalService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public void runDaily() {
        LocalDate today = LocalDate.now(clock);
        log.info("runDaily - start, today={}", today);
        remindBills(today);
        remindCards(today);
        remindLoans(today);
        try {
            savingsGoalService.checkMilestones();
        } catch (Exception e) {
            log.warn("Không kiểm tra được mốc tiết kiệm", e);
        }
    }

    void remindBills(LocalDate today) {
        for (RecurringTransaction r : recurringTransactionDao.selectReminderDue(today)) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    r.setLastRemindedFor(r.getNextRunDate());
                    recurringTransactionDao.update(r);
                });
                long days = ChronoUnit.DAYS.between(today, r.getNextRunDate());
                String label = r.getNote() != null && !r.getNote().isBlank() ? "\"" + r.getNote().trim() + "\"" : "Hoá đơn định kỳ";
                eventPublisher.publishEvent(ExpenseEvent.notice(ExpenseEvent.BILL_DUE_SOON, r.getFamilyId(),
                        r.getCreatedByUserId(), r.getCreatedByDisplayName(), null, null,
                        "Hoá đơn sắp đến hạn",
                        label + " " + CurrencyUtil.formatCurrency(r.getAmount()) + " đến hạn ngày " + r.getNextRunDate()
                                + " (còn " + days + " ngày)" + ("CONFIRM".equals(r.getMode())
                                ? ". Khi đến hạn, hãy xác nhận số tiền thật trong trang Giao dịch định kỳ." : "."),
                        "/recurring-transactions"));
            } catch (Exception e) {
                log.warn("Không gửi được nhắc hoá đơn recurring id={}", r.getId(), e);
            }
        }
    }

    void remindCards(LocalDate today) {
        for (Wallet card : walletDao.selectCreditCardsWithDueDay()) {
            try {
                LocalDate due = nextDueDate(today, card.getPaymentDueDay());
                if (ChronoUnit.DAYS.between(today, due) > CARD_REMIND_DAYS || due.equals(card.getLastPaymentReminderOn())) {
                    continue;
                }
                BigDecimal balance = walletService.currentBalanceOf(card);
                card.setLastPaymentReminderOn(due);
                transactionTemplate.executeWithoutResult(status -> walletDao.update(card));
                if (balance.signum() >= 0) {
                    continue;
                }
                eventPublisher.publishEvent(ExpenseEvent.notice(ExpenseEvent.BILL_DUE_SOON, card.getFamilyId(),
                        card.getOwnerUserId(), null, card.getOwnerUserId(), null,
                        "Thẻ tín dụng sắp đến hạn thanh toán",
                        "Thẻ \"" + card.getName() + "\" đến hạn thanh toán ngày " + due + ", dư nợ hiện tại "
                                + CurrencyUtil.formatCurrency(balance.negate()) + ".",
                        "/wallets"));
            } catch (Exception e) {
                log.warn("Không gửi được nhắc thẻ tín dụng walletId={}", card.getId(), e);
            }
        }
    }

    void remindLoans(LocalDate today) {
        for (Loan loan : loanDao.selectDueForReminder(today, today.plusDays(LOAN_REMIND_DAYS))) {
            try {
                loan.setLastRemindedFor(loan.getDueDate());
                transactionTemplate.executeWithoutResult(status -> loanDao.update(loan));
                boolean borrowed = LoanService.BORROWED.equals(loan.getDirection());
                eventPublisher.publishEvent(ExpenseEvent.notice(ExpenseEvent.LOAN_DUE_SOON, loan.getFamilyId(),
                        loan.getMemberUserId(), null, loan.getMemberUserId(), null,
                        borrowed ? "Khoản vay sắp đến hạn trả" : "Khoản cho vay sắp đến hạn thu",
                        (borrowed ? "Khoản vay của " : "Khoản cho ") + loan.getCounterpartyName() + " "
                                + CurrencyUtil.formatCurrency(loan.getPrincipal()) + " đến hạn ngày " + loan.getDueDate() + ".",
                        "/loans"));
            } catch (Exception e) {
                log.warn("Không gửi được nhắc khoản vay loanId={}", loan.getId(), e);
            }
        }
    }

    /** The next payment deadline on/after {@code today}; a day past the month's end falls on its last day. */
    static LocalDate nextDueDate(LocalDate today, int dueDay) {
        YearMonth month = YearMonth.from(today);
        LocalDate thisMonth = month.atDay(Math.min(dueDay, month.lengthOfMonth()));
        if (!thisMonth.isBefore(today)) {
            return thisMonth;
        }
        YearMonth next = month.plusMonths(1);
        return next.atDay(Math.min(dueDay, next.lengthOfMonth()));
    }
}
