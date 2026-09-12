package com.family.expensemanager.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.family.expensemanager.auth.dao.FamilyDao;
import com.family.expensemanager.auth.dao.RefreshTokenDao;
import com.family.expensemanager.auth.dao.UserDao;
import com.family.expensemanager.auth.domain.entity.Family;
import com.family.expensemanager.auth.domain.entity.RefreshToken;
import com.family.expensemanager.auth.domain.entity.User;
import com.family.expensemanager.auth.dto.AuthResponse;
import com.family.expensemanager.auth.dto.LoginRequest;
import com.family.expensemanager.auth.dto.MessageResponse;
import com.family.expensemanager.auth.dto.RefreshRequest;
import com.family.expensemanager.auth.dto.RegisterRequest;
import com.family.expensemanager.common.event.UserVerificationEvent;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.ConflictException;
import com.family.expensemanager.common.exception.UnauthorizedException;
import com.family.expensemanager.common.security.JwtUtil;

import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j(topic = "AuthService")
public class AuthService {

    private static final String ROLE_OWNER = "OWNER";
    private static final String PROVIDER_LOCAL = "LOCAL";

    private final FamilyDao familyDao;
    private final UserDao userDao;
    private final RefreshTokenDao refreshTokenDao;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final ApplicationEventPublisher eventPublisher;
    private final long accessTokenTtlMillis;
    private final Duration refreshTokenTtl;
    private final Duration verificationTokenTtl;

    public AuthService(FamilyDao familyDao,
                        UserDao userDao,
                        RefreshTokenDao refreshTokenDao,
                        PasswordEncoder passwordEncoder,
                        JwtUtil jwtUtil,
                        ApplicationEventPublisher eventPublisher,
                        @Value("${jwt.access-token-ttl-minutes}") long accessTokenTtlMinutes,
                        @Value("${jwt.refresh-token-ttl-days}") long refreshTokenTtlDays,
                        @Value("${auth.verification-token-ttl-hours}") long verificationTokenTtlHours) {
        this.familyDao = familyDao;
        this.userDao = userDao;
        this.refreshTokenDao = refreshTokenDao;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.eventPublisher = eventPublisher;
        this.accessTokenTtlMillis = Duration.ofMinutes(accessTokenTtlMinutes).toMillis();
        this.refreshTokenTtl = Duration.ofDays(refreshTokenTtlDays);
        this.verificationTokenTtl = Duration.ofHours(verificationTokenTtlHours);
    }

    public MessageResponse register(RegisterRequest request) {
        log.info("register - start, email={}", request.email());
        userDao.selectByEmail(request.email()).ifPresent(u -> {
            throw new ConflictException("Email đã được đăng ký: " + request.email());
        });

        Family family = new Family();
        family.setName(request.familyName());
        family.setCreatedAt(LocalDateTime.now());
        familyDao.insert(family);

        String verificationToken = generateOpaqueToken();

        User user = new User();
        user.setFamilyId(family.getId());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        user.setRole(ROLE_OWNER);
        user.setActive(false);
        user.setProvider(PROVIDER_LOCAL);
        user.setVerificationToken(verificationToken);
        user.setVerificationTokenExpiresAt(LocalDateTime.now().plus(verificationTokenTtl));
        userDao.insert(user);

        eventPublisher.publishEvent(new UserVerificationEvent(
                user.getId(), user.getEmail(), user.getDisplayName(), verificationToken, Instant.now()));

        return new MessageResponse("Đăng ký thành công. Vui lòng kiểm tra email để xác thực tài khoản.");
    }

    public void verifyEmail(String token) {
        log.info("verifyEmail - start");
        User user = userDao.selectByVerificationToken(token)
                .orElseThrow(() -> new BadRequestException("Token xác thực không hợp lệ"));

        if (user.getVerificationTokenExpiresAt() == null
                || user.getVerificationTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Token xác thực đã hết hạn");
        }

        user.setActive(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiresAt(null);
        userDao.update(user);
    }

    public AuthResponse login(LoginRequest request) {
        log.info("login - start, email={}", request.email());
        User user = userDao.selectByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("Email hoặc mật khẩu không đúng"));

        if (user.getPasswordHash() == null) {
            throw new UnauthorizedException(
                    "Tài khoản này được đăng ký qua " + user.getProvider() + ". Vui lòng đăng nhập bằng phương thức đó.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Email hoặc mật khẩu không đúng");
        }

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new UnauthorizedException("Tài khoản chưa được xác thực email. Vui lòng kiểm tra hộp thư.");
        }

        return issueTokens(user);
    }

    public AuthResponse refresh(RefreshRequest request) {
        log.info("refresh - start");
        String tokenHash = sha256(request.refreshToken());
        RefreshToken stored = refreshTokenDao.selectByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Refresh token không hợp lệ"));

        if (Boolean.TRUE.equals(stored.getRevoked()) || stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("Refresh token đã hết hạn hoặc bị thu hồi");
        }

        User user = userDao.selectById(stored.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Tài khoản không còn tồn tại"));

        stored.setRevoked(true);
        refreshTokenDao.update(stored);

        return issueTokens(user);
    }

    /**
     * Finds or creates the local {@link User} behind an OAuth2 login, called from the
     * provider-specific {@code OAuth2UserService}s during the login flow. A verified
     * social-provider email is trusted the same way our own email verification is, so
     * the account comes back {@code active} immediately.
     */
    public User processOAuth2User(String provider, String providerId, String email, String displayName) {
        log.info("processOAuth2User - start, provider={}, email={}", provider, email);
        if (email == null || email.isBlank()) {
            throw new BadRequestException(
                    "Không lấy được email từ " + provider + ". Vui lòng cấp quyền chia sẻ email.");
        }

        User existingByProvider = userDao.selectByProviderAndProviderId(provider, providerId).orElse(null);
        if (existingByProvider != null) {
            return existingByProvider;
        }

        User existingByEmail = userDao.selectByEmail(email).orElse(null);
        if (existingByEmail != null) {
            // Link this provider to the account already registered with that (verified) email.
            existingByEmail.setProvider(provider);
            existingByEmail.setProviderId(providerId);
            existingByEmail.setActive(true);
            userDao.update(existingByEmail);
            return existingByEmail;
        }

        Family family = new Family();
        family.setName(displayName + "'s Family");
        family.setCreatedAt(LocalDateTime.now());
        familyDao.insert(family);

        User user = new User();
        user.setFamilyId(family.getId());
        user.setEmail(email);
        user.setPasswordHash(null);
        user.setDisplayName(displayName);
        user.setRole(ROLE_OWNER);
        user.setActive(true);
        user.setProvider(provider);
        user.setProviderId(providerId);
        userDao.insert(user);
        return user;
    }

    public AuthResponse issueTokens(User user) {
        log.info("issueTokens - start, userId={}", user.getId());
        Map<String, Object> claims = Map.of(
                JwtUtil.CLAIM_FAMILY_ID, user.getFamilyId(),
                JwtUtil.CLAIM_ROLE, user.getRole());
        String accessToken = jwtUtil.generateToken(String.valueOf(user.getId()), claims, accessTokenTtlMillis);

        String rawRefreshToken = generateOpaqueToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setTokenHash(sha256(rawRefreshToken));
        refreshToken.setExpiresAt(LocalDateTime.now().plus(refreshTokenTtl));
        refreshToken.setRevoked(false);
        refreshTokenDao.insert(refreshToken);

        return new AuthResponse(accessToken, rawRefreshToken, "Bearer");
    }

    private String generateOpaqueToken() {
        log.info("generateOpaqueToken - start");
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        log.info("sha256 - start");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            log.error("Không tạo được SHA-256 MessageDigest", e);
            throw new IllegalStateException(e);
        }
    }
}
