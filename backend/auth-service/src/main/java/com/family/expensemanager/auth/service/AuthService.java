package com.family.expensemanager.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.family.expensemanager.auth.dao.FamilyDao;
import com.family.expensemanager.auth.dao.FamilyInviteDao;
import com.family.expensemanager.auth.dao.FamilyMembershipDao;
import com.family.expensemanager.auth.dao.RefreshTokenDao;
import com.family.expensemanager.auth.dao.TwoFactorRecoveryCodeDao;
import com.family.expensemanager.auth.dao.UserDao;
import com.family.expensemanager.auth.domain.entity.Family;
import com.family.expensemanager.auth.domain.entity.FamilyInvite;
import com.family.expensemanager.auth.domain.entity.FamilyMembership;
import com.family.expensemanager.auth.domain.entity.RefreshToken;
import com.family.expensemanager.auth.domain.entity.TwoFactorRecoveryCode;
import com.family.expensemanager.auth.domain.entity.User;
import com.family.expensemanager.auth.dto.AcceptInviteRequest;
import com.family.expensemanager.auth.dto.AuthResponse;
import com.family.expensemanager.auth.dto.ChangeEmailRequest;
import com.family.expensemanager.auth.dto.ChangePasswordRequest;
import com.family.expensemanager.auth.dto.DeleteAccountRequest;
import com.family.expensemanager.auth.dto.FamilyMembershipResponse;
import com.family.expensemanager.auth.dto.ForgotPasswordRequest;
import com.family.expensemanager.auth.dto.InviteDetailsResponse;
import com.family.expensemanager.auth.dto.InviteMemberRequest;
import com.family.expensemanager.auth.dto.LoginRequest;
import com.family.expensemanager.auth.dto.LoginResponse;
import com.family.expensemanager.auth.dto.MessageResponse;
import com.family.expensemanager.auth.dto.PendingInviteResponse;
import com.family.expensemanager.auth.dto.PersonalDataExportResponse;
import com.family.expensemanager.auth.dto.RefreshRequest;
import com.family.expensemanager.auth.dto.RegisterRequest;
import com.family.expensemanager.auth.dto.RenameFamilyRequest;
import com.family.expensemanager.auth.dto.ResendVerificationRequest;
import com.family.expensemanager.auth.dto.ResetPasswordRequest;
import com.family.expensemanager.auth.dto.SessionResponse;
import com.family.expensemanager.auth.dto.TransferOwnershipRequest;
import com.family.expensemanager.auth.dto.TwoFactorConfirmResponse;
import com.family.expensemanager.auth.dto.TwoFactorSetupResponse;
import com.family.expensemanager.auth.dto.UpdateProfileRequest;
import com.family.expensemanager.auth.dto.UserProfileResponse;
import com.family.expensemanager.auth.security.LoginAttemptStore;
import com.family.expensemanager.auth.security.TotpSecretCipher;
import com.family.expensemanager.auth.security.TotpService;
import com.family.expensemanager.auth.security.TwoFactorChallengeStore;
import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.event.FamilyInviteEvent;
import com.family.expensemanager.common.event.FamilyMemberEvent;
import com.family.expensemanager.common.event.NewUserRegisteredEvent;
import com.family.expensemanager.common.event.PasswordResetEvent;
import com.family.expensemanager.common.event.UserVerificationEvent;
import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.ConflictException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.common.exception.UnauthorizedException;
import com.family.expensemanager.common.security.JwtUtil;
import com.family.expensemanager.common.security.RevokedSessionStore;

import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j(topic = "AuthService")
public class AuthService {

    private static final String ROLE_OWNER = "OWNER";
    private static final String ROLE_MEMBER = "MEMBER";
    private static final String PROVIDER_LOCAL = "LOCAL";
    private static final int MAX_PAGE_SIZE = 100;

    private static final int RECOVERY_CODE_COUNT = 8;

    private static final String BAD_CREDENTIALS_MESSAGE = "Email hoặc mật khẩu không đúng";
    /** Frontend detects this text on a failed login to offer "resend verification email" — keep in sync with Login.jsx. */
    public static final String NOT_VERIFIED_MESSAGE = "Tài khoản chưa được xác thực email. Vui lòng kiểm tra hộp thư.";
    private static final String REGISTER_SUCCESS_MESSAGE = "Đăng ký thành công. Vui lòng kiểm tra email để xác thực tài khoản.";
    private static final String RESEND_VERIFICATION_MESSAGE = "Nếu tài khoản đang chờ xác thực, chúng tôi đã gửi lại email xác thực.";
    /** A verification email is not re-sent (and its token not rotated) while the previous one is younger than this. */
    private static final long VERIFICATION_RESEND_COOLDOWN_SECONDS = 60;
    private static final String ACCOUNT_LOCKED_MESSAGE = "Tài khoản đã bị khoá bởi quản trị viên";
    private static final String INVALID_TWO_FACTOR_CODE_MESSAGE = "Mã xác thực không đúng hoặc đã được sử dụng";
    /** Lets the shared /verify link (built by notification-service) tell an e-mail change token from a registration token; base64url never contains '.'. */
    private static final String EMAIL_CHANGE_TOKEN_PREFIX = "ec.";
    public static final String OAUTH2_ACCOUNT_LOCKED_ERROR = "account_locked";

    private final FamilyDao familyDao;
    private final UserDao userDao;
    private final RefreshTokenDao refreshTokenDao;
    private final FamilyInviteDao familyInviteDao;
    private final FamilyMembershipDao familyMembershipDao;
    private final TwoFactorRecoveryCodeDao twoFactorRecoveryCodeDao;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RevokedSessionStore revokedSessionStore;
    private final TotpService totpService;
    private final TwoFactorChallengeStore twoFactorChallengeStore;
    private final LoginAttemptStore loginAttemptStore;
    private final TotpSecretCipher totpSecretCipher;
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
                        FamilyMembershipDao familyMembershipDao,
                        TwoFactorRecoveryCodeDao twoFactorRecoveryCodeDao,
                        PasswordEncoder passwordEncoder,
                        JwtUtil jwtUtil,
                        RevokedSessionStore revokedSessionStore,
                        TotpService totpService,
                        TwoFactorChallengeStore twoFactorChallengeStore,
                        LoginAttemptStore loginAttemptStore,
                        TotpSecretCipher totpSecretCipher,
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
        this.familyMembershipDao = familyMembershipDao;
        this.twoFactorRecoveryCodeDao = twoFactorRecoveryCodeDao;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.revokedSessionStore = revokedSessionStore;
        this.totpService = totpService;
        this.twoFactorChallengeStore = twoFactorChallengeStore;
        this.loginAttemptStore = loginAttemptStore;
        this.totpSecretCipher = totpSecretCipher;
        this.eventPublisher = eventPublisher;
        this.accessTokenTtlMillis = Duration.ofMinutes(accessTokenTtlMinutes).toMillis();
        this.refreshTokenTtl = Duration.ofDays(refreshTokenTtlDays);
        this.verificationTokenTtl = Duration.ofHours(verificationTokenTtlHours);
        this.resetPasswordTokenTtl = Duration.ofHours(resetPasswordTokenTtlHours);
        this.inviteTokenTtl = Duration.ofHours(inviteTokenTtlHours);
    }

    public MessageResponse register(RegisterRequest request) {
        log.info("register - start, email={}", request.email());
        User existing = userDao.selectByEmail(request.email()).orElse(null);
        if (existing != null) {
            if (!isPendingLocalRegistration(existing)) {
                throw new ConflictException("Email đã được đăng ký: " + request.email());
            }
            return reRegisterPending(existing, request);
        }

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
        user.setIsSystemAdmin(Boolean.FALSE);
        user.setTotpEnabled(Boolean.FALSE);
        user.setLocked(Boolean.FALSE);
        user.setVerificationToken(verificationToken);
        user.setVerificationTokenExpiresAt(LocalDateTime.now().plus(verificationTokenTtl));
        userDao.insert(user);
        addMembership(user.getId(), family.getId(), ROLE_OWNER);

        eventPublisher.publishEvent(new UserVerificationEvent(
                user.getId(), user.getEmail(), user.getDisplayName(), verificationToken, Instant.now()));
        publishNewUserRegistered(user, family.getName(), NewUserRegisteredEvent.SOURCE_LOCAL, false);

        return new MessageResponse(REGISTER_SUCCESS_MESSAGE);
    }

    /**
     * Registering again with an email whose account was never verified takes over that pending account:
     * nobody but the mailbox owner can verify it, so the new password/name simply replace the old ones.
     */
    private MessageResponse reRegisterPending(User pending, RegisterRequest request) {
        log.info("register - re-registering over pending account, userId={}", pending.getId());
        pending.setPasswordHash(passwordEncoder.encode(request.password()));
        pending.setDisplayName(request.displayName());
        familyDao.selectById(pending.getFamilyId()).ifPresent(family -> {
            family.setName(request.familyName());
            familyDao.update(family);
        });
        issueVerificationEmail(pending);
        return new MessageResponse(REGISTER_SUCCESS_MESSAGE);
    }

    /** Sends a fresh verification email to an account that registered but never verified; the answer never reveals whether the email exists. */
    public MessageResponse resendVerification(ResendVerificationRequest request) {
        log.info("resendVerification - start, email={}", request.email());
        userDao.selectByEmail(request.email())
                .filter(this::isPendingLocalRegistration)
                .ifPresent(this::issueVerificationEmail);
        return new MessageResponse(RESEND_VERIFICATION_MESSAGE);
    }

    private boolean isPendingLocalRegistration(User user) {
        return !Boolean.TRUE.equals(user.getActive())
                && !Boolean.TRUE.equals(user.getLocked())
                && PROVIDER_LOCAL.equals(user.getProvider())
                && user.getPasswordHash() != null;
    }

    /** Persists any changes on the user and, unless the last email is still fresh, rotates the token and publishes a new verification email. */
    private void issueVerificationEmail(User user) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = user.getVerificationTokenExpiresAt();
        boolean issuedRecently = user.getVerificationToken() != null && expiresAt != null
                && expiresAt.isAfter(now.plus(verificationTokenTtl).minusSeconds(VERIFICATION_RESEND_COOLDOWN_SECONDS));
        if (issuedRecently) {
            userDao.update(user);
            return;
        }
        String verificationToken = generateOpaqueToken();
        user.setVerificationToken(verificationToken);
        user.setVerificationTokenExpiresAt(now.plus(verificationTokenTtl));
        userDao.update(user);
        eventPublisher.publishEvent(new UserVerificationEvent(
                user.getId(), user.getEmail(), user.getDisplayName(), verificationToken, Instant.now()));
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

    public LoginResponse login(LoginRequest request, String deviceInfo, String ipAddress) {
        log.info("login - start, email={}", request.email());
        String email = request.email();
        requireNotLockedOut(email);

        // Unknown email and wrong password share one message and both count toward the lockout.
        User user = userDao.selectByEmail(email).orElse(null);
        if (user == null) {
            loginAttemptStore.recordFailure(email);
            throw new UnauthorizedException(BAD_CREDENTIALS_MESSAGE);
        }

        if (user.getPasswordHash() == null) {
            throw new UnauthorizedException(
                    "Tài khoản này được đăng ký qua " + user.getProvider() + ". Vui lòng đăng nhập bằng phương thức đó.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttemptStore.recordFailure(email);
            throw new UnauthorizedException(BAD_CREDENTIALS_MESSAGE);
        }

        requireNotLocked(user);

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new UnauthorizedException(NOT_VERIFIED_MESSAGE);
        }

        if (Boolean.TRUE.equals(user.getTotpEnabled())) {
            // Password alone isn't enough — hand back a short-lived challenge instead of
            // tokens; the real tokens only get minted once /2fa/verify-login checks the
            // code (see README "9. Không có 2FA"). The failure counter is only cleared
            // after that second step, so the 2FA code can't be brute-forced for free.
            String challengeToken = twoFactorChallengeStore.issueChallenge(user.getId());
            return LoginResponse.ofChallenge(challengeToken);
        }

        loginAttemptStore.reset(email);
        return LoginResponse.ofTokens(issueTokens(user, deviceInfo, ipAddress));
    }

    private void requireNotLockedOut(String email) {
        long minutes = loginAttemptStore.remainingLockMinutes(email);
        if (minutes > 0) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Tài khoản tạm khoá do đăng nhập sai nhiều lần, thử lại sau " + minutes + " phút");
        }
    }

    private static void requireNotLocked(User user) {
        if (Boolean.TRUE.equals(user.getLocked())) {
            throw new ApiException(HttpStatus.FORBIDDEN, ACCOUNT_LOCKED_MESSAGE);
        }
    }

    /** For login paths that authenticate the user without a password (OAuth2) but must still demand the 2FA code. */
    public String issueTwoFactorChallenge(Long userId) {
        return twoFactorChallengeStore.issueChallenge(userId);
    }

    /** Second step of login for a 2FA-enabled account — accepts either a live TOTP code or an unused recovery code. */
    public AuthResponse verifyTwoFactorLogin(String challengeToken, String code, String deviceInfo, String ipAddress) {
        log.info("verifyTwoFactorLogin - start");
        Long userId = twoFactorChallengeStore.consumeChallenge(challengeToken)
                .orElseThrow(() -> new UnauthorizedException("Yêu cầu đăng nhập đã hết hạn, vui lòng đăng nhập lại"));
        User user = userDao.selectById(userId)
                .orElseThrow(() -> new UnauthorizedException("Tài khoản không tồn tại"));

        requireNotLocked(user);
        requireNotLockedOut(user.getEmail());

        if (!isValidTwoFactorCode(user, code)) {
            loginAttemptStore.recordFailure(user.getEmail());
            throw new UnauthorizedException("Mã xác thực không đúng");
        }

        loginAttemptStore.reset(user.getEmail());
        return issueTokens(user, deviceInfo, ipAddress);
    }

    private boolean isValidTwoFactorCode(User user, String code) {
        if (acceptTotpCode(user, code)) {
            userDao.update(user);
            return true;
        }
        return consumeRecoveryCodeIfValid(user.getId(), code);
    }

    /**
     * Checks a TOTP against the decrypted secret and remembers its time step on the (not yet saved)
     * user, so the same code can never be accepted twice. The caller must persist the user.
     */
    private boolean acceptTotpCode(User user, String code) {
        if (user.getTotpSecret() == null) {
            return false;
        }
        String secret = totpSecretCipher.decrypt(user.getTotpSecret());
        Optional<Long> step = totpService.matchStep(secret, code);
        if (step.isEmpty()) {
            return false;
        }
        Long lastStep = user.getTotpLastStep();
        if (lastStep != null && step.get() <= lastStep) {
            return false;
        }
        user.setTotpLastStep(step.get());
        if (!totpSecretCipher.isEncrypted(user.getTotpSecret())) {
            user.setTotpSecret(totpSecretCipher.encrypt(secret));
        }
        return true;
    }

    private boolean consumeRecoveryCodeIfValid(Long userId, String code) {
        for (TwoFactorRecoveryCode recoveryCode : twoFactorRecoveryCodeDao.selectUnusedByUserId(userId)) {
            if (passwordEncoder.matches(code, recoveryCode.getCodeHash())) {
                recoveryCode.setUsedAt(LocalDateTime.now());
                twoFactorRecoveryCodeDao.update(recoveryCode);
                return true;
            }
        }
        return false;
    }

    public AuthResponse refresh(RefreshRequest request, String deviceInfo, String ipAddress) {
        log.info("refresh - start");
        String tokenHash = sha256(request.refreshToken());
        RefreshToken stored = refreshTokenDao.selectByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Refresh token không hợp lệ"));

        if (Boolean.TRUE.equals(stored.getRevoked()) || stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("Refresh token đã hết hạn hoặc bị thu hồi");
        }

        User user = userDao.selectById(stored.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Tài khoản không còn tồn tại"));
        requireNotLocked(user);

        stored.setRevoked(true);
        refreshTokenDao.update(stored);

        return issueTokens(user, deviceInfo, ipAddress);
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
        loginAttemptStore.reset(user.getEmail());
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

    /**
     * Re-authentication for sensitive actions: accounts with a password must send it; provider-only
     * accounts (Google/Facebook, no password) must send a live TOTP, so they need 2FA set up first.
     */
    private void requireReauthentication(User user, String password, String code) {
        if (user.getPasswordHash() != null) {
            if (password == null || password.isBlank()
                    || !passwordEncoder.matches(password, user.getPasswordHash())) {
                throw new UnauthorizedException("Mật khẩu không đúng");
            }
            return;
        }
        if (!Boolean.TRUE.equals(user.getTotpEnabled())) {
            throw new BadRequestException("Tài khoản này đăng nhập qua " + user.getProvider()
                    + " và chưa có mật khẩu. Hãy bật xác thực 2 lớp (2FA) để thực hiện thao tác này.");
        }
        if (code == null || code.isBlank() || !acceptTotpCode(user, code)) {
            throw new UnauthorizedException(INVALID_TWO_FACTOR_CODE_MESSAGE);
        }
        userDao.update(user);
    }

    /** Stores the new address as pending and mails a confirmation link to it; the address only changes once that link is opened. */
    public MessageResponse requestEmailChange(Long userId, ChangeEmailRequest request) {
        log.info("requestEmailChange - start, userId={}", userId);
        User user = userDao.selectById(userId)
                .orElseThrow(() -> new UnauthorizedException("Tài khoản không tồn tại"));
        requireReauthentication(user, request.password(), request.code());

        String newEmail = request.newEmail().trim();
        if (newEmail.equalsIgnoreCase(user.getEmail())) {
            throw new BadRequestException("Email mới trùng với email hiện tại");
        }
        userDao.selectByEmail(newEmail).ifPresent(existing -> {
            throw new ConflictException("Email đã được sử dụng: " + newEmail);
        });

        String token = EMAIL_CHANGE_TOKEN_PREFIX + generateOpaqueToken();
        user.setPendingEmail(newEmail);
        user.setPendingEmailToken(token);
        user.setPendingEmailExpiresAt(LocalDateTime.now().plus(verificationTokenTtl));
        userDao.update(user);

        eventPublisher.publishEvent(new UserVerificationEvent(
                user.getId(), newEmail, user.getDisplayName(), token, Instant.now()));

        return new MessageResponse("Đã gửi link xác nhận đến " + newEmail + ". Email chỉ đổi sau khi bạn mở link đó.");
    }

    public boolean isEmailChangeToken(String token) {
        return token != null && token.startsWith(EMAIL_CHANGE_TOKEN_PREFIX);
    }

    /** Public (the link may be opened without being logged in), so every session of the account is revoked afterwards. */
    public void verifyEmailChange(String token) {
        log.info("verifyEmailChange - start");
        User user = userDao.selectByPendingEmailToken(token)
                .orElseThrow(() -> new BadRequestException("Link đổi email không hợp lệ"));

        if (user.getPendingEmail() == null || user.getPendingEmailExpiresAt() == null
                || user.getPendingEmailExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Link đổi email đã hết hạn");
        }
        String newEmail = user.getPendingEmail();
        userDao.selectByEmail(newEmail)
                .filter(existing -> !existing.getId().equals(user.getId()))
                .ifPresent(existing -> {
                    throw new ConflictException("Email đã được sử dụng: " + newEmail);
                });

        user.setEmail(newEmail);
        user.setPendingEmail(null);
        user.setPendingEmailToken(null);
        user.setPendingEmailExpiresAt(null);
        userDao.update(user);
        revokeAllSessions(user.getId());
    }

    public PersonalDataExportResponse exportPersonalData(Long userId, Long currentSessionId) {
        log.info("exportPersonalData - start, userId={}", userId);
        User user = userDao.selectById(userId)
                .orElseThrow(() -> new UnauthorizedException("Tài khoản không tồn tại"));
        List<SessionResponse> sessions = refreshTokenDao.selectActiveByUserId(userId).stream()
                .map(t -> SessionResponse.from(t, currentSessionId))
                .toList();
        return new PersonalDataExportResponse(UserProfileResponse.from(user), listMyFamilies(userId), sessions);
    }

    /**
     * Auth-side deletion only: expense/notification data of a family emptied by this deletion is left
     * orphaned in those services. An OWNER of a family that still has other members must transfer ownership first.
     */
    public void deleteAccount(Long userId, DeleteAccountRequest request) {
        log.info("deleteAccount - start, userId={}", userId);
        User user = userDao.selectById(userId)
                .orElseThrow(() -> new UnauthorizedException("Tài khoản không tồn tại"));
        requireReauthentication(user, request.password(), request.code());

        List<FamilyMembership> memberships = familyMembershipDao.selectByUserId(userId);
        for (FamilyMembership membership : memberships) {
            if (ROLE_OWNER.equals(membership.getRole())
                    && familyMembershipDao.countByFamilyId(membership.getFamilyId()) > 1) {
                String familyName = familyDao.selectById(membership.getFamilyId())
                        .map(Family::getName)
                        .orElse(String.valueOf(membership.getFamilyId()));
                throw new BadRequestException("Bạn đang là chủ hộ của gia đình \"" + familyName
                        + "\" vẫn còn thành viên khác. Hãy chuyển quyền chủ hộ trước khi xoá tài khoản.");
            }
        }

        removeUserAndOwnedData(user, memberships);
    }

    private void removeUserAndOwnedData(User user, List<FamilyMembership> memberships) {
        Long userId = user.getId();
        revokeAllSessions(userId);
        refreshTokenDao.deleteByUserId(userId);
        twoFactorRecoveryCodeDao.deleteByUserId(userId);
        familyInviteDao.deleteByInvitedByUserId(userId);
        memberships.forEach(familyMembershipDao::delete);
        userDao.delete(user);

        for (FamilyMembership membership : memberships) {
            Long familyId = membership.getFamilyId();
            if (familyMembershipDao.countByFamilyId(familyId) == 0) {
                familyInviteDao.deleteByFamilyId(familyId);
                familyDao.selectById(familyId).ifPresent(familyDao::delete);
            } else {
                publishMemberEvent(FamilyMemberEvent.MEMBER_LEFT, familyId, userId, user.getDisplayName());
            }
        }
    }

    /**
     * Housekeeping for accounts that registered but never verified their email: once the verification
     * token has been expired for {@code retentionDays}, the account and its (empty) family are removed so
     * the email can be registered again and stale rows don't pile up. Returns how many accounts were removed.
     */
    public int purgeStaleUnverifiedAccounts(int retentionDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int purged = 0;
        for (User user : userDao.selectStaleUnverified(cutoff)) {
            List<FamilyMembership> memberships = familyMembershipDao.selectByUserId(user.getId());
            boolean ownsFamilyWithOthers = memberships.stream().anyMatch(m ->
                    ROLE_OWNER.equals(m.getRole()) && familyMembershipDao.countByFamilyId(m.getFamilyId()) > 1);
            if (ownsFamilyWithOthers) {
                log.warn("purgeStaleUnverifiedAccounts - bỏ qua userId={} vì đang là chủ hộ của gia đình có thành viên khác",
                        user.getId());
                continue;
            }
            removeUserAndOwnedData(user, memberships);
            purged++;
        }
        return purged;
    }

    /**
     * Step 1 of enabling 2FA: generates a secret and stores it, but leaves
     * {@code totpEnabled=false} — it only flips on once {@link #confirmTwoFactor} proves
     * the user actually scanned it correctly, so a half-finished setup can never lock
     * anyone out of their own account.
     */
    public TwoFactorSetupResponse setupTwoFactor(Long userId) {
        log.info("setupTwoFactor - start, userId={}", userId);
        User user = userDao.selectById(userId)
                .orElseThrow(() -> new UnauthorizedException("Tài khoản không tồn tại"));
        if (Boolean.TRUE.equals(user.getTotpEnabled())) {
            throw new BadRequestException("2FA đang bật. Hãy tắt 2FA (cần nhập mật khẩu) trước khi thiết lập lại");
        }

        String secret = totpService.generateSecret();
        user.setTotpSecret(totpSecretCipher.encrypt(secret));
        user.setTotpLastStep(null);
        user.setTotpEnabled(false);
        userDao.update(user);

        return new TwoFactorSetupResponse(secret, totpService.buildOtpAuthUri(secret, user.getEmail()));
    }

    /** Step 2: proves the secret from setupTwoFactor works, turns 2FA on, and hands out one-time recovery codes. */
    public TwoFactorConfirmResponse confirmTwoFactor(Long userId, String code) {
        log.info("confirmTwoFactor - start, userId={}", userId);
        User user = userDao.selectById(userId)
                .orElseThrow(() -> new UnauthorizedException("Tài khoản không tồn tại"));
        if (user.getTotpSecret() == null) {
            throw new BadRequestException("Chưa bắt đầu thiết lập 2FA");
        }
        if (!acceptTotpCode(user, code)) {
            throw new BadRequestException("Mã xác thực không đúng");
        }

        user.setTotpEnabled(true);
        userDao.update(user);

        twoFactorRecoveryCodeDao.deleteByUserId(userId);
        List<String> recoveryCodes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String rawCode = generateRecoveryCode();
            recoveryCodes.add(rawCode);
            TwoFactorRecoveryCode entity = new TwoFactorRecoveryCode();
            entity.setUserId(userId);
            entity.setCodeHash(passwordEncoder.encode(rawCode));
            entity.setCreatedAt(LocalDateTime.now());
            twoFactorRecoveryCodeDao.insert(entity);
        }
        return new TwoFactorConfirmResponse(recoveryCodes);
    }

    /**
     * Exactly one of {@code password} / {@code code}: accounts with a password confirm with it,
     * provider-only accounts (no password) confirm with a live TOTP or an unused recovery code.
     */
    public void disableTwoFactor(Long userId, String password, String code) {
        log.info("disableTwoFactor - start, userId={}", userId);
        User user = userDao.selectById(userId)
                .orElseThrow(() -> new UnauthorizedException("Tài khoản không tồn tại"));

        boolean hasPassword = password != null && !password.isBlank();
        boolean hasCode = code != null && !code.isBlank();
        if (hasPassword == hasCode) {
            throw new BadRequestException("Vui lòng nhập mật khẩu hoặc mã xác thực (chỉ một trong hai)");
        }

        if (hasPassword) {
            if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
                throw new UnauthorizedException("Mật khẩu không đúng");
            }
        } else {
            if (user.getPasswordHash() != null) {
                throw new BadRequestException("Tài khoản có mật khẩu, vui lòng nhập mật khẩu để tắt 2FA");
            }
            if (!isValidTwoFactorCode(user, code)) {
                throw new UnauthorizedException(INVALID_TWO_FACTOR_CODE_MESSAGE);
            }
        }

        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        user.setTotpLastStep(null);
        userDao.update(user);
        twoFactorRecoveryCodeDao.deleteByUserId(userId);
    }

    private String generateRecoveryCode() {
        byte[] bytes = new byte[6];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** The real member list for this family — unlike USERS.family_id, which only tracks each user's currently-active one. */
    public PageResponse<UserProfileResponse> getFamilyMembersPaged(Long familyId, int page, int size) {
        log.info("getFamilyMembersPaged - start, familyId={}, page={}, size={}", familyId, page, size);
        validatePage(page, size);
        long totalElements = familyMembershipDao.countByFamilyId(familyId);
        // User lookup only runs for this page's memberships.
        List<UserProfileResponse> content = familyMembershipDao.selectByFamilyIdPaged(familyId, size, page * size).stream()
                .map(m -> userDao.selectById(m.getUserId())
                        .map(u -> UserProfileResponse.from(u, familyId, m.getRole()))
                        .orElse(null))
                .filter(Objects::nonNull)
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

    @PreAuthorize("hasRole('OWNER')")
    public void removeMember(Long familyId, Long callerUserId, Long targetUserId) {
        log.info("removeMember - start, familyId={}, targetUserId={}", familyId, targetUserId);
        if (targetUserId.equals(callerUserId)) {
            throw new BadRequestException("Không thể tự xoá chính mình khỏi gia đình");
        }
        FamilyMembership membership = familyMembershipDao.selectByUserIdAndFamilyId(targetUserId, familyId)
                .orElseThrow(() -> new NotFoundException("Thành viên không tồn tại: " + targetUserId));
        if (ROLE_OWNER.equals(membership.getRole())) {
            throw new BadRequestException("Không thể xoá chủ hộ khỏi gia đình");
        }
        familyMembershipDao.delete(membership);

        User target = userDao.selectById(targetUserId)
                .orElseThrow(() -> new NotFoundException("Thành viên không tồn tại: " + targetUserId));
        List<FamilyMembership> remaining = familyMembershipDao.selectByUserId(targetUserId);
        if (remaining.isEmpty()) {
            // No families left at all — an account with none is meaningless here, so
            // remove it entirely (matches this app's pre-multi-family behavior).
            refreshTokenDao.deleteByUserId(targetUserId);
            userDao.delete(target);
        } else if (target.getFamilyId().equals(familyId)) {
            // Their currently-active family was the one they just lost — fall back to
            // another membership so USERS.family_id/role (what every JWT is minted
            // from) still points somewhere valid. Their next /refresh call picks this
            // up automatically since issueTokens() reads the row fresh.
            FamilyMembership fallback = remaining.get(0);
            target.setFamilyId(fallback.getFamilyId());
            target.setRole(fallback.getRole());
            userDao.update(target);
        }
        publishMemberEvent(FamilyMemberEvent.MEMBER_REMOVED, familyId, targetUserId, target.getDisplayName());
    }

    private void publishMemberEvent(String eventType, Long familyId, Long memberUserId, String memberDisplayName) {
        eventPublisher.publishEvent(new FamilyMemberEvent(
                eventType, familyId, memberUserId, memberDisplayName, Instant.now()));
    }

    @PreAuthorize("hasRole('OWNER')")
    public MessageResponse renameFamily(Long familyId, RenameFamilyRequest request) {
        log.info("renameFamily - start, familyId={}", familyId);
        Family family = familyDao.selectById(familyId)
                .orElseThrow(() -> new NotFoundException("Gia đình không tồn tại: " + familyId));
        family.setName(request.name().trim());
        familyDao.update(family);
        return new MessageResponse("Đã đổi tên gia đình");
    }

    /**
     * Returns fresh tokens because the caller's active family (and so the JWT's
     * familyId/role claims) changes; the session that made this call is revoked so
     * its old token can't keep acting on the family they just left.
     */
    public AuthResponse leaveFamily(Long familyId, Long userId, Long currentSessionId,
                                     String deviceInfo, String ipAddress) {
        log.info("leaveFamily - start, familyId={}, userId={}", familyId, userId);
        FamilyMembership membership = familyMembershipDao.selectByUserIdAndFamilyId(userId, familyId)
                .orElseThrow(() -> new NotFoundException("Bạn không thuộc gia đình này"));
        if (ROLE_OWNER.equals(membership.getRole())) {
            throw new BadRequestException(
                    "Chủ hộ không thể rời gia đình. Hãy chuyển quyền chủ hộ cho thành viên khác trước.");
        }
        User user = userDao.selectById(userId)
                .orElseThrow(() -> new UnauthorizedException("Tài khoản không tồn tại"));
        familyMembershipDao.delete(membership);

        List<FamilyMembership> remaining = familyMembershipDao.selectByUserId(userId);
        if (!remaining.isEmpty()) {
            FamilyMembership fallback = remaining.get(0);
            user.setFamilyId(fallback.getFamilyId());
            user.setRole(fallback.getRole());
        } else {
            // Every account needs at least one family to be active in, or it can't log in usefully.
            Family personal = new Family();
            personal.setName(user.getDisplayName() + "'s Family");
            personal.setCreatedAt(LocalDateTime.now());
            familyDao.insert(personal);
            addMembership(userId, personal.getId(), ROLE_OWNER);
            user.setFamilyId(personal.getId());
            user.setRole(ROLE_OWNER);
        }
        userDao.update(user);
        publishMemberEvent(FamilyMemberEvent.MEMBER_LEFT, familyId, userId, user.getDisplayName());

        logout(userId, currentSessionId);
        return issueTokens(user, deviceInfo, ipAddress);
    }

    /**
     * Hands the OWNER role to another existing member. FAMILY_MEMBERSHIPS is the source
     * of truth; USERS.role is mirrored only for users whose currently-active family is
     * this one (it tracks the active family's role, see {@link #switchFamily}). Swapping
     * both roles in one step means the family never has zero OWNERs.
     */
    @PreAuthorize("hasRole('OWNER')")
    public AuthResponse transferOwnership(Long familyId, Long callerUserId, Long currentSessionId,
                                           TransferOwnershipRequest request, String deviceInfo, String ipAddress) {
        Long targetUserId = request.userId();
        log.info("transferOwnership - start, familyId={}, targetUserId={}", familyId, targetUserId);
        if (targetUserId.equals(callerUserId)) {
            throw new BadRequestException("Bạn đã là chủ hộ của gia đình này");
        }
        FamilyMembership callerMembership = familyMembershipDao.selectByUserIdAndFamilyId(callerUserId, familyId)
                .orElseThrow(() -> new NotFoundException("Bạn không thuộc gia đình này"));
        FamilyMembership targetMembership = familyMembershipDao.selectByUserIdAndFamilyId(targetUserId, familyId)
                .orElseThrow(() -> new NotFoundException("Thành viên không tồn tại: " + targetUserId));
        User caller = userDao.selectById(callerUserId)
                .orElseThrow(() -> new UnauthorizedException("Tài khoản không tồn tại"));
        User target = userDao.selectById(targetUserId)
                .orElseThrow(() -> new NotFoundException("Thành viên không tồn tại: " + targetUserId));

        targetMembership.setRole(ROLE_OWNER);
        familyMembershipDao.update(targetMembership);
        callerMembership.setRole(ROLE_MEMBER);
        familyMembershipDao.update(callerMembership);

        if (familyId.equals(target.getFamilyId())) {
            target.setRole(ROLE_OWNER);
            userDao.update(target);
        }
        if (familyId.equals(caller.getFamilyId())) {
            caller.setRole(ROLE_MEMBER);
            userDao.update(caller);
        }

        logout(callerUserId, currentSessionId);
        return issueTokens(caller, deviceInfo, ipAddress);
    }

    @PreAuthorize("hasRole('OWNER')")
    public MessageResponse inviteMember(Long familyId, Long inviterUserId, InviteMemberRequest request) {
        log.info("inviteMember - start, familyId={}, email={}", familyId, request.email());
        userDao.selectByEmail(request.email()).ifPresent(existing ->
                familyMembershipDao.selectByUserIdAndFamilyId(existing.getId(), familyId).ifPresent(m -> {
                    throw new ConflictException("Email này đã là thành viên của gia đình: " + request.email());
                }));

        Family family = familyDao.selectById(familyId)
                .orElseThrow(() -> new NotFoundException("Gia đình không tồn tại: " + familyId));
        User inviter = userDao.selectById(inviterUserId)
                .orElseThrow(() -> new UnauthorizedException("Tài khoản không tồn tại"));

        FamilyInvite invite = new FamilyInvite();
        invite.setFamilyId(familyId);
        invite.setEmail(request.email());
        invite.setInvitedByUserId(inviterUserId);
        invite.setCreatedAt(LocalDateTime.now());
        renewInviteToken(invite);
        familyInviteDao.insert(invite);

        publishInviteEvent(family, inviter, invite);

        return new MessageResponse("Đã gửi lời mời đến " + request.email());
    }

    private void renewInviteToken(FamilyInvite invite) {
        invite.setToken(generateOpaqueToken());
        invite.setExpiresAt(LocalDateTime.now().plus(inviteTokenTtl));
    }

    private void publishInviteEvent(Family family, User inviter, FamilyInvite invite) {
        eventPublisher.publishEvent(new FamilyInviteEvent(
                family.getId(), family.getName(), invite.getEmail(), inviter.getDisplayName(),
                invite.getToken(), Instant.now()));
    }

    @PreAuthorize("hasRole('OWNER')")
    public PageResponse<PendingInviteResponse> getPendingInvitesPaged(Long familyId, int page, int size) {
        log.info("getPendingInvitesPaged - start, familyId={}, page={}, size={}", familyId, page, size);
        validatePage(page, size);
        long totalElements = familyInviteDao.countPendingByFamilyId(familyId);
        List<PendingInviteResponse> content = familyInviteDao.selectPendingByFamilyIdPaged(familyId, size, page * size)
                .stream()
                .map(PendingInviteResponse::from)
                .toList();
        return PageResponse.of(content, page, size, totalElements);
    }

    @PreAuthorize("hasRole('OWNER')")
    public void cancelInvite(Long familyId, Long inviteId) {
        log.info("cancelInvite - start, familyId={}, inviteId={}", familyId, inviteId);
        FamilyInvite invite = familyInviteDao.selectByIdAndFamilyId(inviteId, familyId)
                .orElseThrow(() -> new NotFoundException("Lời mời không tồn tại: " + inviteId));
        familyInviteDao.delete(invite);
    }

    /** Issues a fresh token (the old link stops working) and extends the expiry, so an expired invite can be revived too. */
    @PreAuthorize("hasRole('OWNER')")
    public MessageResponse resendInvite(Long familyId, Long callerUserId, Long inviteId) {
        log.info("resendInvite - start, familyId={}, inviteId={}", familyId, inviteId);
        FamilyInvite invite = familyInviteDao.selectByIdAndFamilyId(inviteId, familyId)
                .orElseThrow(() -> new NotFoundException("Lời mời không tồn tại: " + inviteId));
        if (invite.getAcceptedAt() != null) {
            throw new BadRequestException("Lời mời này đã được sử dụng");
        }
        Family family = familyDao.selectById(familyId)
                .orElseThrow(() -> new NotFoundException("Gia đình không tồn tại: " + familyId));
        User caller = userDao.selectById(callerUserId)
                .orElseThrow(() -> new UnauthorizedException("Tài khoản không tồn tại"));

        renewInviteToken(invite);
        familyInviteDao.update(invite);
        publishInviteEvent(family, caller, invite);

        return new MessageResponse("Đã gửi lại lời mời đến " + invite.getEmail());
    }

    public InviteDetailsResponse getInviteDetails(String token) {
        log.info("getInviteDetails - start");
        FamilyInvite invite = requireValidInvite(token);
        Family family = familyDao.selectById(invite.getFamilyId())
                .orElseThrow(() -> new NotFoundException("Gia đình không tồn tại: " + invite.getFamilyId()));
        boolean isExistingAccount = userDao.selectByEmail(invite.getEmail()).isPresent();
        return new InviteDetailsResponse(invite.getEmail(), family.getName(), isExistingAccount);
    }

    /**
     * Two branches depending on whether the invited email already has an account:
     * an existing account just gets a new {@link FamilyMembership} added (they keep
     * whichever family they're currently active in — see README "6. 1 tài khoản chỉ
     * thuộc đúng 1 gia đình"); a brand-new email creates a new {@link User} exactly as
     * before, requiring displayName/password since {@link AcceptInviteRequest} can no
     * longer enforce that with static annotations.
     */
    public void acceptInvite(String token, AcceptInviteRequest request) {
        log.info("acceptInvite - start");
        FamilyInvite invite = requireValidInvite(token);

        User existingUser = userDao.selectByEmail(invite.getEmail()).orElse(null);
        if (existingUser != null) {
            familyMembershipDao.selectByUserIdAndFamilyId(existingUser.getId(), invite.getFamilyId())
                    .ifPresent(m -> {
                        throw new ConflictException("Bạn đã là thành viên của gia đình này");
                    });
            addMembership(existingUser.getId(), invite.getFamilyId(), ROLE_MEMBER);
            invite.setAcceptedAt(LocalDateTime.now());
            familyInviteDao.update(invite);
            publishMemberEvent(FamilyMemberEvent.MEMBER_JOINED, invite.getFamilyId(), existingUser.getId(),
                    existingUser.getDisplayName());
            return;
        }

        if (request.displayName() == null || request.displayName().isBlank()
                || request.password() == null || request.password().length() < 8) {
            throw new BadRequestException(
                    "Cần nhập tên hiển thị và mật khẩu (tối thiểu 8 ký tự) để tạo tài khoản mới");
        }

        User user = new User();
        user.setFamilyId(invite.getFamilyId());
        user.setEmail(invite.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        user.setRole(ROLE_MEMBER);
        user.setActive(true);
        user.setProvider(PROVIDER_LOCAL);
        user.setIsSystemAdmin(Boolean.FALSE);
        user.setTotpEnabled(Boolean.FALSE);
        user.setLocked(Boolean.FALSE);
        userDao.insert(user);
        addMembership(user.getId(), invite.getFamilyId(), ROLE_MEMBER);

        invite.setAcceptedAt(LocalDateTime.now());
        familyInviteDao.update(invite);
        publishMemberEvent(FamilyMemberEvent.MEMBER_JOINED, invite.getFamilyId(), user.getId(), user.getDisplayName());
        publishNewUserRegistered(user, familyNameOf(invite.getFamilyId()), NewUserRegisteredEvent.SOURCE_INVITE, true);
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
            requireNotLockedForOAuth2(existingByProvider);
            return existingByProvider;
        }

        User existingByEmail = userDao.selectByEmail(email).orElse(null);
        if (existingByEmail != null) {
            requireNotLockedForOAuth2(existingByEmail);
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
        user.setIsSystemAdmin(Boolean.FALSE);
        user.setTotpEnabled(Boolean.FALSE);
        user.setLocked(Boolean.FALSE);
        userDao.insert(user);
        addMembership(user.getId(), family.getId(), ROLE_OWNER);
        publishNewUserRegistered(user, family.getName(), NewUserRegisteredEvent.SOURCE_GOOGLE, true);
        return user;
    }

    private void publishNewUserRegistered(User user, String familyName, String source, boolean emailVerified) {
        List<String> adminEmails = userDao.selectSystemAdminEmails();
        if (adminEmails == null || adminEmails.isEmpty()) {
            log.warn("Có tài khoản mới userId={} nhưng chưa có admin hệ thống nào nhận được email thông báo", user.getId());
            return;
        }
        eventPublisher.publishEvent(new NewUserRegisteredEvent(
                user.getId(), user.getEmail(), user.getDisplayName(), familyName, source, emailVerified,
                adminEmails, Instant.now()));
    }

    private String familyNameOf(Long familyId) {
        return familyDao.selectById(familyId).map(Family::getName).orElse("");
    }

    /** Thrown as an OAuth2 error (not an ApiException) because it surfaces inside the login filter, where only the failure handler's redirect can reach the browser. */
    private static void requireNotLockedForOAuth2(User user) {
        if (Boolean.TRUE.equals(user.getLocked())) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(OAUTH2_ACCOUNT_LOCKED_ERROR, ACCOUNT_LOCKED_MESSAGE, null), ACCOUNT_LOCKED_MESSAGE);
        }
    }

    /** Every family this account belongs to — backs the family switcher UI. */
    public List<FamilyMembershipResponse> listMyFamilies(Long userId) {
        log.info("listMyFamilies - start, userId={}", userId);
        User user = userDao.selectById(userId)
                .orElseThrow(() -> new UnauthorizedException("Tài khoản không tồn tại"));
        return familyMembershipDao.selectByUserId(userId).stream()
                .map(m -> {
                    String familyName = familyDao.selectById(m.getFamilyId()).map(Family::getName).orElse("");
                    return new FamilyMembershipResponse(
                            m.getFamilyId(), familyName, m.getRole(), m.getFamilyId().equals(user.getFamilyId()));
                })
                .toList();
    }

    /**
     * Re-points USERS.family_id/role at a different family this account belongs to and
     * mints fresh tokens for it — same idea as switching workspaces on Slack/GitHub.
     * Every other service only ever reads the JWT's single familyId claim per request,
     * so this is the only place that needs to know an account can have more than one.
     */
    @Transactional
    public AuthResponse switchFamily(Long userId, Long targetFamilyId, String deviceInfo, String ipAddress) {
        log.info("switchFamily - start, userId={}, targetFamilyId={}", userId, targetFamilyId);
        FamilyMembership membership = familyMembershipDao.selectByUserIdAndFamilyId(userId, targetFamilyId)
                .orElseThrow(() -> new NotFoundException("Bạn không thuộc gia đình này"));
        User user = userDao.selectById(userId)
                .orElseThrow(() -> new UnauthorizedException("Tài khoản không tồn tại"));
        requireNotLocked(user);
        user.setFamilyId(targetFamilyId);
        user.setRole(membership.getRole());
        userDao.update(user);
        return issueTokens(user, deviceInfo, ipAddress);
    }

    private void addMembership(Long userId, Long familyId, String role) {
        FamilyMembership membership = new FamilyMembership();
        membership.setUserId(userId);
        membership.setFamilyId(familyId);
        membership.setRole(role);
        membership.setCreatedAt(LocalDateTime.now());
        familyMembershipDao.insert(membership);
    }

    public AuthResponse issueTokens(User user) {
        return issueTokens(user, null, null);
    }

    /**
     * The new refresh token row is inserted first (not after minting the JWT, unlike
     * before this method grew a session concept) purely so its generated id exists in
     * time to embed as {@link JwtUtil#CLAIM_SESSION_ID} — that claim is what lets
     * {@code revokeSession}/{@code logout} block this exact access token immediately
     * via {@link RevokedSessionStore}, instead of only preventing future refreshes.
     */
    public AuthResponse issueTokens(User user, String deviceInfo, String ipAddress) {
        log.info("issueTokens - start, userId={}", user.getId());
        requireNotLocked(user);
        LocalDateTime now = LocalDateTime.now();
        String rawRefreshToken = generateOpaqueToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setTokenHash(sha256(rawRefreshToken));
        refreshToken.setExpiresAt(now.plus(refreshTokenTtl));
        refreshToken.setRevoked(false);
        refreshToken.setCreatedAt(now);
        refreshToken.setDeviceInfo(truncate(deviceInfo, 255));
        refreshToken.setIpAddress(truncate(ipAddress, 255));
        refreshToken.setLastUsedAt(now);
        refreshTokenDao.insert(refreshToken);

        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtUtil.CLAIM_FAMILY_ID, user.getFamilyId());
        claims.put(JwtUtil.CLAIM_ROLE, user.getRole());
        claims.put(JwtUtil.CLAIM_DISPLAY_NAME, user.getDisplayName());
        claims.put(JwtUtil.CLAIM_IS_SYSTEM_ADMIN, Boolean.TRUE.equals(user.getIsSystemAdmin()));
        claims.put(JwtUtil.CLAIM_EMAIL, user.getEmail());
        claims.put(JwtUtil.CLAIM_SESSION_ID, refreshToken.getId());
        String accessToken = jwtUtil.generateToken(String.valueOf(user.getId()), claims, accessTokenTtlMillis);

        return new AuthResponse(accessToken, rawRefreshToken, "Bearer");
    }

    /** Every active login session for a user — backs the "phiên đăng nhập" list (README "8"). */
    public PageResponse<SessionResponse> listSessionsPaged(Long userId, Long currentSessionId, int page, int size) {
        log.info("listSessionsPaged - start, userId={}, page={}, size={}", userId, page, size);
        validatePage(page, size);
        long totalElements = refreshTokenDao.countActiveByUserId(userId);
        List<SessionResponse> content = refreshTokenDao.selectActiveByUserIdPaged(userId, size, page * size).stream()
                .map(t -> SessionResponse.from(t, currentSessionId))
                .toList();
        return PageResponse.of(content, page, size, totalElements);
    }

    /** Logs out the session the current access token belongs to, blocking it immediately. */
    public void logout(Long userId, Long sessionId) {
        log.info("logout - start, userId={}, sessionId={}", userId, sessionId);
        if (sessionId == null) {
            return;
        }
        refreshTokenDao.revokeById(sessionId, userId);
        revokedSessionStore.markRevoked(sessionId, accessTokenTtlMillis);
    }

    /** Remotely revokes one other session by id — the "log out this device" action. */
    public void revokeSession(Long userId, Long sessionId) {
        log.info("revokeSession - start, userId={}, sessionId={}", userId, sessionId);
        int updated = refreshTokenDao.revokeById(sessionId, userId);
        if (updated == 0) {
            throw new NotFoundException("Phiên đăng nhập không tồn tại");
        }
        revokedSessionStore.markRevoked(sessionId, accessTokenTtlMillis);
    }

    /** "Log out everywhere else" — revokes every active session except the caller's own. */
    public void revokeAllOtherSessions(Long userId, Long currentSessionId) {
        log.info("revokeAllOtherSessions - start, userId={}, currentSessionId={}", userId, currentSessionId);
        List<RefreshToken> toRevoke = refreshTokenDao.selectActiveByUserId(userId).stream()
                .filter(t -> !t.getId().equals(currentSessionId))
                .toList();
        if (toRevoke.isEmpty()) {
            return;
        }
        refreshTokenDao.revokeAllByUserIdExcept(userId, currentSessionId);
        toRevoke.forEach(t -> revokedSessionStore.markRevoked(t.getId(), accessTokenTtlMillis));
    }

    /** Revokes every active session of the account and blocks their access tokens immediately (admin lock, e-mail change). */
    public void revokeAllSessions(Long userId) {
        log.info("revokeAllSessions - start, userId={}", userId);
        List<RefreshToken> active = refreshTokenDao.selectActiveByUserId(userId);
        if (active.isEmpty()) {
            return;
        }
        refreshTokenDao.revokeAllByUserId(userId);
        active.forEach(t -> revokedSessionStore.markRevoked(t.getId(), accessTokenTtlMillis));
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
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
