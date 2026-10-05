package com.family.expensemanager.expense.dao;

import com.family.expensemanager.expense.domain.entity.MemberSpendingLimit;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;

import java.util.List;
import java.util.Optional;

/**
 * @author boyquynhluu
 */
@Dao
public interface MemberSpendingLimitDao {

    @Insert
    int insert(MemberSpendingLimit limit);

    @Update
    int update(MemberSpendingLimit limit);

    @Delete
    int delete(MemberSpendingLimit limit);

    @Select
    Optional<MemberSpendingLimit> selectByFamilyAndUser(Long familyId, Long userId);

    @Select
    List<MemberSpendingLimit> selectByFamilyId(Long familyId);
}
