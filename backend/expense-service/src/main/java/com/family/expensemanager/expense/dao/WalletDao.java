package com.family.expensemanager.expense.dao;

import com.family.expensemanager.expense.domain.entity.Wallet;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;

import java.util.List;
import java.util.Optional;

@Dao
public interface WalletDao {

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

    @Select
    long countDeletedByFamilyId(Long familyId);

    @Select
    List<Wallet> selectDeletedByFamilyIdPaged(Long familyId, int limit, int offset);

    @Update(sqlFile = true)
    int restore(Long id, Long familyId);
}
