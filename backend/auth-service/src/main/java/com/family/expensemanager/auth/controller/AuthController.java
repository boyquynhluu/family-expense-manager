package com.family.expensemanager.auth.controller;

import com.family.expensemanager.auth.dto.AcceptInviteRequest;
import com.family.expensemanager.auth.dto.AuthResponse;
import com.family.expensemanager.auth.dto.ChangePasswordRequest;
import com.family.expensemanager.auth.dto.FamilyMembershipResponse;
import com.family.expensemanager.auth.dto.ForgotPasswordRequest;
import com.family.expensemanager.auth.dto.InviteDetailsResponse;
import com.family.expensemanager.auth.dto.InviteMemberRequest;
import com.family.expensemanager.auth.dto.LoginRequest;
import com.family.expensemanager.auth.dto.LoginResponse;
import com.family.expensemanager.auth.dto.MessageResponse;
import com.family.expensemanager.auth.dto.PendingInviteResponse;
import com.family.expensemanager.auth.dto.RefreshRequest;
import com.family.expensemanager.auth.dto.RegisterRequest;
import com.family.expensemanager.auth.dto.RenameFamilyRequest;
import com.family.expensemanager.auth.dto.ResetPasswordRequest;
import com.family.expensemanager.auth.dto.SessionResponse;
import com.family.expensemanager.auth.dto.SwitchFamilyRequest;
import com.family.expensemanager.auth.dto.TransferOwnershipRequest;
import com.family.expensemanager.auth.dto.TwoFactorCodeRequest;
import com.family.expensemanager.auth.dto.TwoFactorConfirmResponse;
import com.family.expensemanager.auth.dto.TwoFactorDisableRequest;
import com.family.expensemanager.auth.dto.TwoFactorSetupResponse;
import com.family.expensemanager.auth.dto.TwoFactorVerifyLoginRequest;
import com.family.expensemanager.auth.dto.UpdateProfileRequest;
import com.family.expensemanager.auth.dto.UserProfileResponse;
import com.family.expensemanager.auth.security.RequestMetadataUtil;
import com.family.expensemanager.auth.service.AuthService;
import com.family.expensemanager.common.dto.ApiResponse;
import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Base path matches the gateway route predicate {@code Path=/api/auth/**} exactly
 * (that route has no StripPrefix filter), so the same paths work both directly
 * against this service and through the gateway.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j(topic = "AuthController")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ApiResponse<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("register - start, email={}", request.email());
        return ApiResponse.ok(authService.register(request));
    }

    @GetMapping("/verify")
    public ApiResponse<MessageResponse> verify(@RequestParam String token) {
        log.info("verify - start");
        authService.verifyEmail(token);
        return ApiResponse.ok(new MessageResponse("Xác thực email thành công. Bạn có thể đăng nhập."));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        log.info("login - start, email={}", request.email());
        return ApiResponse.ok(authService.login(request,
                RequestMetadataUtil.deviceInfo(httpRequest), RequestMetadataUtil.ipAddress(httpRequest)));
    }

    @PostMapping("/2fa/verify-login")
    public ApiResponse<AuthResponse> verifyTwoFactorLogin(@Valid @RequestBody TwoFactorVerifyLoginRequest request,
                                                            HttpServletRequest httpRequest) {
        log.info("verifyTwoFactorLogin - start");
        return ApiResponse.ok(authService.verifyTwoFactorLogin(request.challengeToken(), request.code(),
                RequestMetadataUtil.deviceInfo(httpRequest), RequestMetadataUtil.ipAddress(httpRequest)));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest httpRequest) {
        log.info("refresh - start");
        return ApiResponse.ok(authService.refresh(request,
                RequestMetadataUtil.deviceInfo(httpRequest), RequestMetadataUtil.ipAddress(httpRequest)));
    }

    @PostMapping("/logout")
    public ApiResponse<MessageResponse> logout() {
        log.info("logout - start");
        authService.logout(CurrentUser.userId(), CurrentUser.sessionId());
        return ApiResponse.ok(new MessageResponse("Đã đăng xuất"));
    }

    @PostMapping("/forgot-password")
    public ApiResponse<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        log.info("forgotPassword - start, email={}", request.email());
        return ApiResponse.ok(authService.forgotPassword(request));
    }

    @PostMapping("/reset-password")
    public ApiResponse<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        log.info("resetPassword - start");
        authService.resetPassword(request);
        return ApiResponse.ok(new MessageResponse("Đặt lại mật khẩu thành công. Bạn có thể đăng nhập."));
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> me() {
        log.info("me - start");
        return ApiResponse.ok(authService.getProfile(CurrentUser.userId()));
    }

    @PutMapping("/me")
    public ApiResponse<UserProfileResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        log.info("updateProfile - start");
        return ApiResponse.ok(authService.updateProfile(CurrentUser.userId(), request));
    }

    @PutMapping("/me/password")
    public ApiResponse<MessageResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        log.info("changePassword - start");
        authService.changePassword(CurrentUser.userId(), request);
        return ApiResponse.ok(new MessageResponse("Đổi mật khẩu thành công."));
    }

    @PostMapping("/2fa/setup")
    public ApiResponse<TwoFactorSetupResponse> setupTwoFactor() {
        log.info("setupTwoFactor - start");
        return ApiResponse.ok(authService.setupTwoFactor(CurrentUser.userId()));
    }

    @PostMapping("/2fa/confirm")
    public ApiResponse<TwoFactorConfirmResponse> confirmTwoFactor(@Valid @RequestBody TwoFactorCodeRequest request) {
        log.info("confirmTwoFactor - start");
        return ApiResponse.ok(authService.confirmTwoFactor(CurrentUser.userId(), request.code()));
    }

    @PostMapping("/2fa/disable")
    public ApiResponse<MessageResponse> disableTwoFactor(@Valid @RequestBody TwoFactorDisableRequest request) {
        log.info("disableTwoFactor - start");
        authService.disableTwoFactor(CurrentUser.userId(), request.password());
        return ApiResponse.ok(new MessageResponse("Đã tắt xác thực 2 lớp"));
    }

    @GetMapping("/family/members")
    public ApiResponse<PageResponse<UserProfileResponse>> familyMembers(@RequestParam(defaultValue = "0") int page,
                                                                        @RequestParam(defaultValue = "5") int size) {
        log.info("familyMembers - start, page={}, size={}", page, size);
        return ApiResponse.ok(authService.getFamilyMembersPaged(CurrentUser.familyId(), page, size));
    }

    @DeleteMapping("/family/members/{userId}")
    public ApiResponse<MessageResponse> removeMember(@PathVariable Long userId) {
        log.info("removeMember - start, userId={}", userId);
        authService.removeMember(CurrentUser.familyId(), CurrentUser.userId(), userId);
        return ApiResponse.ok(new MessageResponse("Đã xoá thành viên khỏi gia đình"));
    }

    @PutMapping("/family")
    public ApiResponse<MessageResponse> renameFamily(@Valid @RequestBody RenameFamilyRequest request) {
        log.info("renameFamily - start");
        return ApiResponse.ok(authService.renameFamily(CurrentUser.familyId(), request));
    }

    @PostMapping("/family/leave")
    public ApiResponse<AuthResponse> leaveFamily(HttpServletRequest httpRequest) {
        log.info("leaveFamily - start");
        return ApiResponse.ok(authService.leaveFamily(CurrentUser.familyId(), CurrentUser.userId(),
                CurrentUser.sessionId(), RequestMetadataUtil.deviceInfo(httpRequest),
                RequestMetadataUtil.ipAddress(httpRequest)));
    }

    @PostMapping("/family/transfer-ownership")
    public ApiResponse<AuthResponse> transferOwnership(@Valid @RequestBody TransferOwnershipRequest request,
                                                        HttpServletRequest httpRequest) {
        log.info("transferOwnership - start, targetUserId={}", request.userId());
        return ApiResponse.ok(authService.transferOwnership(CurrentUser.familyId(), CurrentUser.userId(),
                CurrentUser.sessionId(), request, RequestMetadataUtil.deviceInfo(httpRequest),
                RequestMetadataUtil.ipAddress(httpRequest)));
    }

    @GetMapping("/invites")
    public ApiResponse<PageResponse<PendingInviteResponse>> pendingInvites(@RequestParam(defaultValue = "0") int page,
                                                                           @RequestParam(defaultValue = "5") int size) {
        log.info("pendingInvites - start, page={}, size={}", page, size);
        return ApiResponse.ok(authService.getPendingInvitesPaged(CurrentUser.familyId(), page, size));
    }

    @DeleteMapping("/invites/{id}")
    public ApiResponse<MessageResponse> cancelInvite(@PathVariable Long id) {
        log.info("cancelInvite - start, id={}", id);
        authService.cancelInvite(CurrentUser.familyId(), id);
        return ApiResponse.ok(new MessageResponse("Đã huỷ lời mời"));
    }

    @PostMapping("/invites/{id}/resend")
    public ApiResponse<MessageResponse> resendInvite(@PathVariable Long id) {
        log.info("resendInvite - start, id={}", id);
        return ApiResponse.ok(authService.resendInvite(CurrentUser.familyId(), CurrentUser.userId(), id));
    }

    @GetMapping("/my-families")
    public ApiResponse<List<FamilyMembershipResponse>> myFamilies() {
        log.info("myFamilies - start");
        return ApiResponse.ok(authService.listMyFamilies(CurrentUser.userId()));
    }

    @PostMapping("/switch-family")
    public ApiResponse<AuthResponse> switchFamily(@Valid @RequestBody SwitchFamilyRequest request, HttpServletRequest httpRequest) {
        log.info("switchFamily - start, familyId={}", request.familyId());
        return ApiResponse.ok(authService.switchFamily(CurrentUser.userId(), request.familyId(),
                RequestMetadataUtil.deviceInfo(httpRequest), RequestMetadataUtil.ipAddress(httpRequest)));
    }

    @GetMapping("/sessions")
    public ApiResponse<PageResponse<SessionResponse>> sessions(@RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "5") int size) {
        log.info("sessions - start, page={}, size={}", page, size);
        return ApiResponse.ok(authService.listSessionsPaged(CurrentUser.userId(), CurrentUser.sessionId(), page, size));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<MessageResponse> revokeSession(@PathVariable Long sessionId) {
        log.info("revokeSession - start, sessionId={}", sessionId);
        authService.revokeSession(CurrentUser.userId(), sessionId);
        return ApiResponse.ok(new MessageResponse("Đã đăng xuất phiên này"));
    }

    @PostMapping("/sessions/revoke-others")
    public ApiResponse<MessageResponse> revokeOtherSessions() {
        log.info("revokeOtherSessions - start");
        authService.revokeAllOtherSessions(CurrentUser.userId(), CurrentUser.sessionId());
        return ApiResponse.ok(new MessageResponse("Đã đăng xuất khỏi mọi thiết bị khác"));
    }

    @PostMapping("/invite")
    public ApiResponse<MessageResponse> invite(@Valid @RequestBody InviteMemberRequest request) {
        log.info("invite - start, email={}", request.email());
        return ApiResponse.ok(authService.inviteMember(CurrentUser.familyId(), CurrentUser.userId(), request));
    }

    @GetMapping("/invite/{token}")
    public ApiResponse<InviteDetailsResponse> inviteDetails(@PathVariable String token) {
        log.info("inviteDetails - start");
        return ApiResponse.ok(authService.getInviteDetails(token));
    }

    @PostMapping("/invite/{token}/accept")
    public ApiResponse<MessageResponse> acceptInvite(@PathVariable String token,
                                                       @Valid @RequestBody AcceptInviteRequest request) {
        log.info("acceptInvite - start");
        authService.acceptInvite(token, request);
        return ApiResponse.ok(new MessageResponse("Tham gia gia đình thành công. Bạn có thể đăng nhập."));
    }
}
