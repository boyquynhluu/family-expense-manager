package com.family.expensemanager.auth.dao;

import com.family.expensemanager.auth.domain.entity.RefreshToken;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;

import java.util.Optional;

@Dao
public interface RefreshTokenDao {

    @Insert
    int insert(RefreshToken refreshToken);

    @Select
    Optional<RefreshToken> selectByTokenHash(String tokenHash);

    @Update
    int update(RefreshToken refreshToken);
}
