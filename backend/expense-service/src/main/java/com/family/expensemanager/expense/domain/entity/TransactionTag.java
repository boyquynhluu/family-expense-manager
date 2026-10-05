package com.family.expensemanager.expense.domain.entity;

import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

/**
 * README C6: a tag on a transaction.
 *
 * @author boyquynhluu
 */
@Entity
@Table(name = "TRANSACTION_TAGS")
public class TransactionTag {

    @Id
    @Column(name = "transaction_id")
    private Long transactionId;

    @Id
    @Column(name = "tag_id")
    private Long tagId;

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public Long getTagId() {
        return tagId;
    }

    public void setTagId(Long tagId) {
        this.tagId = tagId;
    }
}
