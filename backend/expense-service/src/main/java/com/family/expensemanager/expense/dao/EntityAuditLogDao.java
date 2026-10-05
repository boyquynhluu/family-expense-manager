package com.family.expensemanager.expense.dao;

import com.family.expensemanager.expense.domain.entity.EntityAuditLog;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;

import java.util.List;

/**
 * @author boyquynhluu
 */
@Dao
public interface EntityAuditLogDao {

    @Insert
    int insert(EntityAuditLog log);

    /** Oldest first, like a transaction's history. */
    @Select
    List<EntityAuditLog> selectByEntity(Long familyId, String entityType, Long entityId);
}
