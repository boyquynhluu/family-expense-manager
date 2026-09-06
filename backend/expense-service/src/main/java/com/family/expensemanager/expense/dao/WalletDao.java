package com.family.expensemanager.expense.dao;

import com.family.expensemanager.expense.domain.entity.Wallet;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;

import java.util.List;
import java.util.Optional;

@Dao
public interface WalletDao {

    @Insert
    int insert(Wallet wallet);

    @Select
    List<Wallet> selectByFamilyId(Long familyId);

    @Select
    Optional<Wallet> selectById(Long id);
}
