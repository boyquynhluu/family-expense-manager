package com.family.expensemanager.expense.dao;

import java.util.List;

import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;

import com.family.expensemanager.expense.domain.entity.TransactionAuditLog;

/**
 * Append-only: there is deliberately no update or delete.
 *
 * @author boyquynhluu
 */
@Dao
public interface TransactionAuditLogDao {

    @Insert
    int insert(TransactionAuditLog log);

    /** Oldest first; scoped to the family so one family can never read another's history by guessing ids. */
    @Select
    List<TransactionAuditLog> selectByTransactionIdAndFamilyId(Long transactionId, Long familyId);
}
