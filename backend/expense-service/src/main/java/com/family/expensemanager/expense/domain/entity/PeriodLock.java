package com.family.expensemanager.expense.domain.entity;

import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

import java.time.LocalDateTime;

/**
 * A closed month ("tháng đã chốt sổ") of one family; {@code periodMonth} is "yyyy-MM".
 *
 * @author boyquynhluu
 */
@Entity
@Table(name = "PERIOD_LOCKS")
public class PeriodLock {

    @Id
    @Column(name = "family_id")
    private Long familyId;

    @Id
    @Column(name = "period_month")
    private String periodMonth;

    @Column(name = "locked_by_user_id")
    private Long lockedByUserId;

    @Column(name = "locked_by_name")
    private String lockedByName;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

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

    public Long getLockedByUserId() {
        return lockedByUserId;
    }

    public void setLockedByUserId(Long lockedByUserId) {
        this.lockedByUserId = lockedByUserId;
    }

    public String getLockedByName() {
        return lockedByName;
    }

    public void setLockedByName(String lockedByName) {
        this.lockedByName = lockedByName;
    }

    public LocalDateTime getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(LocalDateTime lockedAt) {
        this.lockedAt = lockedAt;
    }
}
