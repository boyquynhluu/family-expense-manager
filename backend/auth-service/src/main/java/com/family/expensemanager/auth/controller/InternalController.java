package com.family.expensemanager.auth.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.family.expensemanager.auth.dao.FamilyMembershipDao;
import com.family.expensemanager.auth.dao.UserDao;
import com.family.expensemanager.auth.dto.FamilyMemberContactResponse;
import com.family.expensemanager.auth.domain.entity.User;
import com.family.expensemanager.common.dto.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service-to-service endpoints. The API gateway has no route for {@code /internal/**}, so these are only
 * reachable from inside the Docker network, and each call must also carry a JWT signed with the shared
 * {@code jwt.secret} whose role is {@code SERVICE} (notification-service mints one per call) — a normal
 * user token, whatever its role, is rejected with 403.
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
@Slf4j(topic = "InternalController")
public class InternalController {

    private final FamilyMembershipDao familyMembershipDao;
    private final UserDao userDao;

    /**
     * Who to email for a family-wide event. Uses FAMILY_MEMBERSHIPS (the real member list, since USERS.family_id
     * only tracks each user's currently-active family) and skips accounts that can't receive mail anyway
     * (not yet verified, or locked by an admin).
     */
    @GetMapping("/families/{familyId}/members")
    @PreAuthorize("hasRole('SERVICE')")
    public ApiResponse<List<FamilyMemberContactResponse>> listMembers(@PathVariable Long familyId) {
        log.info("listMembers - start, familyId={}", familyId);
        List<FamilyMemberContactResponse> members = familyMembershipDao.selectByFamilyId(familyId).stream()
                .map(m -> userDao.selectById(m.getUserId()).orElse(null))
                .filter(u -> u != null && Boolean.TRUE.equals(u.getActive()) && !Boolean.TRUE.equals(u.getLocked())
                        && u.getEmail() != null)
                .map((User u) -> new FamilyMemberContactResponse(u.getId(), u.getEmail(), u.getDisplayName()))
                .toList();
        return ApiResponse.ok(members);
    }
}
