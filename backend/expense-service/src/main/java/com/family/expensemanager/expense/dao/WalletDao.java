package com.family.expensemanager.expense.dao;

import com.family.expensemanager.expense.domain.entity.Wallet;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * @author boyquynhluu
 */
@Dao
public interface WalletDao {

    /** README C2: every family's (not deleted) credit card with a payment due day — for the due reminder. */
    @Select
    List<Wallet> selectCreditCardsWithDueDay();

    /** README C7: every family that has at least one wallet (i.e. uses the app). */
    @Select
    List<Long> selectActiveFamilyIds();

    @Insert
    int insert(Wallet wallet);

    @Update
    int update(Wallet wallet);

    @Delete
    int delete(Wallet wallet);

    @Select
    List<Wallet> selectByFamilyId(Long familyId);

    @Select
    Optional<Wallet> selectById(Long id);

    /** Same row as {@link #selectById}, locked until the surrounding transaction ends (serialises balance checks). */
    @Select
    Optional<Wallet> selectByIdForUpdate(Long id);

    @Select
    long countDeletedByFamilyId(Long familyId);

    @Select
    List<Wallet> selectDeletedByFamilyIdPaged(Long familyId, int limit, int offset);

    @Update(sqlFile = true)
    int restore(Long id, Long familyId);

    // --- Trash: permanent deletion (TrashService) ---

    @Select
    Optional<Wallet> selectDeletedById(Long id);

    @Select
    List<Wallet> selectDeletedByFamilyId(Long familyId);

    /** Every family: rows that have sat in the trash since before {@code cutoff} (TrashRetentionScheduler). */
    @Select
    List<Wallet> selectDeletedBefore(LocalDateTime cutoff);
}
