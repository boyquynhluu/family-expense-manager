package com.family.expensemanager.expense.dao;

import com.family.expensemanager.expense.domain.entity.SavingsGoal;
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
public interface SavingsGoalDao {

    @Insert
    int insert(SavingsGoal goal);

    @Update
    int update(SavingsGoal goal);

    @Delete
    int delete(SavingsGoal goal);

    @Select
    Optional<SavingsGoal> selectById(Long id);

    /** Active goals first, then by deadline. Never paged: a family has a handful. */
    @Select
    List<SavingsGoal> selectByFamilyId(Long familyId);

    /** Every family's goals still being worked on (not archived, last milestone below 100%) — for the scheduler. */
    @Select
    List<SavingsGoal> selectInProgress();

    @Select
    long countByWalletId(Long walletId);
}
