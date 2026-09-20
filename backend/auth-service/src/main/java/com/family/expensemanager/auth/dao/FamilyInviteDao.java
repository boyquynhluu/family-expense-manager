package com.family.expensemanager.auth.dao;

import java.util.List;
import java.util.Optional;

import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;

import com.family.expensemanager.auth.domain.entity.FamilyInvite;

@Dao
public interface FamilyInviteDao {

    @Insert
    int insert(FamilyInvite invite);

    @Update
    int update(FamilyInvite invite);

    @Delete
    int delete(FamilyInvite invite);

    @Select
    Optional<FamilyInvite> selectByToken(String token);

    @Select
    Optional<FamilyInvite> selectByIdAndFamilyId(Long id, Long familyId);

    /** Invites still usable: not accepted and not expired. */
    @Select
    long countPendingByFamilyId(Long familyId);

    /** One page of pending invites, newest first. */
    @Select
    List<FamilyInvite> selectPendingByFamilyIdPaged(Long familyId, int limit, int offset);

    /** Bulk delete needed before a USERS row can be removed (FAMILY_INVITES.invited_by_user_id is a FK). */
    @Delete(sqlFile = true)
    int deleteByInvitedByUserId(Long userId);

    /** Bulk delete needed before an emptied FAMILIES row can be removed. */
    @Delete(sqlFile = true)
    int deleteByFamilyId(Long familyId);
}
