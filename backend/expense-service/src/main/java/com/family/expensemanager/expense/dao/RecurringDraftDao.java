package com.family.expensemanager.expense.dao;

import com.family.expensemanager.expense.domain.entity.RecurringDraft;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * @author boyquynhluu
 */
@Dao
public interface RecurringDraftDao {

    @Insert
    int insert(RecurringDraft draft);

    @Update
    int update(RecurringDraft draft);

    @Select
    Optional<RecurringDraft> selectById(Long id);

    @Select
    Optional<RecurringDraft> selectByRecurringAndDueDate(Long recurringId, LocalDate dueDate);

    @Select
    long countPendingByFamilyId(Long familyId);

    /** Pending first by due date (oldest first). */
    @Select
    List<RecurringDraft> selectPendingByFamilyIdPaged(Long familyId, int limit, int offset);
}
