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

    /** README C4: refunds of {@code originalId} that are not in the trash. */
    @Select
    long countActiveRefundsOf(Long originalId);

    /** README C4: every refund of {@code originalId}, trash included (they hold its foreign key). */
    @Select
    long countAllRefundsOf(Long originalId);

    /** README C4: how much of {@code originalId} was refunded so far (a positive number; trash excluded). */
    @Select
    java.math.BigDecimal sumRefundedOf(Long originalId);

    /**
     * README A6: EXPENSE spent in [fromDate, toExclusive) within one budget's scope, read from the
     * TRANSACTION_CATEGORY_LINES view (a split transaction counts per part). Null wallet/user = no restriction;
     * {@code allCategories} = every category, otherwise {@code categoryIds} (a category with its sub-categories,
     * README C6) — Doma refuses a null list, hence the separate flag.
     */
    @Select
    java.math.BigDecimal sumExpenseForBudget(Long familyId, boolean allCategories, List<Long> categoryIds,
                                             Long walletId, Long userId,
                                             java.time.LocalDate fromDate, java.time.LocalDate toExclusive);

    /** README A5: one member's EXPENSE total in [fromDate, toExclusive), leaving {@code excludeId} (an edit) out. */
    @Select
    java.math.BigDecimal sumUserExpenseBetween(Long familyId, Long userId, java.time.LocalDate fromDate,
                                               java.time.LocalDate toExclusive, Long excludeId);

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
            Long tagId, int limit, int offset);

    @Select
    long countByFamilyIdFiltered(
            Long familyId, Long viewerUserId, boolean showOthersPrivate, Long walletId, Long categoryId, String type,
            LocalDate fromDate, LocalDate toDate, String notePattern, BigDecimal minAmount, BigDecimal maxAmount,
            Long tagId);

    /** 1 if transaction {@code targetId} matches these list filters (and the viewer sees it in full), else 0. */
    @Select
    long countFilteredMatchingId(
            Long familyId, Long viewerUserId, Long walletId, Long categoryId, String type,
            LocalDate fromDate, LocalDate toDate, String notePattern, BigDecimal minAmount, BigDecimal maxAmount,
            Long tagId, Long targetId);

    /**
     * How many rows of the filtered list come BEFORE the target in its order (occurred_at DESC, id DESC) —
     * i.e. its 0-based position, from which the page holding it follows.
     */
    @Select
    long countFilteredAhead(
            Long familyId, Long viewerUserId, boolean showOthersPrivate, Long walletId, Long categoryId, String type,
            LocalDate fromDate, LocalDate toDate, String notePattern, BigDecimal minAmount, BigDecimal maxAmount,
            Long tagId, LocalDateTime occurredAt, Long targetId);

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
