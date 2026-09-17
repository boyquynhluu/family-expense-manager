package com.family.expensemanager.expense.dao;

import com.family.expensemanager.expense.domain.entity.RecurringTransaction;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Dao
public interface RecurringTransactionDao {

    @Insert
    int insert(RecurringTransaction recurringTransaction);

    @Update
    int update(RecurringTransaction recurringTransaction);

    @Delete
    int delete(RecurringTransaction recurringTransaction);

    @Select
    List<RecurringTransaction> selectByFamilyId(Long familyId);

    @Select
    Optional<RecurringTransaction> selectById(Long id);

    /** Rows the daily scheduler needs to act on — see {@code RecurringTransactionScheduler}. */
    @Select
    List<RecurringTransaction> selectDue(LocalDate today);

    @Select
    long countByWalletId(Long walletId);

    @Select
    long countByCategoryId(Long categoryId);
}
