package com.family.expensemanager.auth.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.family.expensemanager.auth.dao.FamilyDao;
import com.family.expensemanager.auth.dao.UserDao;
import com.family.expensemanager.auth.domain.entity.Family;
import com.family.expensemanager.auth.domain.entity.User;
import com.family.expensemanager.auth.dto.FamilyAdminResponse;
import com.family.expensemanager.auth.dto.UserAdminResponse;
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

    private final FamilyDao familyDao;
    private final UserDao userDao;

    @PreAuthorize("hasRole('ADMIN')")
    public List<FamilyAdminResponse> listFamilies() {
        log.info("listFamilies - start");
        return familyDao.selectAll().stream().map(this::toFamilyAdminResponse).toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<UserAdminResponse> listUsers() {
        log.info("listUsers - start");
        Map<Long, String> familyNames = familyDao.selectAll().stream()
                .collect(Collectors.toMap(Family::getId, Family::getName));
        return userDao.selectAll().stream()
                .map(u -> UserAdminResponse.from(u, familyNames.get(u.getFamilyId())))
                .toList();
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

    private FamilyAdminResponse toFamilyAdminResponse(Family family) {
        List<User> members = userDao.selectByFamilyId(family.getId());
        User owner = members.stream()
                .filter(u -> ROLE_OWNER.equals(u.getRole()))
                .findFirst()
                .orElse(members.stream().findFirst().orElse(null));
        return new FamilyAdminResponse(
                family.getId(), family.getName(), family.getCreatedAt(), members.size(),
                owner != null ? owner.getEmail() : null,
                owner != null ? owner.getDisplayName() : null);
    }
}
