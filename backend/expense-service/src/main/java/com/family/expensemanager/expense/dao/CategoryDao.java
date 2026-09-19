package com.family.expensemanager.expense.dao;

import com.family.expensemanager.expense.domain.entity.Category;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;

import java.util.List;
import java.util.Optional;

@Dao
public interface CategoryDao {

    @Insert
    int insert(Category category);

    @Update
    int update(Category category);

    @Delete
    int delete(Category category);

    @Select
    List<Category> selectByFamilyId(Long familyId);

    @Select
    List<Category> selectByFamilyIdAndType(Long familyId, String type);

    @Select
    Optional<Category> selectById(Long id);

    @Select
    List<Category> selectDeletedByFamilyId(Long familyId);

    @Update(sqlFile = true)
    int restore(Long id, Long familyId);
}
