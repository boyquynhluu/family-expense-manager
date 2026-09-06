package com.family.expensemanager.expense.dao;

import com.family.expensemanager.expense.domain.entity.Budget;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;

import java.util.List;
import java.util.Optional;

@Dao
public interface BudgetDao {

    @Insert
    int insert(Budget budget);

    @Select
    List<Budget> selectByFamilyId(Long familyId);

    @Select
    Optional<Budget> selectByCategoryAndPeriod(Long categoryId, String periodMonth);
}
