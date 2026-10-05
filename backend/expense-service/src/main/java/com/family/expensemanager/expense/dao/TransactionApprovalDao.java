package com.family.expensemanager.expense.dao;

import com.family.expensemanager.expense.domain.entity.TransactionApproval;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;

import java.util.List;
import java.util.Optional;

/**
 * @author boyquynhluu
 */
@Dao
public interface TransactionApprovalDao {

    @Insert
    int insert(TransactionApproval approval);

    @Update
    int update(TransactionApproval approval);

    @Select
    Optional<TransactionApproval> selectById(Long id);

    /** {@code requesterUserId} null = every request of the family (the OWNER's view). */
    @Select
    long countByFamily(Long familyId, Long requesterUserId);

    /** Pending first, then newest. */
    @Select
    List<TransactionApproval> selectByFamilyPaged(Long familyId, Long requesterUserId, int limit, int offset);

    @Select
    long countPendingByFamilyId(Long familyId);
}
