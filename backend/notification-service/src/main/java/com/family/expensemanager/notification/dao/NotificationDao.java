package com.family.expensemanager.notification.dao;

import com.family.expensemanager.notification.domain.entity.Notification;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;

import java.util.List;

@Dao
public interface NotificationDao {

    @Insert
    int insert(Notification notification);

    @Select
    List<Notification> selectByFamilyId(Long familyId);
}
