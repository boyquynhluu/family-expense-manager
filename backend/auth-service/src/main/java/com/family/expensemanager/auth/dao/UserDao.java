package com.family.expensemanager.auth.dao;

import com.family.expensemanager.auth.domain.entity.User;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;

import java.util.Optional;

@Dao
public interface UserDao {

    @Insert
    int insert(User user);

    @Select
    Optional<User> selectByEmail(String email);

    @Select
    Optional<User> selectById(Long id);
}
