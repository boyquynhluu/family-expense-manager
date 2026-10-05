package com.family.expensemanager.expense.domain.entity;

import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

import java.time.LocalDateTime;

/**
 * README C7: the monthly summary of {@code periodMonth} went out to this family (no duplicates).
 *
 * @author boyquynhluu
 */
@Entity
@Table(name = "MONTHLY_SUMMARY_RUNS")
public class MonthlySummaryRun {

    @Id
    @Column(name = "family_id")
    private Long familyId;

    @Id
    @Column(name = "period_month")
    private String periodMonth;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    public Long getFamilyId() {
        return familyId;
    }

    public void setFamilyId(Long familyId) {
        this.familyId = familyId;
    }

    public String getPeriodMonth() {
        return periodMonth;
    }

    public void setPeriodMonth(String periodMonth) {
        this.periodMonth = periodMonth;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }
}
