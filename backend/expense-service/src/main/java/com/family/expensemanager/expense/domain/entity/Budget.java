package com.family.expensemanager.expense.domain.entity;

import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;
import org.seasar.doma.Version;

import java.math.BigDecimal;

/**
 * @author boyquynhluu
 */
@Entity
@Table(name = "BUDGETS")
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "family_id")
    private Long familyId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "wallet_id")
    private Long walletId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "period_type")
    // README A6: MONTH (periodMonth = yyyy-MM) or YEAR (periodMonth = yyyy).
    private String periodType = "MONTH";

    @Column(name = "period_month")
    private String periodMonth;

    @Column(name = "limit_amount")
    private BigDecimal limitAmount;

    private Boolean rollover = false;

    /** Optimistic locking — see V10 migration. Doma checks/increments this on every UPDATE automatically. */
    @Version
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFamilyId() {
        return familyId;
    }

    public void setFamilyId(Long familyId) {
        this.familyId = familyId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getPeriodMonth() {
        return periodMonth;
    }

    public void setPeriodMonth(String periodMonth) {
        this.periodMonth = periodMonth;
    }

    public BigDecimal getLimitAmount() {
        return limitAmount;
    }

    public void setLimitAmount(BigDecimal limitAmount) {
        this.limitAmount = limitAmount;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Long getWalletId() {
        return walletId;
    }

    public void setWalletId(Long walletId) {
        this.walletId = walletId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getPeriodType() {
        return periodType;
    }

    public void setPeriodType(String periodType) {
        this.periodType = periodType;
    }

    public Boolean getRollover() {
        return rollover;
    }

    public void setRollover(Boolean rollover) {
        this.rollover = rollover;
    }
}
