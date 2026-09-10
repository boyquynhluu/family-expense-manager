package com.family.expensemanager.auth.dao;

import java.util.Optional;

import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;

import com.family.expensemanager.auth.domain.entity.User;

@Dao
public interface UserDao {

    @Insert
    int insert(User user);

    @Update
    int update(User user);

    @Select
    Optional<User> selectByEmail(String email);

    @Select
    Optional<User> selectById(Long id);

    @Select
    Optional<User> selectByVerificationToken(String verificationToken);
}
