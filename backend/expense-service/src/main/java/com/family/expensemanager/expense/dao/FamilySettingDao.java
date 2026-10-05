package com.family.expensemanager.expense.dao;

import com.family.expensemanager.expense.domain.entity.FamilySetting;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;

import java.util.Optional;

/**
 * @author boyquynhluu
 */
@Dao
public interface FamilySettingDao {

    @Insert
    int insert(FamilySetting setting);

    @Update
    int update(FamilySetting setting);

    @Select
    Optional<FamilySetting> selectByFamilyId(Long familyId);
}
