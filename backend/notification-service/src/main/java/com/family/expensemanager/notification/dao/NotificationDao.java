package com.family.expensemanager.notification.dao;

import com.family.expensemanager.notification.domain.entity.Notification;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;

import java.util.List;
import java.util.Optional;

@Dao
public interface NotificationDao {

    @Insert
    int insert(Notification notification);

    @Update
    int update(Notification notification);

    @Select
    List<Notification> selectByFamilyId(Long familyId);

    @Select
    long countByFamilyId(Long familyId);

    @Select
    List<Notification> selectByFamilyIdPaged(Long familyId, int limit, int offset);

    @Select
    Optional<Notification> selectById(Long id);

    @Select
    long countUnreadByFamilyId(Long familyId);
}
