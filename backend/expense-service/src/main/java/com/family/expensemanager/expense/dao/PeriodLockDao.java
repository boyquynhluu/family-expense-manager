package com.family.expensemanager.expense.dao;

import com.family.expensemanager.expense.domain.entity.PeriodLock;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;

import java.util.List;
import java.util.Optional;

/**
 * @author boyquynhluu
 */
@Dao
public interface PeriodLockDao {

    @Insert
    int insert(PeriodLock lock);

    @Delete
    int delete(PeriodLock lock);

    @Select
    Optional<PeriodLock> selectByFamilyAndMonth(Long familyId, String periodMonth);

    /** Newest month first. A family closes at most 12 months a year, so this is never paged. */
    @Select
    List<PeriodLock> selectByFamilyId(Long familyId);

    /** How many of {@code periodMonths} are closed — one query for a check spanning several months. */
    @Select
    long countByFamilyAndMonths(Long familyId, List<String> periodMonths);

    @Select
    long countByFamilyId(Long familyId);
}
