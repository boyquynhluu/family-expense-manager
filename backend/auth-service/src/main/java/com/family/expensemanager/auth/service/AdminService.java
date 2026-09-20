package com.family.expensemanager.auth.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.family.expensemanager.auth.dao.FamilyDao;
import com.family.expensemanager.auth.dao.FamilyMembershipDao;
import com.family.expensemanager.auth.dao.UserDao;
import com.family.expensemanager.auth.domain.entity.Family;
import com.family.expensemanager.auth.domain.entity.FamilyMembership;
import com.family.expensemanager.auth.domain.entity.User;
import com.family.expensemanager.auth.dto.FamilyAdminResponse;
import com.family.expensemanager.auth.dto.UserAdminResponse;
import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.common.security.CurrentUser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * System-wide operations restricted to {@code isSystemAdmin} users (see
 * USERS.is_system_admin) — an orthogonal, cross-family privilege independent of the
 * per-family OWNER/MEMBER {@code role}. There is no self-service way to become an
 * admin; the first one must be flipped directly in the database.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j(topic = "AdminService")
public class AdminService {

    private static final String ROLE_OWNER = "OWNER";
    private static final int MAX_PAGE_SIZE = 100;

    private final FamilyDao familyDao;
    private final UserDao userDao;
    private final FamilyMembershipDao familyMembershipDao;
    private final AuthService authService;

    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<FamilyAdminResponse> listFamiliesPaged(int page, int size) {
        log.info("listFamiliesPaged - start, page={}, size={}", page, size);
        validatePage(page, size);
        long totalElements = familyDao.countAll();
        // Per-row enrichment (memberships + owner lookup) only runs for this page's rows.
        List<FamilyAdminResponse> content = familyDao.selectAllPaged(size, page * size).stream()
                .map(this::toFamilyAdminResponse)
                .toList();
        return PageResponse.of(content, page, size, totalElements);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<UserAdminResponse> listUsersPaged(int page, int size) {
        log.info("listUsersPaged - start, page={}, size={}", page, size);
        validatePage(page, size);
        long totalElements = userDao.countAll();
        List<User> users = userDao.selectAllPaged(size, page * size);
        // Resolve family names only for the families referenced by this page's users.
        Map<Long, String> familyNames = new HashMap<>();
        Set<Long> familyIds = users.stream()
                .map(User::getFamilyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (Long familyId : familyIds) {
            familyDao.selectById(familyId).ifPresent(f -> familyNames.put(f.getId(), f.getName()));
        }
        List<UserAdminResponse> content = users.stream()
                .map(u -> UserAdminResponse.from(u, familyNames.get(u.getFamilyId())))
                .toList();
        return PageResponse.of(content, page, size, totalElements);
    }

    private static void validatePage(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page phải >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size phải trong khoảng 1-" + MAX_PAGE_SIZE);
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void setSystemAdmin(Long targetUserId, boolean isSystemAdmin) {
        log.info("setSystemAdmin - start, targetUserId={}, isSystemAdmin={}", targetUserId, isSystemAdmin);
        if (!isSystemAdmin && targetUserId.equals(CurrentUser.userId())) {
            throw new BadRequestException("Không thể tự gỡ quyền admin của chính mình");
        }
        User user = userDao.selectById(targetUserId)
                .orElseThrow(() -> new NotFoundException("Tài khoản không tồn tại: " + targetUserId));
        user.setIsSystemAdmin(isSystemAdmin);
        userDao.update(user);
    }

    /** Locking also revokes every session of the target at once, so a locked user is kicked out immediately. */
    @PreAuthorize("hasRole('ADMIN')")
    public void setLocked(Long targetUserId, boolean locked) {
        log.info("setLocked - start, targetUserId={}, locked={}", targetUserId, locked);
        if (locked && targetUserId.equals(CurrentUser.userId())) {
            throw new BadRequestException("Không thể tự khoá tài khoản của chính mình");
        }
        User user = userDao.selectById(targetUserId)
                .orElseThrow(() -> new NotFoundException("Tài khoản không tồn tại: " + targetUserId));
        if (locked && Boolean.TRUE.equals(user.getIsSystemAdmin())) {
            throw new BadRequestException("Không thể khoá tài khoản quản trị hệ thống khác");
        }
        user.setLocked(locked);
        user.setLockedAt(locked ? LocalDateTime.now() : null);
        userDao.update(user);
        if (locked) {
            authService.revokeAllSessions(targetUserId);
        }
    }

    private FamilyAdminResponse toFamilyAdminResponse(Family family) {
        // Membership rows are the real member list — USERS.family_id only tracks each
        // user's currently-active family, which undercounts once accounts can belong
        // to more than one (see README "6. 1 tài khoản chỉ thuộc đúng 1 gia đình").
        List<FamilyMembership> memberships = familyMembershipDao.selectByFamilyId(family.getId());
        FamilyMembership ownerMembership = memberships.stream()
                .filter(m -> ROLE_OWNER.equals(m.getRole()))
                .findFirst()
                .orElse(memberships.stream().findFirst().orElse(null));
        User owner = ownerMembership != null ? userDao.selectById(ownerMembership.getUserId()).orElse(null) : null;
        return new FamilyAdminResponse(
                family.getId(), family.getName(), family.getCreatedAt(), memberships.size(),
                owner != null ? owner.getEmail() : null,
                owner != null ? owner.getDisplayName() : null);
    }
}
