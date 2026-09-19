package com.family.expensemanager.auth.service;

import com.family.expensemanager.auth.dao.FamilyDao;
import com.family.expensemanager.auth.dao.FamilyMembershipDao;
import com.family.expensemanager.auth.dao.UserDao;
import com.family.expensemanager.auth.domain.entity.Family;
import com.family.expensemanager.auth.domain.entity.FamilyMembership;
import com.family.expensemanager.auth.domain.entity.User;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.common.security.CurrentUser;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private FamilyDao familyDao;
    @Mock
    private UserDao userDao;
    @Mock
    private FamilyMembershipDao familyMembershipDao;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(familyDao, userDao, familyMembershipDao);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listFamilies_includesMemberCountAndOwnerInfo() {
        Family family = family(1L, "Nhà Nguyễn");
        User owner = user(1L, 1L, "OWNER", "owner@b.com", "Chủ hộ");
        when(familyDao.selectAll()).thenReturn(List.of(family));
        when(familyMembershipDao.selectByFamilyId(1L))
                .thenReturn(List.of(membership(1L, 1L, "OWNER"), membership(2L, 1L, "MEMBER")));
        when(userDao.selectById(1L)).thenReturn(Optional.of(owner));

        var result = adminService.listFamilies();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).memberCount()).isEqualTo(2);
        assertThat(result.get(0).ownerEmail()).isEqualTo("owner@b.com");
        assertThat(result.get(0).ownerDisplayName()).isEqualTo("Chủ hộ");
    }

    @Test
    void listUsers_resolvesFamilyNameForEachUser() {
        Family family = family(1L, "Nhà Nguyễn");
        User user = user(1L, 1L, "OWNER", "owner@b.com", "Chủ hộ");
        when(familyDao.selectAll()).thenReturn(List.of(family));
        when(userDao.selectAll()).thenReturn(List.of(user));

        var result = adminService.listUsers();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).familyName()).isEqualTo("Nhà Nguyễn");
        assertThat(result.get(0).isSystemAdmin()).isFalse();
    }

    @Test
    void setSystemAdmin_grantsFlag_whenTargetExists() {
        User target = user(5L, 1L, "MEMBER", "member@b.com", "Thành viên");
        when(userDao.selectById(5L)).thenReturn(Optional.of(target));
        withCurrentUserId(99L);

        adminService.setSystemAdmin(5L, true);

        assertThat(target.getIsSystemAdmin()).isTrue();
        verify(userDao).update(target);
    }

    @Test
    void setSystemAdmin_throwsNotFound_whenTargetMissing() {
        when(userDao.selectById(5L)).thenReturn(Optional.empty());
        withCurrentUserId(99L);

        assertThatThrownBy(() -> adminService.setSystemAdmin(5L, true)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void setSystemAdmin_throwsBadRequest_whenAdminRemovesOwnFlag() {
        withCurrentUserId(5L);

        assertThatThrownBy(() -> adminService.setSystemAdmin(5L, false)).isInstanceOf(BadRequestException.class);
    }

    private static Family family(Long id, String name) {
        Family family = new Family();
        family.setId(id);
        family.setName(name);
        family.setCreatedAt(LocalDateTime.now());
        return family;
    }

    private static User user(Long id, Long familyId, String role, String email, String displayName) {
        User user = new User();
        user.setId(id);
        user.setFamilyId(familyId);
        user.setRole(role);
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setActive(true);
        user.setProvider("LOCAL");
        return user;
    }

    private static FamilyMembership membership(Long userId, Long familyId, String role) {
        FamilyMembership membership = new FamilyMembership();
        membership.setUserId(userId);
        membership.setFamilyId(familyId);
        membership.setRole(role);
        return membership;
    }

    /** {@link CurrentUser#userId()} reads from the Spring SecurityContext, so stub it directly. */
    private static void withCurrentUserId(Long userId) {
        var authentication = new UsernamePasswordAuthenticationToken(String.valueOf(userId), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
