package com.family.expensemanager.expense.dao;

import com.family.expensemanager.expense.domain.entity.Budget;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;

import java.util.List;
import java.util.Optional;

@Dao
public interface BudgetDao {

    @Insert
    int insert(Budget budget);

    @Update
    int update(Budget budget);

    @Delete
    int delete(Budget budget);

    @Select
    List<Budget> selectByFamilyId(Long familyId);

    @Select
    Optional<Budget> selectById(Long id);

    @Select
    Optional<Budget> selectByCategoryAndPeriod(Long categoryId, String periodMonth);

    @Select
    long countByCategoryId(Long categoryId);
}
