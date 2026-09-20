package com.family.expensemanager.expense.dao;

import com.family.expensemanager.expense.domain.entity.Transaction;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    /** Backs the paginated/filtered {@code GET /transactions} list — see {@code countByFamilyIdFiltered}. */
    @Select
    List<Transaction> selectByFamilyIdFiltered(
            Long familyId, Long walletId, Long categoryId, String type,
            LocalDate fromDate, LocalDate toDate, String notePattern, BigDecimal minAmount, BigDecimal maxAmount,
            int limit, int offset);

    @Select
    long countByFamilyIdFiltered(
            Long familyId, Long walletId, Long categoryId, String type,
            LocalDate fromDate, LocalDate toDate, String notePattern, BigDecimal minAmount, BigDecimal maxAmount);

    @Select
    Optional<Transaction> selectById(Long id);

    @Select
    Optional<Transaction> selectDeletedById(Long id);

    @Select
    BigDecimal sumAmountByCategoryPeriodAndType(Long familyId, Long categoryId, String periodMonth, String type);

    @Select
    BigDecimal sumAmountByWalletCategoryPeriodAndType(
            Long familyId, Long walletId, Long categoryId, String periodMonth, String type);

    @Select
    BigDecimal sumAmountByFamilyPeriodAndType(Long familyId, String periodMonth, String type);

    @Select
    BigDecimal sumAmountByWalletAndType(Long walletId, String type);

    @Select
    long countByWalletId(Long walletId);

    @Select
    long countByCategoryId(Long categoryId);

    @Select
    long countDeletedByFamilyId(Long familyId);

    @Select
    List<Transaction> selectDeletedByFamilyIdPaged(Long familyId, int limit, int offset);

    @Update(sqlFile = true)
    int restore(Long id, Long familyId);
}
