package com.family.expensemanager.auth.dao;

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

    @Update
    int update(RefreshToken refreshToken);

    /** Bulk delete (not tied to one entity's @Id), needed before a USERS row can be removed. */
    @Delete(sqlFile = true)
    int deleteByUserId(Long userId);
}
