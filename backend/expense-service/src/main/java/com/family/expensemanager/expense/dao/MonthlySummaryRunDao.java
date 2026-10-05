package com.family.expensemanager.expense.dao;

import com.family.expensemanager.expense.domain.entity.MonthlySummaryRun;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;

import java.util.Optional;

/**
 * @author boyquynhluu
 */
@Dao
public interface MonthlySummaryRunDao {

    @Insert
    int insert(MonthlySummaryRun run);

    @Select
    Optional<MonthlySummaryRun> selectByFamilyAndMonth(Long familyId, String periodMonth);
}
