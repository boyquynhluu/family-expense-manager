package com.family.expensemanager.expense.dao;

import com.family.expensemanager.expense.domain.entity.PeriodLockLog;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;

import java.util.List;

/**
 * @author boyquynhluu
 */
@Dao
public interface PeriodLockLogDao {

    @Insert
    int insert(PeriodLockLog log);

    @Select
    long countByFamilyId(Long familyId);

    /** Newest first. */
    @Select
    List<PeriodLockLog> selectByFamilyIdPaged(Long familyId, int limit, int offset);
}
