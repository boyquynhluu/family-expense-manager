package com.family.expensemanager.expense.dao;

import com.family.expensemanager.expense.domain.entity.Category;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;

import java.util.List;
import java.util.Optional;

@Dao
public interface CategoryDao {

    @Insert
    int insert(Category category);

    @Select
    List<Category> selectByFamilyId(Long familyId);

    @Select
    List<Category> selectByFamilyIdAndType(Long familyId, String type);

    @Select
    Optional<Category> selectById(Long id);
}
