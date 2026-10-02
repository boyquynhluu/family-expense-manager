package com.family.expensemanager.expense.dao;

import com.family.expensemanager.expense.domain.entity.Transaction;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * @author boyquynhluu
 */
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
            Long familyId, Long viewerUserId, boolean showOthersPrivate, Long walletId, Long categoryId, String type,
            LocalDate fromDate, LocalDate toDate, String notePattern, BigDecimal minAmount, BigDecimal maxAmount,
            int limit, int offset);

    @Select
    long countByFamilyIdFiltered(
            Long familyId, Long viewerUserId, boolean showOthersPrivate, Long walletId, Long categoryId, String type,
            LocalDate fromDate, LocalDate toDate, String notePattern, BigDecimal minAmount, BigDecimal maxAmount);

    /** 1 if transaction {@code targetId} matches these list filters (and the viewer sees it in full), else 0. */
    @Select
    long countFilteredMatchingId(
            Long familyId, Long viewerUserId, Long walletId, Long categoryId, String type,
            LocalDate fromDate, LocalDate toDate, String notePattern, BigDecimal minAmount, BigDecimal maxAmount,
            Long targetId);

    /**
     * How many rows of the filtered list come BEFORE the target in its order (occurred_at DESC, id DESC) —
     * i.e. its 0-based position, from which the page holding it follows.
     */
    @Select
    long countFilteredAhead(
            Long familyId, Long viewerUserId, boolean showOthersPrivate, Long walletId, Long categoryId, String type,
            LocalDate fromDate, LocalDate toDate, String notePattern, BigDecimal minAmount, BigDecimal maxAmount,
            LocalDateTime occurredAt, Long targetId);

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

    /** All deleted transactions, private ones included (the service masks those it shows to others). */
    @Select
    long countDeletedByFamilyId(Long familyId);

    @Select
    List<Transaction> selectDeletedByFamilyIdPaged(Long familyId, int limit, int offset);

    @Update(sqlFile = true)
    int restore(Long id, Long familyId);

    // --- Trash: permanent deletion (TrashService) ---

    @Select
    List<Transaction> selectDeletedByFamilyId(Long familyId);

    /** Every family: rows that have sat in the trash since before {@code cutoff} (TrashRetentionScheduler). */
    @Select
    List<Transaction> selectDeletedBefore(LocalDateTime cutoff);

    /** Unlike countByWalletId, also counts rows in the trash — they still hold the wallet foreign key. */
    @Select
    long countAllByWalletId(Long walletId);

    /** Unlike countByCategoryId, also counts rows in the trash — they still hold the category foreign key. */
    @Select
    long countAllByCategoryId(Long categoryId);
}
