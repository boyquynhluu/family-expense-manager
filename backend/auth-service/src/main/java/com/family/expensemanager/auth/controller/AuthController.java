package com.family.expensemanager.auth.controller;

import com.family.expensemanager.auth.dto.AuthResponse;
import com.family.expensemanager.auth.dto.LoginRequest;
import com.family.expensemanager.auth.dto.MessageResponse;
import com.family.expensemanager.auth.dto.RefreshRequest;
import com.family.expensemanager.auth.dto.RegisterRequest;
import com.family.expensemanager.auth.service.AuthService;
import com.family.expensemanager.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
