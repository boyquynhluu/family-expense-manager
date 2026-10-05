package com.family.expensemanager.expense.dao;

import com.family.expensemanager.expense.domain.entity.Category;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * @author boyquynhluu
 */
@Dao
public interface CategoryDao {

    /** README C6: ids of the direct sub-categories of {@code parentId} (deleted ones too — their history counts). */
    @Select
    List<Long> selectChildIds(Long parentId);

    /** README C6: sub-categories still in use (not deleted) — a parent with any can't be deleted. */
    @Select
    long countActiveChildren(Long parentId);

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
    long countDeletedByFamilyId(Long familyId);

    @Select
    List<Category> selectDeletedByFamilyIdPaged(Long familyId, int limit, int offset);

    @Update(sqlFile = true)
    int restore(Long id, Long familyId);

    // --- Trash: permanent deletion (TrashService) ---

    @Select
    Optional<Category> selectDeletedById(Long id);

    @Select
    List<Category> selectDeletedByFamilyId(Long familyId);

    /** Every family: rows that have sat in the trash since before {@code cutoff} (TrashRetentionScheduler). */
    @Select
    List<Category> selectDeletedBefore(LocalDateTime cutoff);
}
