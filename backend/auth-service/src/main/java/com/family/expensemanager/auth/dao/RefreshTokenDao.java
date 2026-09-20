package com.family.expensemanager.auth.dao;

import java.util.List;
import java.util.Optional;

import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;

import com.family.expensemanager.auth.domain.entity.RefreshToken;

@Dao
public interface RefreshTokenDao {

    @Insert
    int insert(RefreshToken refreshToken);

    @Select
    Optional<RefreshToken> selectByTokenHash(String tokenHash);

    /** Active (not revoked, not expired) sessions for a user — backs the "phiên đăng nhập" list. */
    @Select
    List<RefreshToken> selectActiveByUserId(Long userId);

    @Select
    long countActiveByUserId(Long userId);

    /** One page of {@link #selectActiveByUserId}; ties broken by id so paging is stable. */
    @Select
    List<RefreshToken> selectActiveByUserIdPaged(Long userId, int limit, int offset);

    @Update
    int update(RefreshToken refreshToken);

    /** Bulk delete (not tied to one entity's @Id), needed before a USERS row can be removed. */
    @Delete(sqlFile = true)
    int deleteByUserId(Long userId);

    /** Revokes one session by id, scoped to its owner so a user can't revoke someone else's. */
    @Update(sqlFile = true)
    int revokeById(Long id, Long userId);

    /** Revokes every other active session, keeping the one the request came from. */
    @Update(sqlFile = true)
    int revokeAllByUserIdExcept(Long userId, Long exceptId);

    /** Revokes every active session of a user (admin lock, e-mail change). */
    @Update(sqlFile = true)
    int revokeAllByUserId(Long userId);
}
