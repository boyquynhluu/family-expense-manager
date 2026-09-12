package com.family.expensemanager.auth.dao;

import java.util.Optional;

import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;

import com.family.expensemanager.auth.domain.entity.FamilyInvite;

@Dao
public interface FamilyInviteDao {

    @Insert
    int insert(FamilyInvite invite);

    @Update
    int update(FamilyInvite invite);

    @Select
    Optional<FamilyInvite> selectByToken(String token);
}
