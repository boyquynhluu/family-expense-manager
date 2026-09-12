package com.family.expensemanager.auth.dao;

import java.util.List;
import java.util.Optional;

import com.family.expensemanager.auth.domain.entity.Family;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;

@Dao
public interface FamilyDao {

    @Insert
    int insert(Family family);

    @Select
    Optional<Family> selectById(Long id);

    @Select
    List<Family> selectAll();
}
