package com.family.expensemanager.expense.dao;

import com.family.expensemanager.expense.domain.entity.TransferRequest;
import org.seasar.doma.Dao;
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
public interface TransferRequestDao {

    @Insert
    int insert(TransferRequest request);

    @Select
    Optional<TransferRequest> selectById(Long id);

    /** Requests the viewer sent or must decide on — pending ones first, newest first. */
    @Select
    List<TransferRequest> selectVisibleToUserPaged(Long familyId, Long userId, int limit, int offset);

    @Select
    long countVisibleToUser(Long familyId, Long userId);

    @Select
    long countPendingForApprover(Long familyId, Long approverUserId);

    @Select
    long countPendingByRequester(Long familyId, Long requesterUserId);

    /**
     * Moves a PENDING request to {@code status}. Returns 0 when it is no longer pending (decided meanwhile, e.g.
     * a double click), so the caller can refuse instead of deciding twice.
     */
    @Update(sqlFile = true)
    int decide(Long id, String status, Long decidedByUserId, String decidedByName, LocalDateTime decidedAt,
               Long transferId);
}
