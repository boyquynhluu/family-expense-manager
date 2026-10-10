package com.family.expensemanager.expense.dao;

import java.time.LocalDateTime;
import java.util.Optional;

import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;

import com.family.expensemanager.expense.domain.entity.IdempotencyKey;

/**
 * @author boyquynhluu
 */
@Dao
public interface IdempotencyKeyDao {

    @Insert
    int insert(IdempotencyKey key);

    @Update
    int update(IdempotencyKey key);

    @Delete
    int delete(IdempotencyKey key);

    @Select
    Optional<IdempotencyKey> selectByFamilyIdAndScopeAndKey(Long familyId, String scope, String idempotencyKey);

    /** Deletes every key created before {@code cutoff} — see IdempotencyKeyCleanupScheduler. */
    @Delete(sqlFile = true)
    int deleteCreatedBefore(LocalDateTime cutoff);
}
