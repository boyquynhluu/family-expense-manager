package com.family.expensemanager.auth.dao;

import java.time.LocalDateTime;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.family.expensemanager.auth.domain.entity.Family;
import com.family.expensemanager.auth.domain.entity.RefreshToken;
import com.family.expensemanager.auth.domain.entity.User;
import com.family.expensemanager.common.doma.AppDomaConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.seasar.doma.jdbc.Config;
import org.seasar.doma.jdbc.JdbcException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * README "11. Chỉ có unit test (mock DAO), không có integration test chạm DB thật" —
 * every other test in this service mocks the DAO layer, so a real schema/entity mismatch
 * (wrong column, a NOT NULL column an entity forgets to set, ...) never surfaces until
 * production. This runs the real Doma DAOs against a real MySQL (Testcontainers) with
 * the actual Flyway migrations applied, no mocks.
 *
 * Excluded from the fast {@code mvn test} unit-test loop (see maven-failsafe-plugin in
 * pom.xml) — runs via {@code mvn verify}, needs a local Docker daemon.
 */
@Testcontainers
class AuthDaoIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("fem_auth_it")
            .withUsername("fem_auth")
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

    private Long insertFamily(FamilyDao familyDao, String name) {
        Family family = new Family();
        family.setName(name);
        family.setCreatedAt(LocalDateTime.now());
        familyDao.insert(family);
        return family.getId();
    }

    @Test
    void userInsert_persistsAllColumns_andRoundTripsCorrectly() {
        FamilyDao familyDao = new FamilyDaoImpl(domaConfig);
        UserDao userDao = new UserDaoImpl(domaConfig);
        Long familyId = insertFamily(familyDao, "Nhà IT Test 1");

        User user = new User();
        user.setFamilyId(familyId);
        user.setEmail("it-user-1@example.com");
        user.setPasswordHash("hashed");
        user.setDisplayName("IT User 1");
        user.setRole("OWNER");
        user.setActive(true);
        user.setProvider("LOCAL");
        user.setIsSystemAdmin(false);
        user.setTotpEnabled(false);
        user.setLocked(false);
        userDao.insert(user);

        assertThat(user.getId()).isNotNull();

        User loaded = userDao.selectById(user.getId()).orElseThrow();
        assertThat(loaded.getEmail()).isEqualTo("it-user-1@example.com");
        assertThat(loaded.getFamilyId()).isEqualTo(familyId);
        assertThat(loaded.getIsSystemAdmin()).isFalse();
        assertThat(loaded.getTotpEnabled()).isFalse();
        assertThat(loaded.getActive()).isTrue();
    }

    /**
     * Regression guard for the exact bug README "11." references: {@code is_system_admin}
     * is {@code NOT NULL DEFAULT FALSE} at the DB level, but Doma's generated INSERT always
     * sends every mapped column explicitly — an unset (null) {@code Boolean} field becomes
     * an explicit SQL {@code NULL}, which collides with {@code NOT NULL} regardless of the
     * column's default (a default only applies when a column is omitted, not when it's
     * explicitly NULL). A mocked {@code UserDao} can never catch this; only a real DB can.
     */
    @Test
    void userInsert_throwsJdbcException_whenIsSystemAdminNeverSet() {
        FamilyDao familyDao = new FamilyDaoImpl(domaConfig);
        UserDao userDao = new UserDaoImpl(domaConfig);
        Long familyId = insertFamily(familyDao, "Nhà IT Test 2");

        User user = new User();
        user.setFamilyId(familyId);
        user.setEmail("it-user-2@example.com");
        user.setPasswordHash("hashed");
        user.setDisplayName("IT User 2");
        user.setRole("OWNER");
        user.setActive(true);
        user.setProvider("LOCAL");
        // isSystemAdmin intentionally left null — every real insert path (AuthService)
        // always sets it explicitly; this proves why that discipline matters.

        assertThatThrownBy(() -> userDao.insert(user)).isInstanceOf(JdbcException.class);
    }

    @Test
    void refreshTokenInsert_persistsSessionMetadata_andRoundTripsCorrectly() {
        FamilyDao familyDao = new FamilyDaoImpl(domaConfig);
        UserDao userDao = new UserDaoImpl(domaConfig);
        RefreshTokenDao refreshTokenDao = new RefreshTokenDaoImpl(domaConfig);
        Long familyId = insertFamily(familyDao, "Nhà IT Test 3");

        User user = new User();
        user.setFamilyId(familyId);
        user.setEmail("it-user-3@example.com");
        user.setPasswordHash("hashed");
        user.setDisplayName("IT User 3");
        user.setRole("OWNER");
        user.setActive(true);
        user.setProvider("LOCAL");
        user.setIsSystemAdmin(false);
        user.setTotpEnabled(false);
        user.setLocked(false);
        userDao.insert(user);

        LocalDateTime now = LocalDateTime.now().withNano(0);
        RefreshToken token = new RefreshToken();
        token.setUserId(user.getId());
        token.setTokenHash("hash-abc");
        token.setExpiresAt(now.plusDays(7));
        token.setRevoked(false);
        token.setCreatedAt(now);
        token.setDeviceInfo("Mozilla/5.0 (integration-test)");
        token.setIpAddress("203.0.113.1");
        token.setLastUsedAt(now);
        refreshTokenDao.insert(token);

        assertThat(token.getId()).isNotNull();

        RefreshToken loaded = refreshTokenDao.selectByTokenHash("hash-abc").orElseThrow();
        assertThat(loaded.getUserId()).isEqualTo(user.getId());
        assertThat(loaded.getDeviceInfo()).isEqualTo("Mozilla/5.0 (integration-test)");
        assertThat(loaded.getIpAddress()).isEqualTo("203.0.113.1");
        assertThat(loaded.getRevoked()).isFalse();

        List<RefreshToken> active = refreshTokenDao.selectActiveByUserId(user.getId());
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getId()).isEqualTo(token.getId());
    }
}
