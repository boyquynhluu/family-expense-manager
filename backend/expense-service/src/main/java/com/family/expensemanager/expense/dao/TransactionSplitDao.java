package com.family.expensemanager.expense.dao;

import com.family.expensemanager.expense.domain.entity.TransactionSplit;
import org.seasar.doma.BatchInsert;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Select;

import java.util.List;

/**
 * @author boyquynhluu
 */
@Dao
public interface TransactionSplitDao {

    @BatchInsert
    int[] insertAll(List<TransactionSplit> splits);

    @Delete(sqlFile = true)
    int deleteByTransactionId(Long transactionId);

    /** In part order. */
    @Select
    List<TransactionSplit> selectByTransactionIds(List<Long> transactionIds);

    @Select
    List<Long> selectTransactionIdsByCategoryIds(List<Long> categoryIds);

    @Select
    long countByCategoryId(Long categoryId);
}
