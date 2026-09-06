package com.family.expensemanager.auth.dao;

import com.family.expensemanager.auth.domain.entity.Family;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;

@Dao
public interface FamilyDao {

    @Insert
    int insert(Family family);
}
