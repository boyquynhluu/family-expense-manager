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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    @Mock
    private AuthService authService;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(familyDao, userDao, familyMembershipDao, authService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listFamiliesPaged_includesMemberCountAndOwnerInfo() {
        Family family = family(1L, "Nhà Nguyễn");
        User owner = user(1L, 1L, "OWNER", "owner@b.com", "Chủ hộ");
        when(familyDao.countAll()).thenReturn(1L);
        when(familyDao.selectAllPaged(5, 0)).thenReturn(List.of(family));
        when(familyMembershipDao.selectByFamilyId(1L))
                .thenReturn(List.of(membership(1L, 1L, "OWNER"), membership(2L, 1L, "MEMBER")));
        when(userDao.selectById(1L)).thenReturn(Optional.of(owner));

        var result = adminService.listFamiliesPaged(0, 5);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).memberCount()).isEqualTo(2);
        assertThat(result.content().get(0).ownerEmail()).isEqualTo("owner@b.com");
        assertThat(result.content().get(0).ownerDisplayName()).isEqualTo("Chủ hộ");
        assertThat(result.totalElements()).isEqualTo(1L);
    }

    @Test
    void listFamiliesPaged_returnsPageWithOffset() {
        when(familyDao.countAll()).thenReturn(12L);
        when(familyDao.selectAllPaged(5, 10)).thenReturn(List.of(family(11L, "A"), family(12L, "B")));
        when(familyMembershipDao.selectByFamilyId(anyLong())).thenReturn(List.of());

        var result = adminService.listFamiliesPaged(2, 5);

        assertThat(result.content()).hasSize(2);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(5);
        assertThat(result.totalElements()).isEqualTo(12L);
        assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    void listFamiliesPaged_rejectsInvalidPageOrSize() {
        assertThatThrownBy(() -> adminService.listFamiliesPaged(-1, 5)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> adminService.listFamiliesPaged(0, 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> adminService.listFamiliesPaged(0, 101)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void listUsersPaged_resolvesFamilyNameForEachUser() {
        Family family = family(1L, "Nhà Nguyễn");
        User user = user(1L, 1L, "OWNER", "owner@b.com", "Chủ hộ");
        when(userDao.countAll()).thenReturn(1L);
        when(userDao.selectAllPaged(5, 0)).thenReturn(List.of(user));
        when(familyDao.selectById(1L)).thenReturn(Optional.of(family));

        var result = adminService.listUsersPaged(0, 5);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).familyName()).isEqualTo("Nhà Nguyễn");
        assertThat(result.content().get(0).isSystemAdmin()).isFalse();
        assertThat(result.content().get(0).locked()).isFalse();
        assertThat(result.totalElements()).isEqualTo(1L);
    }

    @Test
    void listUsersPaged_returnsPageWithOffset() {
        when(userDao.countAll()).thenReturn(12L);
        when(userDao.selectAllPaged(5, 10)).thenReturn(List.of(
                user(11L, 1L, "MEMBER", "a@b.com", "A"), user(12L, 1L, "MEMBER", "b@b.com", "B")));
        when(familyDao.selectById(1L)).thenReturn(Optional.of(family(1L, "Nhà Nguyễn")));

        var result = adminService.listUsersPaged(2, 5);

        assertThat(result.content()).hasSize(2);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(12L);
        assertThat(result.totalPages()).isEqualTo(3);
        // Family looked up once for the page, not once per user.
        verify(familyDao, times(1)).selectById(1L);
    }

    @Test
    void listUsersPaged_rejectsInvalidPageOrSize() {
        assertThatThrownBy(() -> adminService.listUsersPaged(-1, 5)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> adminService.listUsersPaged(0, 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> adminService.listUsersPaged(0, 101)).isInstanceOf(BadRequestException.class);
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

    @Test
    void setLocked_locksUser_andRevokesEverySessionImmediately() {
        User target = user(5L, 1L, "MEMBER", "member@b.com", "Thành viên");
        when(userDao.selectById(5L)).thenReturn(Optional.of(target));
        withCurrentUserId(99L);

        adminService.setLocked(5L, true);

        assertThat(target.getLocked()).isTrue();
        assertThat(target.getLockedAt()).isNotNull();
        verify(userDao).update(target);
        verify(authService).revokeAllSessions(5L);
    }

    @Test
    void setLocked_unlocksUser_clearsTimestamp_withoutTouchingSessions() {
        User target = user(5L, 1L, "MEMBER", "member@b.com", "Thành viên");
        target.setLocked(true);
        target.setLockedAt(LocalDateTime.now().minusDays(1));
        when(userDao.selectById(5L)).thenReturn(Optional.of(target));
        withCurrentUserId(99L);

        adminService.setLocked(5L, false);

        assertThat(target.getLocked()).isFalse();
        assertThat(target.getLockedAt()).isNull();
        verify(userDao).update(target);
        verify(authService, never()).revokeAllSessions(any());
    }

    @Test
    void setLocked_throwsBadRequest_whenAdminLocksThemselves() {
        withCurrentUserId(5L);

        assertThatThrownBy(() -> adminService.setLocked(5L, true)).isInstanceOf(BadRequestException.class);
        verify(userDao, never()).update(any());
    }

    @Test
    void setLocked_throwsBadRequest_whenTargetIsAnotherSystemAdmin() {
        User target = user(5L, 1L, "OWNER", "admin2@b.com", "Admin 2");
        target.setIsSystemAdmin(true);
        when(userDao.selectById(5L)).thenReturn(Optional.of(target));
        withCurrentUserId(99L);

        assertThatThrownBy(() -> adminService.setLocked(5L, true)).isInstanceOf(BadRequestException.class);
        verify(userDao, never()).update(any());
        verify(authService, never()).revokeAllSessions(any());
    }

    @Test
    void setLocked_throwsNotFound_whenTargetMissing() {
        when(userDao.selectById(5L)).thenReturn(Optional.empty());
        withCurrentUserId(99L);

        assertThatThrownBy(() -> adminService.setLocked(5L, true)).isInstanceOf(NotFoundException.class);
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
