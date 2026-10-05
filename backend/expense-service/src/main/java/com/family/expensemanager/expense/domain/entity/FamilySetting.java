package com.family.expensemanager.expense.domain.entity;

import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * README A5: per-family settings kept by expense-service (the approval threshold).
 *
 * @author boyquynhluu
 */
@Entity
@Table(name = "FAMILY_SETTINGS")
public class FamilySetting {

    @Id
    @Column(name = "family_id")
    private Long familyId;

    @Column(name = "approval_threshold")
    private BigDecimal approvalThreshold;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getFamilyId() {
        return familyId;
    }

    public void setFamilyId(Long familyId) {
        this.familyId = familyId;
    }

    public BigDecimal getApprovalThreshold() {
        return approvalThreshold;
    }

    public void setApprovalThreshold(BigDecimal approvalThreshold) {
        this.approvalThreshold = approvalThreshold;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
