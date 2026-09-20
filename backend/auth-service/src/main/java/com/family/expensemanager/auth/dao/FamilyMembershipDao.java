package com.family.expensemanager.auth.dao;

import com.family.expensemanager.auth.domain.entity.FamilyMembership;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;

import java.util.List;
import java.util.Optional;

@Dao
public interface FamilyMembershipDao {

    @Insert
    int insert(FamilyMembership membership);

    @Delete
    int delete(FamilyMembership membership);

    @Select
    Optional<FamilyMembership> selectByUserIdAndFamilyId(Long userId, Long familyId);

    /** Every family this user belongs to — backs the family switcher. */
    @Select
    List<FamilyMembership> selectByUserId(Long userId);

    /** Every member of this family — the real member list, unlike USERS.family_id which only tracks each user's currently-active one. */
    @Select
    List<FamilyMembership> selectByFamilyId(Long familyId);

    @Select
    long countByFamilyId(Long familyId);

    /** One page of this family's members, same ordering as {@link #selectByFamilyId}. */
    @Select
    List<FamilyMembership> selectByFamilyIdPaged(Long familyId, int limit, int offset);
}
