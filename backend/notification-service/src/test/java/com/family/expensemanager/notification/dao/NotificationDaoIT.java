package com.family.expensemanager.notification.dao;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.family.expensemanager.common.doma.AppDomaConfig;
import com.family.expensemanager.notification.domain.entity.Notification;
import com.family.expensemanager.notification.domain.entity.NotificationPreference;
import com.zaxxer.hikari.HikariDataSource;

import org.seasar.doma.jdbc.Config;
import org.seasar.doma.jdbc.JdbcException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * README "11. Chỉ có unit test (mock DAO), không có integration test chạm DB thật" —
 * runs the real NotificationDao against a real MySQL (Testcontainers) with the actual
 * Flyway migration applied, no mocked DAO.
 *
 * Excluded from the fast {@code mvn test} unit-test loop (see maven-failsafe-plugin in
 * pom.xml) — runs via {@code mvn verify}, needs a local Docker daemon.
 */
@Testcontainers
class NotificationDaoIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("fem_notify_it")
            .withUsername("fem_notify")
            .withPassword("test");

    static Config domaConfig;

    @BeforeAll
    static void migrateAndConfigureDoma() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(MYSQL.getJdbcUrl());
        dataSource.setUsername(MYSQL.getUsername());
        dataSource.setPassword(MYSQL.getPassword());
        domaConfig = new AppDomaConfig(dataSource);
    }

    @Test
    void notificationInsert_roundTripsCorrectly_andCountsUnread() {
        NotificationDao notificationDao = new NotificationDaoImpl(domaConfig);
        Long familyId = 300L;

        Notification notification = new Notification();
        notification.setFamilyId(familyId);
        notification.setUserId(1L);
        notification.setType("BUDGET_EXCEEDED");
        notification.setTitle("Vượt ngân sách");
        notification.setMessage("Danh mục Ăn uống đã vượt ngân sách tháng này");
        notification.setPayloadJson("{\"categoryId\":1}");
        notification.setIsRead(false);
        notificationDao.insert(notification);

        assertThat(notification.getId()).isNotNull();

        Notification loaded = notificationDao.selectById(notification.getId()).orElseThrow();
        assertThat(loaded.getTitle()).isEqualTo("Vượt ngân sách");
        assertThat(loaded.getIsRead()).isFalse();
        assertThat(notificationDao.countUnreadByFamilyId(familyId, List.of())).isEqualTo(1);

        loaded.setIsRead(true);
        notificationDao.update(loaded);
        assertThat(notificationDao.countUnreadByFamilyId(familyId, List.of())).isEqualTo(0);
    }

    /**
     * {@code is_read} is a boxed {@code Boolean} with no Java-side default — same risk
     * shape as the historical {@code is_system_admin} bug (README "11."): Doma's generated
     * INSERT sends an explicit SQL NULL for an unset field, which a NOT NULL column
     * rejects regardless of its DEFAULT. Only a real DB round-trip catches this.
     */
    @Test
    void notificationInsert_throwsJdbcException_whenIsReadNeverSet() {
        NotificationDao notificationDao = new NotificationDaoImpl(domaConfig);

        Notification notification = new Notification();
        notification.setFamilyId(301L);
        notification.setUserId(1L);
        notification.setType("BUDGET_EXCEEDED");
        notification.setTitle("Thiếu is_read");
        notification.setMessage("Không set isRead");
        // isRead intentionally left null.

        assertThatThrownBy(() -> notificationDao.insert(notification)).isInstanceOf(JdbcException.class);
    }

    @Test
    void excludedTypes_areFilteredFromListAndCounts_andReadNotificationsCanBeDeleted() {
        NotificationDao notificationDao = new NotificationDaoImpl(domaConfig);
        Long familyId = 302L;
        Notification budget = insertNotification(notificationDao, familyId, "BUDGET_EXCEEDED", false);
        insertNotification(notificationDao, familyId, "MEMBER_JOINED", false);
        Notification alreadyRead = insertNotification(notificationDao, familyId, "MEMBER_LEFT", true);

        List<String> excluded = List.of("MEMBER_JOINED");
        assertThat(notificationDao.countByFamilyId(familyId, List.of())).isEqualTo(3);
        assertThat(notificationDao.countByFamilyId(familyId, excluded)).isEqualTo(2);
        assertThat(notificationDao.countUnreadByFamilyId(familyId, excluded)).isEqualTo(1);
        assertThat(notificationDao.selectByFamilyIdPaged(familyId, excluded, 10, 0))
                .extracting(Notification::getType).containsExactly("MEMBER_LEFT", "BUDGET_EXCEEDED");

        assertThat(notificationDao.deleteReadByFamilyId(familyId)).isEqualTo(1);
        assertThat(notificationDao.selectById(alreadyRead.getId())).isEmpty();
        assertThat(notificationDao.delete(budget)).isEqualTo(1);
        assertThat(notificationDao.countByFamilyId(familyId, List.of())).isEqualTo(1);
    }

    @Test
    void notificationPreference_roundTripsWithCompositeKey() {
        NotificationPreferenceDao preferenceDao = new NotificationPreferenceDaoImpl(domaConfig);

        NotificationPreference preference = new NotificationPreference();
        preference.setUserId(900L);
        preference.setType("MEMBER_JOINED");
        preference.setInAppEnabled(false);
        preference.setEmailEnabled(true);
        preferenceDao.insert(preference);

        NotificationPreference loaded = preferenceDao.selectByUserIdAndType(900L, "MEMBER_JOINED").orElseThrow();
        assertThat(loaded.getInAppEnabled()).isFalse();

        loaded.setInAppEnabled(true);
        loaded.setEmailEnabled(false);
        preferenceDao.update(loaded);

        assertThat(preferenceDao.selectByUserId(900L)).singleElement().satisfies(p -> {
            assertThat(p.getInAppEnabled()).isTrue();
            assertThat(p.getEmailEnabled()).isFalse();
        });
        assertThat(preferenceDao.selectByUserIdAndType(901L, "MEMBER_JOINED")).isEmpty();
    }

    private static Notification insertNotification(NotificationDao dao, Long familyId, String type, boolean isRead) {
        Notification notification = new Notification();
        notification.setFamilyId(familyId);
        notification.setUserId(1L);
        notification.setType(type);
        notification.setTitle(type);
        notification.setMessage(type);
        notification.setIsRead(isRead);
        dao.insert(notification);
        return notification;
    }
}
