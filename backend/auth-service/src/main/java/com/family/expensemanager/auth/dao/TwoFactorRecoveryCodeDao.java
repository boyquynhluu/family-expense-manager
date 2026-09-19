package com.family.expensemanager.auth.dao;

import java.util.List;

import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;

import com.family.expensemanager.auth.domain.entity.TwoFactorRecoveryCode;

@Dao
public interface TwoFactorRecoveryCodeDao {

    @Insert
    int insert(TwoFactorRecoveryCode code);

    @Select
    List<TwoFactorRecoveryCode> selectUnusedByUserId(Long userId);

    @Update
    int update(TwoFactorRecoveryCode code);

    /** Wipes every code (used or not) when 2FA is disabled or a fresh batch is regenerated. */
    @Delete(sqlFile = true)
    int deleteByUserId(Long userId);
}
