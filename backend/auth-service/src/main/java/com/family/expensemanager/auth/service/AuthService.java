package com.family.expensemanager.auth.service;

import com.family.expensemanager.auth.dao.FamilyDao;
import com.family.expensemanager.auth.dao.RefreshTokenDao;
import com.family.expensemanager.auth.dao.UserDao;
import com.family.expensemanager.auth.domain.entity.Family;
import com.family.expensemanager.auth.domain.entity.RefreshToken;
import com.family.expensemanager.auth.domain.entity.User;
import com.family.expensemanager.auth.dto.AuthResponse;
import com.family.expensemanager.auth.dto.LoginRequest;
import com.family.expensemanager.auth.dto.RefreshRequest;
import com.family.expensemanager.auth.dto.RegisterRequest;
import com.family.expensemanager.common.exception.ConflictException;
import com.family.expensemanager.common.exception.UnauthorizedException;
import com.family.expensemanager.common.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

@Service
public class AuthService {

    private static final String ROLE_OWNER = "OWNER";

    private final FamilyDao familyDao;
    private final UserDao userDao;
    private final RefreshTokenDao refreshTokenDao;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final long accessTokenTtlMillis;
    private final Duration refreshTokenTtl;

    public AuthService(FamilyDao familyDao,
                        UserDao userDao,
                        RefreshTokenDao refreshTokenDao,
                        PasswordEncoder passwordEncoder,
                        JwtUtil jwtUtil,
                        @Value("${jwt.access-token-ttl-minutes}") long accessTokenTtlMinutes,
                        @Value("${jwt.refresh-token-ttl-days}") long refreshTokenTtlDays) {
        this.familyDao = familyDao;
        this.userDao = userDao;
        this.refreshTokenDao = refreshTokenDao;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.accessTokenTtlMillis = Duration.ofMinutes(accessTokenTtlMinutes).toMillis();
        this.refreshTokenTtl = Duration.ofDays(refreshTokenTtlDays);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        userDao.selectByEmail(request.email()).ifPresent(u -> {
            throw new ConflictException("Email đã được đăng ký: " + request.email());
        });

        Family family = new Family();
        family.setName(request.familyName());
        family.setCreatedAt(LocalDateTime.now());
        familyDao.insert(family);

        User user = new User();
        user.setFamilyId(family.getId());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        user.setRole(ROLE_OWNER);
        user.setActive(true);
        userDao.insert(user);

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userDao.selectByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("Email hoặc mật khẩu không đúng"));

        if (!Boolean.TRUE.equals(user.getActive()) || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Email hoặc mật khẩu không đúng");
        }

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
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

    private AuthResponse issueTokens(User user) {
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
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
