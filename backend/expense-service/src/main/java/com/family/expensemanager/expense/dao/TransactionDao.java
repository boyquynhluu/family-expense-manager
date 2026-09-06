package com.family.expensemanager.expense.dao;

import com.family.expensemanager.expense.domain.entity.Transaction;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Dao
public interface TransactionDao {

    @Insert
    int insert(Transaction transaction);

    @Update
    int update(Transaction transaction);

    @Delete
    int delete(Transaction transaction);

    @Select
    List<Transaction> selectByFamilyId(Long familyId);

    @Select
    Optional<Transaction> selectById(Long id);

    @Select
    BigDecimal sumAmountByCategoryPeriodAndType(Long familyId, Long categoryId, String periodMonth, String type);

    @Select
    BigDecimal sumAmountByFamilyPeriodAndType(Long familyId, String periodMonth, String type);
}
