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
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.family.expensemanager.auth.dao.FamilyDao;
import com.family.expensemanager.auth.dao.FamilyInviteDao;
import com.family.expensemanager.auth.dao.RefreshTokenDao;
import com.family.expensemanager.auth.dao.UserDao;
import com.family.expensemanager.auth.domain.entity.Family;
import com.family.expensemanager.auth.domain.entity.FamilyInvite;
import com.family.expensemanager.auth.domain.entity.RefreshToken;
import com.family.expensemanager.auth.domain.entity.User;
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
import com.family.expensemanager.common.event.FamilyInviteEvent;
import com.family.expensemanager.common.event.PasswordResetEvent;
import com.family.expensemanager.common.event.UserVerificationEvent;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.ConflictException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.common.exception.UnauthorizedException;
import com.family.expensemanager.common.security.JwtUtil;

import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j(topic = "AuthService")
public class AuthService {

    private static final String ROLE_OWNER = "OWNER";
    private static final String ROLE_MEMBER = "MEMBER";
    private static final String PROVIDER_LOCAL = "LOCAL";

    private final FamilyDao familyDao;
    private final UserDao userDao;
    private final RefreshTokenDao refreshTokenDao;
    private final FamilyInviteDao familyInviteDao;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final ApplicationEventPublisher eventPublisher;
    private final long accessTokenTtlMillis;
    private final Duration refreshTokenTtl;
    private final Duration verificationTokenTtl;
    private final Duration resetPasswordTokenTtl;
    private final Duration inviteTokenTtl;

    public AuthService(FamilyDao familyDao,
                        UserDao userDao,
                        RefreshTokenDao refreshTokenDao,
                        FamilyInviteDao familyInviteDao,
                        PasswordEncoder passwordEncoder,
                        JwtUtil jwtUtil,
                        ApplicationEventPublisher eventPublisher,
                        @Value("${jwt.access-token-ttl-minutes}") long accessTokenTtlMinutes,
                        @Value("${jwt.refresh-token-ttl-days}") long refreshTokenTtlDays,
                        @Value("${auth.verification-token-ttl-hours}") long verificationTokenTtlHours,
                        @Value("${auth.reset-password-token-ttl-hours}") long resetPasswordTokenTtlHours,
                        @Value("${auth.invite-token-ttl-hours}") long inviteTokenTtlHours) {
        this.familyDao = familyDao;
        this.userDao = userDao;
        this.refreshTokenDao = refreshTokenDao;
        this.familyInviteDao = familyInviteDao;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.eventPublisher = eventPublisher;
        this.accessTokenTtlMillis = Duration.ofMinutes(accessTokenTtlMinutes).toMillis();
        this.refreshTokenTtl = Duration.ofDays(refreshTokenTtlDays);
        this.verificationTokenTtl = Duration.ofHours(verificationTokenTtlHours);
        this.resetPasswordTokenTtl = Duration.ofHours(resetPasswordTokenTtlHours);
        this.inviteTokenTtl = Duration.ofHours(inviteTokenTtlHours);
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
     * Always returns a generic success message, whether or not the email is registered
     * — revealing that would let an attacker enumerate which emails have accounts.
     */
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        log.info("forgotPassword - start, email={}", request.email());
        userDao.selectByEmail(request.email()).ifPresent(user -> {
            String resetToken = generateOpaqueToken();
            user.setResetPasswordToken(resetToken);
            user.setResetPasswordTokenExpiresAt(LocalDateTime.now().plus(resetPasswordTokenTtl));
            userDao.update(user);

            eventPublisher.publishEvent(new PasswordResetEvent(
                    user.getId(), user.getEmail(), user.getDisplayName(), resetToken, Instant.now()));
        });
        return new MessageResponse("Nếu email tồn tại trong hệ thống, chúng tôi đã gửi link đặt lại mật khẩu.");
    }

    public void resetPassword(ResetPasswordRequest request) {
        log.info("resetPassword - start");
        User user = userDao.selectByResetPasswordToken(request.token())
                .orElseThrow(() -> new BadRequestException("Token đặt lại mật khẩu không hợp lệ"));

        if (user.getResetPasswordTokenExpiresAt() == null
                || user.getResetPasswordTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Token đặt lại mật khẩu đã hết hạn");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setResetPasswordToken(null);
        user.setResetPasswordTokenExpiresAt(null);
        userDao.update(user);
    }

    public UserProfileResponse getProfile(Long userId) {
        log.info("getProfile - start, userId={}", userId);
        User user = userDao.selectById(userId)
                .orElseThrow(() -> new UnauthorizedException("Tài khoản không tồn tại"));
        return UserProfileResponse.from(user);
    }

    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        log.info("updateProfile - start, userId={}", userId);
        User user = userDao.selectById(userId)
                .orElseThrow(() -> new UnauthorizedException("Tài khoản không tồn tại"));
        user.setDisplayName(request.displayName());
        user.setRelationship(request.relationship());
        userDao.update(user);
        return UserProfileResponse.from(user);
    }

    public void changePassword(Long userId, ChangePasswordRequest request) {
        log.info("changePassword - start, userId={}", userId);
        User user = userDao.selectById(userId)
                .orElseThrow(() -> new UnauthorizedException("Tài khoản không tồn tại"));

        if (user.getPasswordHash() == null) {
            throw new BadRequestException(
                    "Tài khoản này đăng nhập qua " + user.getProvider() + ", không có mật khẩu để đổi.");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Mật khẩu hiện tại không đúng");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userDao.update(user);
    }

    public List<UserProfileResponse> getFamilyMembers(Long familyId) {
        log.info("getFamilyMembers - start, familyId={}", familyId);
        return userDao.selectByFamilyId(familyId).stream().map(UserProfileResponse::from).toList();
    }

    @Transactional
    @PreAuthorize("hasRole('OWNER')")
    public void removeMember(Long familyId, Long callerUserId, Long targetUserId) {
        log.info("removeMember - start, familyId={}, targetUserId={}", familyId, targetUserId);
        if (targetUserId.equals(callerUserId)) {
            throw new BadRequestException("Không thể tự xoá chính mình khỏi gia đình");
        }
        User target = userDao.selectById(targetUserId)
                .orElseThrow(() -> new NotFoundException("Thành viên không tồn tại: " + targetUserId));
        if (!target.getFamilyId().equals(familyId)) {
            throw new NotFoundException("Thành viên không tồn tại: " + targetUserId);
        }
        if (ROLE_OWNER.equals(target.getRole())) {
            throw new BadRequestException("Không thể xoá chủ hộ khỏi gia đình");
        }
        refreshTokenDao.deleteByUserId(targetUserId);
        userDao.delete(target);
    }

    @PreAuthorize("hasRole('OWNER')")
    public MessageResponse inviteMember(Long familyId, Long inviterUserId, InviteMemberRequest request) {
        log.info("inviteMember - start, familyId={}, email={}", familyId, request.email());
        userDao.selectByEmail(request.email()).ifPresent(u -> {
            throw new ConflictException("Email đã có tài khoản trong hệ thống: " + request.email());
        });

        Family family = familyDao.selectById(familyId)
                .orElseThrow(() -> new NotFoundException("Gia đình không tồn tại: " + familyId));
        User inviter = userDao.selectById(inviterUserId)
                .orElseThrow(() -> new UnauthorizedException("Tài khoản không tồn tại"));

        String token = generateOpaqueToken();
        FamilyInvite invite = new FamilyInvite();
        invite.setFamilyId(familyId);
        invite.setEmail(request.email());
        invite.setToken(token);
        invite.setInvitedByUserId(inviterUserId);
        invite.setExpiresAt(LocalDateTime.now().plus(inviteTokenTtl));
        invite.setCreatedAt(LocalDateTime.now());
        familyInviteDao.insert(invite);

        eventPublisher.publishEvent(new FamilyInviteEvent(
                familyId, family.getName(), request.email(), inviter.getDisplayName(), token, Instant.now()));

        return new MessageResponse("Đã gửi lời mời đến " + request.email());
    }

    public InviteDetailsResponse getInviteDetails(String token) {
        log.info("getInviteDetails - start");
        FamilyInvite invite = requireValidInvite(token);
        Family family = familyDao.selectById(invite.getFamilyId())
                .orElseThrow(() -> new NotFoundException("Gia đình không tồn tại: " + invite.getFamilyId()));
        return new InviteDetailsResponse(invite.getEmail(), family.getName());
    }

    public void acceptInvite(String token, AcceptInviteRequest request) {
        log.info("acceptInvite - start");
        FamilyInvite invite = requireValidInvite(token);

        userDao.selectByEmail(invite.getEmail()).ifPresent(u -> {
            throw new ConflictException("Email đã có tài khoản trong hệ thống: " + invite.getEmail());
        });

        User user = new User();
        user.setFamilyId(invite.getFamilyId());
        user.setEmail(invite.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        user.setRole(ROLE_MEMBER);
        user.setActive(true);
        user.setProvider(PROVIDER_LOCAL);
        userDao.insert(user);

        invite.setAcceptedAt(LocalDateTime.now());
        familyInviteDao.update(invite);
    }

    private FamilyInvite requireValidInvite(String token) {
        FamilyInvite invite = familyInviteDao.selectByToken(token)
                .orElseThrow(() -> new BadRequestException("Lời mời không hợp lệ"));
        if (invite.getAcceptedAt() != null) {
            throw new BadRequestException("Lời mời này đã được sử dụng");
        }
        if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Lời mời đã hết hạn");
        }
        return invite;
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
                JwtUtil.CLAIM_ROLE, user.getRole(),
                JwtUtil.CLAIM_DISPLAY_NAME, user.getDisplayName(),
                JwtUtil.CLAIM_IS_SYSTEM_ADMIN, Boolean.TRUE.equals(user.getIsSystemAdmin()));
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
