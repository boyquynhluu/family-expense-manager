package com.family.expensemanager.expense.dao;

import com.family.expensemanager.expense.domain.entity.Budget;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;

import java.util.List;
import java.util.Optional;

/**
 * @author boyquynhluu
 */
@Dao
public interface BudgetDao {

    @Select
    long countByWalletId(Long walletId);

    /** README A6: every budget covering that month — its MONTH budgets and the YEAR budgets of its year. */
    @Select
    List<Budget> selectApplicable(Long familyId, String periodMonth, String periodYear);

    /** README A6: the budget with exactly this scope and period, if any (duplicates, rollover source). */
    @Select
    Optional<Budget> selectSameScope(Long familyId, Long categoryId, Long walletId, Long userId, String periodType,
                                     String periodMonth);

    @Insert
    int insert(Budget budget);

    @Update
    int update(Budget budget);

    @Delete
    int delete(Budget budget);

    @Select
    long countByFamilyId(Long familyId);

    @Select
    List<Budget> selectByFamilyIdPaged(Long familyId, int limit, int offset);

    @Select
    Optional<Budget> selectById(Long id);

    @Select
    Optional<Budget> selectByCategoryAndPeriod(Long categoryId, String periodMonth);

    @Select
    Optional<Budget> selectOverallByPeriod(Long familyId, String periodMonth);

    @Select
    List<Budget> selectByFamilyAndPeriod(Long familyId, String periodMonth);

    @Select
    long countByCategoryId(Long categoryId);
}
