package com.family.expensemanager.auth.controller;

import com.family.expensemanager.auth.dto.AcceptInviteRequest;
import com.family.expensemanager.auth.dto.AuthResponse;
import com.family.expensemanager.auth.dto.ChangePasswordRequest;
import com.family.expensemanager.auth.dto.ForgotPasswordRequest;
import com.family.expensemanager.auth.dto.InviteDetailsResponse;
import com.family.expensemanager.auth.dto.InviteMemberRequest;
import com.family.expensemanager.auth.dto.LoginRequest;
import com.family.expensemanager.auth.dto.MessageResponse;
import com.family.expensemanager.auth.dto.RefreshRequest;
import com.family.expensemanager.auth.dto.RegisterRequest;
import com.family.expensemanager.auth.dto.ResetPasswordRequest;
import com.family.expensemanager.auth.dto.UpdateProfileRequest;
import com.family.expensemanager.auth.dto.UserProfileResponse;
import com.family.expensemanager.auth.service.AuthService;
import com.family.expensemanager.common.dto.ApiResponse;
import com.family.expensemanager.common.security.CurrentUser;
import jakarta.validation.Valid;
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
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("login - start, email={}", request.email());
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        log.info("refresh - start");
        return ApiResponse.ok(authService.refresh(request));
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

    @GetMapping("/family/members")
    public ApiResponse<List<UserProfileResponse>> familyMembers() {
        log.info("familyMembers - start");
        return ApiResponse.ok(authService.getFamilyMembers(CurrentUser.familyId()));
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
