package com.family.expensemanager.notification.dao;

import com.family.expensemanager.notification.domain.entity.NotificationPreference;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;

import java.util.List;
import java.util.Optional;

@Dao
public interface NotificationPreferenceDao {

    @Insert
    int insert(NotificationPreference preference);

    @Update
    int update(NotificationPreference preference);

    @Select
    List<NotificationPreference> selectByUserId(Long userId);

    @Select
    Optional<NotificationPreference> selectByUserIdAndType(Long userId, String type);
}
