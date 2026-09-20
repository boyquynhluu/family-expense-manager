package com.family.expensemanager.auth.dao;

import java.util.List;
import java.util.Optional;

import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
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

    @Delete
    int delete(User user);

    @Select
    Optional<User> selectByEmail(String email);

    @Select
    List<User> selectByFamilyId(Long familyId);

    @Select
    Optional<User> selectById(Long id);

    @Select
    Optional<User> selectByVerificationToken(String verificationToken);

    @Select
    Optional<User> selectByResetPasswordToken(String resetPasswordToken);

    @Select
    Optional<User> selectByProviderAndProviderId(String provider, String providerId);

    @Select
    long countAll();

    @Select
    List<User> selectAllPaged(int limit, int offset);

    @Select
    long countByFamilyId(Long familyId);
}
