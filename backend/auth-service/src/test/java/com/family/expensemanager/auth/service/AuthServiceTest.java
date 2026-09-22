package com.family.expensemanager.auth.service;

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
import com.family.expensemanager.auth.dto.ChangeEmailRequest;
import com.family.expensemanager.auth.dto.ChangePasswordRequest;
import com.family.expensemanager.auth.dto.DeleteAccountRequest;
import com.family.expensemanager.auth.dto.ForgotPasswordRequest;
import com.family.expensemanager.auth.dto.InviteMemberRequest;
import com.family.expensemanager.auth.dto.LoginRequest;
import com.family.expensemanager.auth.dto.RefreshRequest;
import com.family.expensemanager.auth.dto.RegisterRequest;
import com.family.expensemanager.auth.dto.ResendVerificationRequest;
import com.family.expensemanager.auth.dto.RenameFamilyRequest;
import com.family.expensemanager.auth.dto.ResetPasswordRequest;
import com.family.expensemanager.auth.dto.TransferOwnershipRequest;
import com.family.expensemanager.auth.dto.TwoFactorConfirmResponse;
import com.family.expensemanager.auth.dto.TwoFactorSetupResponse;
import com.family.expensemanager.auth.dto.UpdateProfileRequest;
import com.family.expensemanager.auth.security.LoginAttemptStore;
import com.family.expensemanager.auth.security.TotpSecretCipher;
import com.family.expensemanager.auth.security.TotpService;
import com.family.expensemanager.auth.security.TwoFactorChallengeStore;
import com.family.expensemanager.common.message.Messages;
import com.family.expensemanager.common.event.FamilyInviteEvent;
import com.family.expensemanager.common.event.NewUserRegisteredEvent;
import com.family.expensemanager.common.event.FamilyMemberEvent;
import com.family.expensemanager.common.event.PasswordResetEvent;
import com.family.expensemanager.common.event.UserVerificationEvent;
import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.ConflictException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.common.exception.UnauthorizedException;
import com.family.expensemanager.common.security.JwtUtil;
import com.family.expensemanager.common.security.RevokedSessionStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private FamilyDao familyDao;
    @Mock
    private UserDao userDao;
    @Mock
    private RefreshTokenDao refreshTokenDao;
    @Mock
    private FamilyInviteDao familyInviteDao;
    @Mock
    private FamilyMembershipDao familyMembershipDao;
    @Mock
    private TwoFactorRecoveryCodeDao twoFactorRecoveryCodeDao;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private RevokedSessionStore revokedSessionStore;
    @Mock
    private TotpService totpService;
    @Mock
    private TwoFactorChallengeStore twoFactorChallengeStore;
    @Mock
    private LoginAttemptStore loginAttemptStore;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final TotpSecretCipher totpSecretCipher =
            new TotpSecretCipher("ZGV2LW9ubHktdG90cC1lbmNyeXB0aW9uLWtleS0zMmI=");
    // Real bundle (not mocked) so every message assertion below reads the exact production text,
    // the same way common.message.Messages does when Spring wires it from application.yml's
    // spring.messages.basename.
    private final Messages messages = new Messages(authMessageSource());

    private static org.springframework.context.support.ResourceBundleMessageSource authMessageSource() {
        var source = new org.springframework.context.support.ResourceBundleMessageSource();
        source.setBasename("messages/auth-messages");
        source.setDefaultEncoding("UTF-8");
        return source;
    }

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                familyDao, userDao, refreshTokenDao, familyInviteDao, familyMembershipDao, twoFactorRecoveryCodeDao,
                passwordEncoder, jwtUtil, revokedSessionStore, totpService, twoFactorChallengeStore, loginAttemptStore,
                totpSecretCipher, messages, eventPublisher, 15, 7, 24, 1, 72);
    }

    @Test
    void register_createsFamilyAndOwnerUser_whenEmailNotTaken() {
        when(userDao.selectByEmail("a@b.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password1")).thenReturn("hashed");

        var response = authService.register(new RegisterRequest("Nhà Nguyễn", "a@b.com", "password1", "An"));

        assertThat(response.message()).contains("Đăng ký thành công");
        verify(familyDao).insert(any(Family.class));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userDao).insert(userCaptor.capture());
        User inserted = userCaptor.getValue();
        assertThat(inserted.getEmail()).isEqualTo("a@b.com");
        assertThat(inserted.getRole()).isEqualTo("OWNER");
        assertThat(inserted.getActive()).isFalse();
        assertThat(inserted.getPasswordHash()).isEqualTo("hashed");
        assertThat(inserted.getVerificationToken()).isNotBlank();
        assertThat(inserted.getLocked()).isFalse();

        verify(eventPublisher).publishEvent(any(UserVerificationEvent.class));
    }

    @Test
    void register_notifiesSystemAdmins_withUnverifiedLocalSignUp() {
        when(userDao.selectByEmail("a@b.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password1")).thenReturn("hashed");
        when(userDao.selectSystemAdminEmails()).thenReturn(List.of("admin1@x.com", "admin2@x.com"));

        authService.register(new RegisterRequest("Nhà Nguyễn", "a@b.com", "password1", "An"));

        NewUserRegisteredEvent event = capturedNewUserEvent();
        assertThat(event).isNotNull();
        assertThat(event.email()).isEqualTo("a@b.com");
        assertThat(event.displayName()).isEqualTo("An");
        assertThat(event.familyName()).isEqualTo("Nhà Nguyễn");
        assertThat(event.source()).isEqualTo(NewUserRegisteredEvent.SOURCE_LOCAL);
        assertThat(event.emailVerified()).isFalse();
        assertThat(event.adminEmails()).containsExactly("admin1@x.com", "admin2@x.com");
    }

    @Test
    void register_doesNotPublishAdminEvent_whenThereIsNoSystemAdmin() {
        when(userDao.selectByEmail("a@b.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password1")).thenReturn("hashed");

        authService.register(new RegisterRequest("F", "a@b.com", "password1", "An"));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        assertThat(captor.getAllValues()).noneMatch(NewUserRegisteredEvent.class::isInstance);
    }

    @Test
    void processOAuth2User_notifiesSystemAdmins_whenGoogleCreatesBrandNewAccount() {
        when(userDao.selectSystemAdminEmails()).thenReturn(List.of("admin@x.com"));

        authService.processOAuth2User("GOOGLE", "g-1", "new@b.com", "Nam");

        NewUserRegisteredEvent event = capturedNewUserEvent();
        assertThat(event).isNotNull();
        assertThat(event.email()).isEqualTo("new@b.com");
        assertThat(event.source()).isEqualTo(NewUserRegisteredEvent.SOURCE_GOOGLE);
        assertThat(event.emailVerified()).isTrue();
        assertThat(event.familyName()).isEqualTo("Nam's Family");
        assertThat(event.adminEmails()).containsExactly("admin@x.com");
    }

    @Test
    void acceptInvite_notifiesSystemAdmins_whenInviteCreatesNewAccount() {
        FamilyInvite invite = validInvite();
        when(familyInviteDao.selectByToken("tok")).thenReturn(Optional.of(invite));
        when(userDao.selectByEmail("invitee@b.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password1")).thenReturn("hashed");
        Family family = new Family();
        family.setName("Nhà Lan");
        when(familyDao.selectById(invite.getFamilyId())).thenReturn(Optional.of(family));
        when(userDao.selectSystemAdminEmails()).thenReturn(List.of("admin@x.com"));

        authService.acceptInvite("tok", new AcceptInviteRequest("Invitee", "password1"));

        NewUserRegisteredEvent event = capturedNewUserEvent();
        assertThat(event).isNotNull();
        assertThat(event.source()).isEqualTo(NewUserRegisteredEvent.SOURCE_INVITE);
        assertThat(event.familyName()).isEqualTo("Nhà Lan");
        assertThat(event.emailVerified()).isTrue();
    }

    @Test
    void register_throwsConflict_whenEmailAlreadyRegistered() {
        when(userDao.selectByEmail("a@b.com")).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> authService.register(new RegisterRequest("F", "a@b.com", "password1", "An")))
                .isInstanceOf(ConflictException.class);

        verify(familyDao, never()).insert(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void verifyEmail_activatesUser_whenTokenValid() {
        User user = new User();
        user.setVerificationToken("tok");
        user.setVerificationTokenExpiresAt(LocalDateTime.now().plusHours(1));
        when(userDao.selectByVerificationToken("tok")).thenReturn(Optional.of(user));

        authService.verifyEmail("tok");

        assertThat(user.getActive()).isTrue();
        assertThat(user.getVerificationToken()).isNull();
        verify(userDao).update(user);
    }

    @Test
    void verifyEmail_throwsBadRequest_whenTokenExpired() {
        User user = new User();
        user.setVerificationToken("tok");
        user.setVerificationTokenExpiresAt(LocalDateTime.now().minusHours(1));
        when(userDao.selectByVerificationToken("tok")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.verifyEmail("tok")).isInstanceOf(BadRequestException.class);
        verify(userDao, never()).update(any());
    }

    @Test
    void verifyEmail_throwsBadRequest_whenTokenUnknown() {
        when(userDao.selectByVerificationToken("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail("bad")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void login_succeeds_andIssuesTokens_whenCredentialsValidAndActive() {
        User user = activeLocalUser();
        when(userDao.selectByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password1", "hashed")).thenReturn(true);
        when(jwtUtil.generateToken(any(), any(), anyLong())).thenReturn("access-token");

        var response = authService.login(new LoginRequest("a@b.com", "password1"), null, null);

        assertThat(response.requiresTwoFactor()).isFalse();
        assertThat(response.tokens().accessToken()).isEqualTo("access-token");
        assertThat(response.tokens().refreshToken()).isNotBlank();
        verify(refreshTokenDao).insert(any());
        verify(loginAttemptStore).reset("a@b.com");
    }

    @Test
    void login_returnsChallenge_insteadOfTokens_whenTwoFactorEnabled() {
        User user = activeLocalUser();
        user.setTotpEnabled(true);
        when(userDao.selectByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password1", "hashed")).thenReturn(true);
        when(twoFactorChallengeStore.issueChallenge(1L)).thenReturn("challenge-token");

        var response = authService.login(new LoginRequest("a@b.com", "password1"), null, null);

        assertThat(response.requiresTwoFactor()).isTrue();
        assertThat(response.twoFactorToken()).isEqualTo("challenge-token");
        assertThat(response.tokens()).isNull();
        verify(refreshTokenDao, never()).insert(any());
        verify(loginAttemptStore, never()).reset(any());
    }

    @Test
    void login_throwsUnauthorized_whenPasswordWrong() {
        User user = activeLocalUser();
        when(userDao.selectByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("a@b.com", "wrong"), null, null))
                .isInstanceOf(UnauthorizedException.class);
        verify(loginAttemptStore).recordFailure("a@b.com");
    }

    @Test
    void login_throwsUnauthorized_whenAccountNotActive() {
        User user = activeLocalUser();
        user.setActive(false);
        when(userDao.selectByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password1", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("a@b.com", "password1"), null, null))
                .isInstanceOf(UnauthorizedException.class);
        verify(loginAttemptStore, never()).recordFailure(any());
    }

    @Test
    void login_throwsUnauthorized_whenOAuth2OnlyAccount() {
        User user = activeLocalUser();
        user.setPasswordHash(null);
        user.setProvider("GOOGLE");
        when(userDao.selectByEmail("a@b.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("a@b.com", "password1"), null, null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void login_throwsUnauthorized_whenEmailUnknown() {
        when(userDao.selectByEmail("nobody@b.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@b.com", "password1"), null, null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Email hoặc mật khẩu không đúng");
        verify(loginAttemptStore).recordFailure("nobody@b.com");
    }

    @Test
    void login_throwsTooManyRequests_whenEmailLockedOut_withoutCheckingCredentials() {
        when(loginAttemptStore.remainingLockMinutes("a@b.com")).thenReturn(7L);

        assertThatThrownBy(() -> authService.login(new LoginRequest("a@b.com", "password1"), null, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS))
                .hasMessage("Tài khoản tạm khoá do đăng nhập sai nhiều lần, thử lại sau 7 phút");
        verify(userDao, never()).selectByEmail(any());
        verify(loginAttemptStore, never()).recordFailure(any());
    }

    @Test
    void login_throwsForbidden_whenAccountLockedByAdmin_evenWithCorrectPassword() {
        User user = activeLocalUser();
        user.setLocked(true);
        when(userDao.selectByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password1", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("a@b.com", "password1"), null, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN))
                .hasMessage("Tài khoản đã bị khoá bởi quản trị viên");
        verify(refreshTokenDao, never()).insert(any());
        verify(twoFactorChallengeStore, never()).issueChallenge(any());
    }

    @Test
    void login_doesNotRevealLockedStatus_whenPasswordWrong() {
        User user = activeLocalUser();
        user.setLocked(true);
        when(userDao.selectByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("a@b.com", "wrong"), null, null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refresh_throwsForbidden_whenAccountLocked() {
        User user = activeLocalUser();
        user.setLocked(true);
        RefreshToken stored = refreshToken(3L, 1L);
        when(refreshTokenDao.selectByTokenHash(sha256("raw-refresh"))).thenReturn(Optional.of(stored));
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("raw-refresh"), null, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(refreshTokenDao, never()).update(any());
        verify(refreshTokenDao, never()).insert(any());
    }

    @Test
    void switchFamily_throwsForbidden_whenAccountLocked() {
        User user = activeLocalUser();
        user.setLocked(true);
        when(familyMembershipDao.selectByUserIdAndFamilyId(1L, 2L)).thenReturn(Optional.of(membership(1L, 2L, "MEMBER")));
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.switchFamily(1L, 2L, null, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(userDao, never()).update(any());
    }

    @Test
    void processOAuth2User_rejectsLockedUser_foundByProvider() {
        User user = activeLocalUser();
        user.setLocked(true);
        when(userDao.selectByProviderAndProviderId("GOOGLE", "g-1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.processOAuth2User("GOOGLE", "g-1", "a@b.com", "An"))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void processOAuth2User_rejectsLockedUser_foundByEmail_withoutLinkingProvider() {
        User user = activeLocalUser();
        user.setLocked(true);
        when(userDao.selectByProviderAndProviderId("GOOGLE", "g-1")).thenReturn(Optional.empty());
        when(userDao.selectByEmail("a@b.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.processOAuth2User("GOOGLE", "g-1", "a@b.com", "An"))
                .isInstanceOf(OAuth2AuthenticationException.class);
        verify(userDao, never()).update(any());
    }

    @Test
    void issueTokens_throwsForbidden_whenAccountLocked() {
        User user = activeLocalUser();
        user.setLocked(true);

        assertThatThrownBy(() -> authService.issueTokens(user))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(refreshTokenDao, never()).insert(any());
    }

    @Test
    void revokeAllSessions_revokesInDbAndBlocksEveryActiveSessionInRedis() {
        when(refreshTokenDao.selectActiveByUserId(5L)).thenReturn(List.of(refreshToken(2L, 5L), refreshToken(3L, 5L)));

        authService.revokeAllSessions(5L);

        verify(refreshTokenDao).revokeAllByUserId(5L);
        verify(revokedSessionStore).markRevoked(2L, 15 * 60 * 1000L);
        verify(revokedSessionStore).markRevoked(3L, 15 * 60 * 1000L);
    }

    @Test
    void revokeAllSessions_doesNothing_whenNoActiveSession() {
        when(refreshTokenDao.selectActiveByUserId(5L)).thenReturn(List.of());

        authService.revokeAllSessions(5L);

        verify(refreshTokenDao, never()).revokeAllByUserId(any());
        verify(revokedSessionStore, never()).markRevoked(any(), anyLong());
    }

    @Test
    void forgotPassword_returnsGenericMessage_evenWhenEmailUnknown_andDoesNotPublishEvent() {
        when(userDao.selectByEmail("nobody@b.com")).thenReturn(Optional.empty());

        var response = authService.forgotPassword(new ForgotPasswordRequest("nobody@b.com"));

        assertThat(response.message()).contains("Nếu email tồn tại");
        verify(eventPublisher, never()).publishEvent(any());
        verify(userDao, never()).update(any());
    }

    @Test
    void forgotPassword_setsResetToken_andPublishesEvent_whenEmailExists() {
        User user = activeLocalUser();
        when(userDao.selectByEmail("a@b.com")).thenReturn(Optional.of(user));

        authService.forgotPassword(new ForgotPasswordRequest("a@b.com"));

        assertThat(user.getResetPasswordToken()).isNotBlank();
        assertThat(user.getResetPasswordTokenExpiresAt()).isAfter(LocalDateTime.now());
        verify(userDao).update(user);
        verify(eventPublisher).publishEvent(any(PasswordResetEvent.class));
    }

    @Test
    void resetPassword_updatesPasswordAndClearsToken_whenTokenValid() {
        User user = activeLocalUser();
        user.setResetPasswordToken("tok");
        user.setResetPasswordTokenExpiresAt(LocalDateTime.now().plusHours(1));
        when(userDao.selectByResetPasswordToken("tok")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpassword1")).thenReturn("new-hashed");

        authService.resetPassword(new ResetPasswordRequest("tok", "newpassword1"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hashed");
        assertThat(user.getResetPasswordToken()).isNull();
        verify(userDao).update(user);
        verify(loginAttemptStore).reset("a@b.com");
    }

    @Test
    void resetPassword_throwsBadRequest_whenTokenExpired() {
        User user = activeLocalUser();
        user.setResetPasswordToken("tok");
        user.setResetPasswordTokenExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(userDao.selectByResetPasswordToken("tok")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest("tok", "newpassword1")))
                .isInstanceOf(BadRequestException.class);
        verify(userDao, never()).update(any());
    }

    @Test
    void changePassword_throwsUnauthorized_whenCurrentPasswordWrong() {
        User user = activeLocalUser();
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(1L, new ChangePasswordRequest("wrong", "newpassword1")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void changePassword_throwsBadRequest_whenOAuth2OnlyAccount() {
        User user = activeLocalUser();
        user.setPasswordHash(null);
        user.setProvider("GOOGLE");
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.changePassword(1L, new ChangePasswordRequest("x", "newpassword1")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateProfile_updatesDisplayNameAndRelationship() {
        User user = activeLocalUser();
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));

        var response = authService.updateProfile(1L, new UpdateProfileRequest("Tên Mới", "Bố"));

        assertThat(response.displayName()).isEqualTo("Tên Mới");
        assertThat(response.relationship()).isEqualTo("Bố");
        verify(userDao).update(user);
    }

    @Test
    void removeMember_deletesAccountEntirely_whenNoOtherMembershipsRemain() {
        User member = activeLocalUser();
        member.setId(5L);
        FamilyMembership membership = membership(5L, 1L, "MEMBER");
        when(familyMembershipDao.selectByUserIdAndFamilyId(5L, 1L)).thenReturn(Optional.of(membership));
        when(userDao.selectById(5L)).thenReturn(Optional.of(member));
        when(familyMembershipDao.selectByUserId(5L)).thenReturn(List.of());

        authService.removeMember(1L, 1L, 5L);

        verify(familyMembershipDao).delete(membership);
        verify(refreshTokenDao).deleteByUserId(5L);
        verify(userDao).delete(member);
        assertMemberEventPublished(FamilyMemberEvent.MEMBER_REMOVED, 1L, 5L, "An");
    }

    @Test
    void removeMember_reassignsActiveFamily_whenOtherMembershipsRemain() {
        User member = activeLocalUser();
        member.setId(5L);
        member.setFamilyId(1L); // family 1 is their currently-active one, being removed from
        FamilyMembership membership = membership(5L, 1L, "MEMBER");
        FamilyMembership other = membership(5L, 2L, "OWNER");
        when(familyMembershipDao.selectByUserIdAndFamilyId(5L, 1L)).thenReturn(Optional.of(membership));
        when(userDao.selectById(5L)).thenReturn(Optional.of(member));
        when(familyMembershipDao.selectByUserId(5L)).thenReturn(List.of(other));

        authService.removeMember(1L, 1L, 5L);

        verify(familyMembershipDao).delete(membership);
        verify(userDao, never()).delete(any());
        assertThat(member.getFamilyId()).isEqualTo(2L);
        assertThat(member.getRole()).isEqualTo("OWNER");
        verify(userDao).update(member);
    }

    @Test
    void removeMember_leavesOtherFamilyUntouched_whenRemovedFamilyIsNotTheirActiveOne() {
        User member = activeLocalUser();
        member.setId(5L);
        member.setFamilyId(2L); // active elsewhere; being removed from family 1 only
        FamilyMembership membership = membership(5L, 1L, "MEMBER");
        when(familyMembershipDao.selectByUserIdAndFamilyId(5L, 1L)).thenReturn(Optional.of(membership));
        when(userDao.selectById(5L)).thenReturn(Optional.of(member));
        when(familyMembershipDao.selectByUserId(5L)).thenReturn(List.of(membership(5L, 2L, "OWNER")));

        authService.removeMember(1L, 1L, 5L);

        verify(familyMembershipDao).delete(membership);
        verify(userDao, never()).update(any());
        verify(userDao, never()).delete(any());
    }

    @Test
    void removeMember_throwsBadRequest_whenRemovingSelf() {
        assertThatThrownBy(() -> authService.removeMember(1L, 1L, 1L)).isInstanceOf(BadRequestException.class);
        verify(userDao, never()).delete(any());
    }

    @Test
    void removeMember_throwsNotFound_whenTargetHasNoMembershipInFamily() {
        when(familyMembershipDao.selectByUserIdAndFamilyId(5L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.removeMember(1L, 1L, 5L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void removeMember_throwsBadRequest_whenTargetIsOwner() {
        when(familyMembershipDao.selectByUserIdAndFamilyId(5L, 1L))
                .thenReturn(Optional.of(membership(5L, 1L, "OWNER")));

        assertThatThrownBy(() -> authService.removeMember(1L, 1L, 5L)).isInstanceOf(BadRequestException.class);
        verify(familyMembershipDao, never()).delete(any());
    }

    @Test
    void inviteMember_throwsConflict_whenEmailAlreadyMemberOfThisFamily() {
        User existing = activeLocalUser();
        existing.setId(20L);
        when(userDao.selectByEmail("existing@b.com")).thenReturn(Optional.of(existing));
        when(familyMembershipDao.selectByUserIdAndFamilyId(20L, 1L))
                .thenReturn(Optional.of(membership(20L, 1L, "MEMBER")));

        assertThatThrownBy(() -> authService.inviteMember(1L, 10L, new InviteMemberRequest("existing@b.com")))
                .isInstanceOf(ConflictException.class);
        verify(familyInviteDao, never()).insert(any());
    }

    @Test
    void inviteMember_allowsInvitingExistingAccount_whenNotYetAMemberOfThisFamily() {
        // The whole point of multi-family support: an email with an account elsewhere
        // can still be invited into a different family.
        User existing = activeLocalUser();
        existing.setId(20L);
        Family family = new Family();
        family.setId(1L);
        family.setName("Nhà Nguyễn");
        User inviter = activeLocalUser();
        inviter.setId(10L);
        when(userDao.selectByEmail("existing@b.com")).thenReturn(Optional.of(existing));
        when(familyMembershipDao.selectByUserIdAndFamilyId(20L, 1L)).thenReturn(Optional.empty());
        when(familyDao.selectById(1L)).thenReturn(Optional.of(family));
        when(userDao.selectById(10L)).thenReturn(Optional.of(inviter));

        authService.inviteMember(1L, 10L, new InviteMemberRequest("existing@b.com"));

        verify(familyInviteDao).insert(any());
    }

    @Test
    void inviteMember_throwsNotFound_whenFamilyMissing() {
        when(userDao.selectByEmail("new@b.com")).thenReturn(Optional.empty());
        when(familyDao.selectById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.inviteMember(1L, 10L, new InviteMemberRequest("new@b.com")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void inviteMember_insertsInvite_andPublishesEvent_whenValid() {
        Family family = new Family();
        family.setId(1L);
        family.setName("Nhà Nguyễn");
        User inviter = activeLocalUser();
        inviter.setId(10L);
        inviter.setDisplayName("An");

        when(userDao.selectByEmail("new@b.com")).thenReturn(Optional.empty());
        when(familyDao.selectById(1L)).thenReturn(Optional.of(family));
        when(userDao.selectById(10L)).thenReturn(Optional.of(inviter));

        var response = authService.inviteMember(1L, 10L, new InviteMemberRequest("new@b.com"));

        assertThat(response.message()).contains("new@b.com");
        ArgumentCaptor<FamilyInvite> captor = ArgumentCaptor.forClass(FamilyInvite.class);
        verify(familyInviteDao).insert(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("new@b.com");
        assertThat(captor.getValue().getFamilyId()).isEqualTo(1L);
        verify(eventPublisher).publishEvent(any(FamilyInviteEvent.class));
    }

    @Test
    void acceptInvite_throwsBadRequest_whenTokenUnknown() {
        when(familyInviteDao.selectByToken("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.acceptInvite("bad", new AcceptInviteRequest("An", "password1")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void acceptInvite_throwsBadRequest_whenAlreadyAccepted() {
        FamilyInvite invite = validInvite();
        invite.setAcceptedAt(LocalDateTime.now());
        when(familyInviteDao.selectByToken("tok")).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> authService.acceptInvite("tok", new AcceptInviteRequest("An", "password1")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void acceptInvite_throwsBadRequest_whenExpired() {
        FamilyInvite invite = validInvite();
        invite.setExpiresAt(LocalDateTime.now().minusHours(1));
        when(familyInviteDao.selectByToken("tok")).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> authService.acceptInvite("tok", new AcceptInviteRequest("An", "password1")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void acceptInvite_createsMemberUser_andMarksInviteAccepted_whenEmailHasNoAccountYet() {
        FamilyInvite invite = validInvite();
        when(familyInviteDao.selectByToken("tok")).thenReturn(Optional.of(invite));
        when(userDao.selectByEmail("invitee@b.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password1")).thenReturn("hashed");

        authService.acceptInvite("tok", new AcceptInviteRequest("Invitee", "password1"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userDao).insert(userCaptor.capture());
        assertThat(userCaptor.getValue().getRole()).isEqualTo("MEMBER");
        assertThat(userCaptor.getValue().getFamilyId()).isEqualTo(invite.getFamilyId());
        verify(familyMembershipDao).insert(any());

        assertThat(invite.getAcceptedAt()).isNotNull();
        verify(familyInviteDao).update(invite);
        assertMemberEventPublished(FamilyMemberEvent.MEMBER_JOINED, invite.getFamilyId(), null, "Invitee");
    }

    @Test
    void acceptInvite_throwsBadRequest_whenNewAccountMissingDisplayNameOrPassword() {
        FamilyInvite invite = validInvite();
        when(familyInviteDao.selectByToken("tok")).thenReturn(Optional.of(invite));
        when(userDao.selectByEmail("invitee@b.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.acceptInvite("tok", new AcceptInviteRequest("", "password1")))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> authService.acceptInvite("tok", new AcceptInviteRequest("Invitee", "short")))
                .isInstanceOf(BadRequestException.class);
        verify(userDao, never()).insert(any());
    }

    @Test
    void acceptInvite_addsMembershipToExistingAccount_withoutCreatingNewUser() {
        // The whole point of multi-family support: accepting an invite for an email
        // that already has an account just adds a membership, not a second account.
        FamilyInvite invite = validInvite();
        User existing = activeLocalUser();
        existing.setId(30L);
        when(familyInviteDao.selectByToken("tok")).thenReturn(Optional.of(invite));
        when(userDao.selectByEmail("invitee@b.com")).thenReturn(Optional.of(existing));
        when(familyMembershipDao.selectByUserIdAndFamilyId(30L, invite.getFamilyId())).thenReturn(Optional.empty());

        authService.acceptInvite("tok", new AcceptInviteRequest(null, null));

        verify(userDao, never()).insert(any());
        ArgumentCaptor<FamilyMembership> captor = ArgumentCaptor.forClass(FamilyMembership.class);
        verify(familyMembershipDao).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(30L);
        assertThat(captor.getValue().getFamilyId()).isEqualTo(invite.getFamilyId());
        assertThat(invite.getAcceptedAt()).isNotNull();
        verify(familyInviteDao).update(invite);
        assertMemberEventPublished(FamilyMemberEvent.MEMBER_JOINED, invite.getFamilyId(), 30L, "An");
    }

    @Test
    void acceptInvite_throwsConflict_whenExistingAccountAlreadyMemberOfThisFamily() {
        FamilyInvite invite = validInvite();
        User existing = activeLocalUser();
        existing.setId(30L);
        when(familyInviteDao.selectByToken("tok")).thenReturn(Optional.of(invite));
        when(userDao.selectByEmail("invitee@b.com")).thenReturn(Optional.of(existing));
        when(familyMembershipDao.selectByUserIdAndFamilyId(30L, invite.getFamilyId()))
                .thenReturn(Optional.of(membership(30L, invite.getFamilyId(), "MEMBER")));

        assertThatThrownBy(() -> authService.acceptInvite("tok", new AcceptInviteRequest(null, null)))
                .isInstanceOf(ConflictException.class);
        verify(familyMembershipDao, never()).insert(any());
        verify(familyInviteDao, never()).update(any());
    }

    @Test
    void setupTwoFactor_generatesAndStoresSecret_withoutEnablingYet() {
        User user = activeLocalUser();
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));
        when(totpService.generateSecret()).thenReturn("SECRET123");
        when(totpService.buildOtpAuthUri("SECRET123", "a@b.com")).thenReturn("otpauth://totp/x");

        TwoFactorSetupResponse response = authService.setupTwoFactor(1L);

        assertThat(response.secret()).isEqualTo("SECRET123");
        assertThat(response.otpAuthUri()).isEqualTo("otpauth://totp/x");
        assertThat(user.getTotpSecret()).startsWith("enc:v1:").doesNotContain("SECRET123");
        assertThat(totpSecretCipher.decrypt(user.getTotpSecret())).isEqualTo("SECRET123");
        assertThat(user.getTotpEnabled()).isFalse();
        verify(userDao).update(user);
    }

    @Test
    void setupTwoFactor_rejected_whenTwoFactorAlreadyEnabled() {
        User user = activeLocalUser();
        user.setTotpEnabled(true);
        user.setTotpSecret("EXISTING");
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.setupTwoFactor(1L)).isInstanceOf(BadRequestException.class);

        assertThat(user.getTotpSecret()).isEqualTo("EXISTING");
        assertThat(user.getTotpEnabled()).isTrue();
        verify(userDao, never()).update(any());
    }

    @Test
    void issueTwoFactorChallenge_delegatesToChallengeStore() {
        when(twoFactorChallengeStore.issueChallenge(1L)).thenReturn("challenge-token");

        assertThat(authService.issueTwoFactorChallenge(1L)).isEqualTo("challenge-token");
    }

    @Test
    void confirmTwoFactor_enablesAndReturnsRecoveryCodes_whenCodeValid() {
        User user = activeLocalUser();
        user.setTotpSecret("SECRET123");
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));
        when(totpService.matchStep("SECRET123", "123456")).thenReturn(Optional.of(100L));
        when(passwordEncoder.encode(any())).thenReturn("hashed-code");

        TwoFactorConfirmResponse response = authService.confirmTwoFactor(1L, "123456");

        assertThat(user.getTotpEnabled()).isTrue();
        assertThat(user.getTotpLastStep()).isEqualTo(100L);
        assertThat(user.getTotpSecret()).startsWith("enc:v1:");
        assertThat(response.recoveryCodes()).hasSize(8);
        verify(twoFactorRecoveryCodeDao).deleteByUserId(1L);
        verify(twoFactorRecoveryCodeDao, times(8)).insert(any());
    }

    @Test
    void confirmTwoFactor_throwsBadRequest_whenCodeInvalid() {
        User user = activeLocalUser();
        user.setTotpSecret("SECRET123");
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));
        when(totpService.matchStep("SECRET123", "000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.confirmTwoFactor(1L, "000000")).isInstanceOf(BadRequestException.class);
        assertThat(user.getTotpEnabled()).isNull();
    }

    @Test
    void disableTwoFactor_clearsSecretAndRecoveryCodes_whenPasswordCorrect() {
        User user = activeLocalUser();
        user.setTotpEnabled(true);
        user.setTotpSecret("SECRET123");
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password1", "hashed")).thenReturn(true);

        authService.disableTwoFactor(1L, "password1", null);

        assertThat(user.getTotpEnabled()).isFalse();
        assertThat(user.getTotpSecret()).isNull();
        verify(twoFactorRecoveryCodeDao).deleteByUserId(1L);
    }

    @Test
    void disableTwoFactor_throwsUnauthorized_whenPasswordWrong() {
        User user = activeLocalUser();
        user.setTotpEnabled(true);
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.disableTwoFactor(1L, "wrong", null)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void verifyTwoFactorLogin_issuesTokens_whenTotpCodeValid() {
        User user = activeLocalUser();
        user.setTotpSecret("SECRET123");
        when(twoFactorChallengeStore.consumeChallenge("challenge-token")).thenReturn(Optional.of(1L));
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));
        when(totpService.matchStep("SECRET123", "123456")).thenReturn(Optional.of(100L));
        when(jwtUtil.generateToken(any(), any(), anyLong())).thenReturn("access-token");

        var response = authService.verifyTwoFactorLogin("challenge-token", "123456", null, null);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(user.getTotpLastStep()).isEqualTo(100L);
        verify(userDao).update(user);
        verify(refreshTokenDao).insert(any());
        verify(loginAttemptStore).reset("a@b.com");
    }

    @Test
    void verifyTwoFactorLogin_fallsBackToRecoveryCode_whenTotpCodeWrong() {
        User user = activeLocalUser();
        user.setTotpSecret("SECRET123");
        when(twoFactorChallengeStore.consumeChallenge("challenge-token")).thenReturn(Optional.of(1L));
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));
        when(totpService.matchStep("SECRET123", "recover1")).thenReturn(Optional.empty());
        TwoFactorRecoveryCode recoveryCode = new TwoFactorRecoveryCode();
        recoveryCode.setId(9L);
        recoveryCode.setUserId(1L);
        recoveryCode.setCodeHash("hashed-recovery");
        when(twoFactorRecoveryCodeDao.selectUnusedByUserId(1L)).thenReturn(List.of(recoveryCode));
        when(passwordEncoder.matches("recover1", "hashed-recovery")).thenReturn(true);
        when(jwtUtil.generateToken(any(), any(), anyLong())).thenReturn("access-token");

        var response = authService.verifyTwoFactorLogin("challenge-token", "recover1", null, null);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(recoveryCode.getUsedAt()).isNotNull();
        verify(twoFactorRecoveryCodeDao).update(recoveryCode);
    }

    @Test
    void verifyTwoFactorLogin_throwsUnauthorized_whenCodeInvalid() {
        User user = activeLocalUser();
        user.setTotpSecret("SECRET123");
        when(twoFactorChallengeStore.consumeChallenge("challenge-token")).thenReturn(Optional.of(1L));
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));
        when(totpService.matchStep("SECRET123", "000000")).thenReturn(Optional.empty());
        when(twoFactorRecoveryCodeDao.selectUnusedByUserId(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> authService.verifyTwoFactorLogin("challenge-token", "000000", null, null))
                .isInstanceOf(UnauthorizedException.class);
        verify(loginAttemptStore).recordFailure("a@b.com");
        verify(loginAttemptStore, never()).reset(any());
    }

    @Test
    void verifyTwoFactorLogin_throwsUnauthorized_whenChallengeExpiredOrUnknown() {
        when(twoFactorChallengeStore.consumeChallenge("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyTwoFactorLogin("bad-token", "123456", null, null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void listSessionsPaged_marksMatchingIdAsCurrent() {
        RefreshToken t1 = refreshToken(1L, 5L);
        RefreshToken t2 = refreshToken(2L, 5L);
        when(refreshTokenDao.countActiveByUserId(5L)).thenReturn(2L);
        when(refreshTokenDao.selectActiveByUserIdPaged(5L, 5, 0)).thenReturn(List.of(t1, t2));

        var result = authService.listSessionsPaged(5L, 2L, 0, 5);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).isCurrent()).isFalse();
        assertThat(result.content().get(1).isCurrent()).isTrue();
        assertThat(result.totalElements()).isEqualTo(2L);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    void listSessionsPaged_returnsPageWithOffset() {
        when(refreshTokenDao.countActiveByUserId(5L)).thenReturn(12L);
        when(refreshTokenDao.selectActiveByUserIdPaged(5L, 5, 10))
                .thenReturn(List.of(refreshToken(11L, 5L), refreshToken(12L, 5L)));

        var result = authService.listSessionsPaged(5L, 1L, 2, 5);

        assertThat(result.content()).hasSize(2);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(5);
        assertThat(result.totalElements()).isEqualTo(12L);
        assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    void listSessionsPaged_rejectsInvalidPageOrSize() {
        assertThatThrownBy(() -> authService.listSessionsPaged(5L, 1L, -1, 5))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> authService.listSessionsPaged(5L, 1L, 0, 0))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> authService.listSessionsPaged(5L, 1L, 0, 101))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getFamilyMembersPaged_returnsPageWithOffset() {
        User user = activeLocalUser();
        user.setId(21L);
        when(familyMembershipDao.countByFamilyId(1L)).thenReturn(7L);
        when(familyMembershipDao.selectByFamilyIdPaged(1L, 5, 5))
                .thenReturn(List.of(membership(21L, 1L, "MEMBER"), membership(22L, 1L, "MEMBER")));
        when(userDao.selectById(21L)).thenReturn(Optional.of(user));
        when(userDao.selectById(22L)).thenReturn(Optional.empty());

        var result = authService.getFamilyMembersPaged(1L, 1, 5);

        assertThat(result.content()).hasSize(1);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(7L);
        assertThat(result.totalPages()).isEqualTo(2);
    }

    @Test
    void getFamilyMembersPaged_rejectsInvalidPageOrSize() {
        assertThatThrownBy(() -> authService.getFamilyMembersPaged(1L, -1, 5))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> authService.getFamilyMembersPaged(1L, 0, 0))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> authService.getFamilyMembersPaged(1L, 0, 101))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void logout_revokesSessionAndMarksItInRedis_whenSessionIdPresent() {
        authService.logout(5L, 2L);

        verify(refreshTokenDao).revokeById(2L, 5L);
        verify(revokedSessionStore).markRevoked(2L, 15 * 60 * 1000L);
    }

    @Test
    void logout_doesNothing_whenSessionIdMissing() {
        authService.logout(5L, null);

        verify(refreshTokenDao, never()).revokeById(any(), any());
        verify(revokedSessionStore, never()).markRevoked(any(), anyLong());
    }

    @Test
    void revokeSession_revokesAndBlocksImmediately_whenOwnedByUser() {
        when(refreshTokenDao.revokeById(2L, 5L)).thenReturn(1);

        authService.revokeSession(5L, 2L);

        verify(revokedSessionStore).markRevoked(2L, 15 * 60 * 1000L);
    }

    @Test
    void revokeSession_throwsNotFound_whenNotOwnedByUserOrAlreadyRevoked() {
        when(refreshTokenDao.revokeById(2L, 5L)).thenReturn(0);

        assertThatThrownBy(() -> authService.revokeSession(5L, 2L)).isInstanceOf(NotFoundException.class);
        verify(revokedSessionStore, never()).markRevoked(any(), anyLong());
    }

    @Test
    void revokeAllOtherSessions_blocksEveryOtherActiveSession_butNotTheCurrentOne() {
        RefreshToken current = refreshToken(2L, 5L);
        RefreshToken other = refreshToken(3L, 5L);
        when(refreshTokenDao.selectActiveByUserId(5L)).thenReturn(List.of(current, other));

        authService.revokeAllOtherSessions(5L, 2L);

        verify(refreshTokenDao).revokeAllByUserIdExcept(5L, 2L);
        verify(revokedSessionStore).markRevoked(3L, 15 * 60 * 1000L);
        verify(revokedSessionStore, never()).markRevoked(eq(2L), anyLong());
    }

    @Test
    void revokeAllOtherSessions_doesNothing_whenNoOtherSessionsExist() {
        RefreshToken current = refreshToken(2L, 5L);
        when(refreshTokenDao.selectActiveByUserId(5L)).thenReturn(List.of(current));

        authService.revokeAllOtherSessions(5L, 2L);

        verify(refreshTokenDao, never()).revokeAllByUserIdExcept(any(), any());
        verify(revokedSessionStore, never()).markRevoked(any(), anyLong());
    }

    @Test
    void renameFamily_updatesTrimmedName() {
        Family family = new Family();
        family.setId(1L);
        family.setName("Cũ");
        when(familyDao.selectById(1L)).thenReturn(Optional.of(family));

        var response = authService.renameFamily(1L, new RenameFamilyRequest("  Nhà Mới  "));

        assertThat(response.message()).contains("Đã đổi tên");
        assertThat(family.getName()).isEqualTo("Nhà Mới");
        verify(familyDao).update(family);
    }

    @Test
    void renameFamily_throwsNotFound_whenFamilyMissing() {
        when(familyDao.selectById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.renameFamily(1L, new RenameFamilyRequest("Nhà Mới")))
                .isInstanceOf(NotFoundException.class);
        verify(familyDao, never()).update(any());
    }

    @Test
    void leaveFamily_activatesAnotherMembership_whenMemberHasOthers() {
        User member = activeLocalUser();
        member.setId(5L);
        member.setRole("MEMBER");
        FamilyMembership leaving = membership(5L, 1L, "MEMBER");
        when(familyMembershipDao.selectByUserIdAndFamilyId(5L, 1L)).thenReturn(Optional.of(leaving));
        when(userDao.selectById(5L)).thenReturn(Optional.of(member));
        when(familyMembershipDao.selectByUserId(5L)).thenReturn(List.of(membership(5L, 2L, "OWNER")));

        var response = authService.leaveFamily(1L, 5L, 7L, null, null);

        verify(familyMembershipDao).delete(leaving);
        assertThat(member.getFamilyId()).isEqualTo(2L);
        assertThat(member.getRole()).isEqualTo("OWNER");
        verify(userDao).update(member);
        verify(familyDao, never()).insert(any());
        verify(revokedSessionStore).markRevoked(7L, 15 * 60 * 1000L);
        assertMemberEventPublished(FamilyMemberEvent.MEMBER_LEFT, 1L, 5L, "An");
        assertThat(response.refreshToken()).isNotBlank();
    }

    @Test
    void leaveFamily_createsPersonalFamily_whenNoOtherMembershipRemains() {
        User member = activeLocalUser();
        member.setId(5L);
        member.setDisplayName("Bình");
        member.setRole("MEMBER");
        when(familyMembershipDao.selectByUserIdAndFamilyId(5L, 1L))
                .thenReturn(Optional.of(membership(5L, 1L, "MEMBER")));
        when(userDao.selectById(5L)).thenReturn(Optional.of(member));
        when(familyMembershipDao.selectByUserId(5L)).thenReturn(List.of());
        doAnswer(inv -> {
            ((Family) inv.getArgument(0)).setId(9L);
            return 1;
        }).when(familyDao).insert(any(Family.class));

        authService.leaveFamily(1L, 5L, null, null, null);

        ArgumentCaptor<Family> familyCaptor = ArgumentCaptor.forClass(Family.class);
        verify(familyDao).insert(familyCaptor.capture());
        assertThat(familyCaptor.getValue().getName()).isEqualTo("Bình's Family");
        ArgumentCaptor<FamilyMembership> membershipCaptor = ArgumentCaptor.forClass(FamilyMembership.class);
        verify(familyMembershipDao).insert(membershipCaptor.capture());
        assertThat(membershipCaptor.getValue().getFamilyId()).isEqualTo(9L);
        assertThat(membershipCaptor.getValue().getRole()).isEqualTo("OWNER");
        assertThat(member.getFamilyId()).isEqualTo(9L);
        assertThat(member.getRole()).isEqualTo("OWNER");
        assertMemberEventPublished(FamilyMemberEvent.MEMBER_LEFT, 1L, 5L, "Bình");
        verify(userDao).update(member);
    }

    @Test
    void leaveFamily_throwsBadRequest_whenCallerIsOwner() {
        when(familyMembershipDao.selectByUserIdAndFamilyId(1L, 1L))
                .thenReturn(Optional.of(membership(1L, 1L, "OWNER")));

        assertThatThrownBy(() -> authService.leaveFamily(1L, 1L, 7L, null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("chuyển quyền chủ hộ");
        verify(familyMembershipDao, never()).delete(any());
        verify(refreshTokenDao, never()).insert(any());
    }

    @Test
    void leaveFamily_throwsNotFound_whenNotAMember() {
        when(familyMembershipDao.selectByUserIdAndFamilyId(5L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.leaveFamily(1L, 5L, null, null, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void transferOwnership_swapsRoles_andSyncsUsersWhoseActiveFamilyIsThisOne() {
        User caller = activeLocalUser();
        User target = activeLocalUser();
        target.setId(5L);
        target.setRole("MEMBER");
        FamilyMembership callerMembership = membership(1L, 1L, "OWNER");
        FamilyMembership targetMembership = membership(5L, 1L, "MEMBER");
        when(familyMembershipDao.selectByUserIdAndFamilyId(1L, 1L)).thenReturn(Optional.of(callerMembership));
        when(familyMembershipDao.selectByUserIdAndFamilyId(5L, 1L)).thenReturn(Optional.of(targetMembership));
        when(userDao.selectById(1L)).thenReturn(Optional.of(caller));
        when(userDao.selectById(5L)).thenReturn(Optional.of(target));

        var response = authService.transferOwnership(1L, 1L, 7L, new TransferOwnershipRequest(5L), null, null);

        assertThat(targetMembership.getRole()).isEqualTo("OWNER");
        assertThat(callerMembership.getRole()).isEqualTo("MEMBER");
        assertThat(target.getRole()).isEqualTo("OWNER");
        assertThat(caller.getRole()).isEqualTo("MEMBER");
        verify(familyMembershipDao).update(targetMembership);
        verify(familyMembershipDao).update(callerMembership);
        verify(userDao).update(target);
        verify(userDao).update(caller);
        verify(revokedSessionStore).markRevoked(7L, 15 * 60 * 1000L);
        assertThat(response.refreshToken()).isNotBlank();
    }

    @Test
    void transferOwnership_leavesTargetUserRoleAlone_whenTheirActiveFamilyIsElsewhere() {
        User caller = activeLocalUser();
        User target = activeLocalUser();
        target.setId(5L);
        target.setFamilyId(2L);
        target.setRole("OWNER");
        FamilyMembership targetMembership = membership(5L, 1L, "MEMBER");
        when(familyMembershipDao.selectByUserIdAndFamilyId(1L, 1L))
                .thenReturn(Optional.of(membership(1L, 1L, "OWNER")));
        when(familyMembershipDao.selectByUserIdAndFamilyId(5L, 1L)).thenReturn(Optional.of(targetMembership));
        when(userDao.selectById(1L)).thenReturn(Optional.of(caller));
        when(userDao.selectById(5L)).thenReturn(Optional.of(target));

        authService.transferOwnership(1L, 1L, null, new TransferOwnershipRequest(5L), null, null);

        assertThat(targetMembership.getRole()).isEqualTo("OWNER");
        assertThat(target.getFamilyId()).isEqualTo(2L);
        verify(userDao, never()).update(target);
        verify(userDao).update(caller);
    }

    @Test
    void transferOwnership_throwsNotFound_whenTargetNotAMemberOfThisFamily() {
        when(familyMembershipDao.selectByUserIdAndFamilyId(1L, 1L))
                .thenReturn(Optional.of(membership(1L, 1L, "OWNER")));
        when(familyMembershipDao.selectByUserIdAndFamilyId(5L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.transferOwnership(1L, 1L, null, new TransferOwnershipRequest(5L), null, null))
                .isInstanceOf(NotFoundException.class);
        verify(familyMembershipDao, never()).update(any());
    }

    @Test
    void transferOwnership_throwsBadRequest_whenTargetIsSelf() {
        assertThatThrownBy(() -> authService.transferOwnership(1L, 1L, null, new TransferOwnershipRequest(1L), null, null))
                .isInstanceOf(BadRequestException.class);
        verify(familyMembershipDao, never()).update(any());
    }

    @Test
    void getPendingInvitesPaged_returnsPageWithOffset() {
        when(familyInviteDao.countPendingByFamilyId(1L)).thenReturn(7L);
        when(familyInviteDao.selectPendingByFamilyIdPaged(1L, 5, 5)).thenReturn(List.of(validInvite()));

        var result = authService.getPendingInvitesPaged(1L, 1, 5);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).email()).isEqualTo("invitee@b.com");
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(7L);
        assertThat(result.totalPages()).isEqualTo(2);
    }

    @Test
    void getPendingInvitesPaged_rejectsInvalidPageOrSize() {
        assertThatThrownBy(() -> authService.getPendingInvitesPaged(1L, -1, 5))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> authService.getPendingInvitesPaged(1L, 0, 0))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> authService.getPendingInvitesPaged(1L, 0, 101))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void cancelInvite_deletesInvite_whenInFamily() {
        FamilyInvite invite = validInvite();
        when(familyInviteDao.selectByIdAndFamilyId(1L, 1L)).thenReturn(Optional.of(invite));

        authService.cancelInvite(1L, 1L);

        verify(familyInviteDao).delete(invite);
    }

    @Test
    void cancelInvite_throwsNotFound_whenNotInFamily() {
        when(familyInviteDao.selectByIdAndFamilyId(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.cancelInvite(1L, 1L)).isInstanceOf(NotFoundException.class);
        verify(familyInviteDao, never()).delete(any());
    }

    @Test
    void resendInvite_issuesNewTokenAndExtendsExpiry_thenPublishesEvent() {
        FamilyInvite invite = validInvite();
        invite.setExpiresAt(LocalDateTime.now().minusHours(1));
        Family family = new Family();
        family.setId(1L);
        family.setName("Nhà Nguyễn");
        User caller = activeLocalUser();
        when(familyInviteDao.selectByIdAndFamilyId(1L, 1L)).thenReturn(Optional.of(invite));
        when(familyDao.selectById(1L)).thenReturn(Optional.of(family));
        when(userDao.selectById(1L)).thenReturn(Optional.of(caller));

        var response = authService.resendInvite(1L, 1L, 1L);

        assertThat(response.message()).contains("invitee@b.com");
        assertThat(invite.getToken()).isNotBlank().isNotEqualTo("tok");
        assertThat(invite.getExpiresAt()).isAfter(LocalDateTime.now().plusHours(71));
        verify(familyInviteDao).update(invite);

        ArgumentCaptor<FamilyInviteEvent> eventCaptor = ArgumentCaptor.forClass(FamilyInviteEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().token()).isEqualTo(invite.getToken());
        assertThat(eventCaptor.getValue().email()).isEqualTo("invitee@b.com");
        assertThat(eventCaptor.getValue().familyName()).isEqualTo("Nhà Nguyễn");
    }

    @Test
    void resendInvite_throwsBadRequest_whenAlreadyAccepted() {
        FamilyInvite invite = validInvite();
        invite.setAcceptedAt(LocalDateTime.now());
        when(familyInviteDao.selectByIdAndFamilyId(1L, 1L)).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> authService.resendInvite(1L, 1L, 1L)).isInstanceOf(BadRequestException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void resendInvite_throwsNotFound_whenNotInFamily() {
        when(familyInviteDao.selectByIdAndFamilyId(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resendInvite(1L, 1L, 1L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void verifyTwoFactorLogin_throwsTooManyRequests_whenEmailLockedOut() {
        User user = activeLocalUser();
        user.setTotpSecret("SECRET123");
        when(twoFactorChallengeStore.consumeChallenge("challenge-token")).thenReturn(Optional.of(1L));
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));
        when(loginAttemptStore.remainingLockMinutes("a@b.com")).thenReturn(3L);

        assertThatThrownBy(() -> authService.verifyTwoFactorLogin("challenge-token", "123456", null, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        verify(totpService, never()).matchStep(any(), any());
    }

    @Test
    void verifyTwoFactorLogin_throwsForbidden_whenAccountLocked() {
        User user = activeLocalUser();
        user.setTotpSecret("SECRET123");
        user.setLocked(true);
        when(twoFactorChallengeStore.consumeChallenge("challenge-token")).thenReturn(Optional.of(1L));
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.verifyTwoFactorLogin("challenge-token", "123456", null, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(refreshTokenDao, never()).insert(any());
    }

    @Test
    void verifyTwoFactorLogin_rejectsReplayedTotpStep() {
        User user = activeLocalUser();
        user.setTotpSecret("SECRET123");
        user.setTotpLastStep(100L);
        when(twoFactorChallengeStore.consumeChallenge("challenge-token")).thenReturn(Optional.of(1L));
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));
        when(totpService.matchStep("SECRET123", "123456")).thenReturn(Optional.of(100L));
        when(twoFactorRecoveryCodeDao.selectUnusedByUserId(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> authService.verifyTwoFactorLogin("challenge-token", "123456", null, null))
                .isInstanceOf(UnauthorizedException.class);
        verify(userDao, never()).update(any());
        verify(refreshTokenDao, never()).insert(any());
    }

    @Test
    void verifyTwoFactorLogin_decryptsStoredSecret_beforeCheckingTheCode() {
        User user = activeLocalUser();
        String encrypted = totpSecretCipher.encrypt("SECRET123");
        user.setTotpSecret(encrypted);
        user.setTotpLastStep(99L);
        when(twoFactorChallengeStore.consumeChallenge("challenge-token")).thenReturn(Optional.of(1L));
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));
        when(totpService.matchStep("SECRET123", "123456")).thenReturn(Optional.of(100L));
        when(jwtUtil.generateToken(any(), any(), anyLong())).thenReturn("access-token");

        authService.verifyTwoFactorLogin("challenge-token", "123456", null, null);

        assertThat(user.getTotpSecret()).isEqualTo(encrypted);
        assertThat(user.getTotpLastStep()).isEqualTo(100L);
    }

    @Test
    void verifyTwoFactorLogin_reEncryptsLegacyPlaintextSecret_onSuccess() {
        User user = activeLocalUser();
        user.setTotpSecret("SECRET123");
        when(twoFactorChallengeStore.consumeChallenge("challenge-token")).thenReturn(Optional.of(1L));
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));
        when(totpService.matchStep("SECRET123", "123456")).thenReturn(Optional.of(100L));
        when(jwtUtil.generateToken(any(), any(), anyLong())).thenReturn("access-token");

        authService.verifyTwoFactorLogin("challenge-token", "123456", null, null);

        assertThat(user.getTotpSecret()).startsWith("enc:v1:");
        assertThat(totpSecretCipher.decrypt(user.getTotpSecret())).isEqualTo("SECRET123");
    }

    @Test
    void disableTwoFactor_acceptsTotpCode_whenAccountHasNoPassword() {
        User user = passwordlessUserWithTwoFactor();
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));
        when(totpService.matchStep("SECRET123", "123456")).thenReturn(Optional.of(100L));

        authService.disableTwoFactor(1L, null, "123456");

        assertThat(user.getTotpEnabled()).isFalse();
        assertThat(user.getTotpSecret()).isNull();
        assertThat(user.getTotpLastStep()).isNull();
        verify(twoFactorRecoveryCodeDao).deleteByUserId(1L);
    }

    @Test
    void disableTwoFactor_acceptsRecoveryCode_whenAccountHasNoPassword() {
        User user = passwordlessUserWithTwoFactor();
        TwoFactorRecoveryCode recoveryCode = new TwoFactorRecoveryCode();
        recoveryCode.setUserId(1L);
        recoveryCode.setCodeHash("hashed-recovery");
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));
        when(totpService.matchStep("SECRET123", "abcdef123456")).thenReturn(Optional.empty());
        when(twoFactorRecoveryCodeDao.selectUnusedByUserId(1L)).thenReturn(List.of(recoveryCode));
        when(passwordEncoder.matches("abcdef123456", "hashed-recovery")).thenReturn(true);

        authService.disableTwoFactor(1L, "  ", "abcdef123456");

        assertThat(user.getTotpEnabled()).isFalse();
        assertThat(recoveryCode.getUsedAt()).isNotNull();
    }

    @Test
    void disableTwoFactor_throwsUnauthorized_whenCodeInvalid_forAccountWithoutPassword() {
        User user = passwordlessUserWithTwoFactor();
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));
        when(totpService.matchStep("SECRET123", "000000")).thenReturn(Optional.empty());
        when(twoFactorRecoveryCodeDao.selectUnusedByUserId(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> authService.disableTwoFactor(1L, null, "000000"))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(user.getTotpEnabled()).isTrue();
        verify(twoFactorRecoveryCodeDao, never()).deleteByUserId(any());
    }

    @Test
    void disableTwoFactor_throwsBadRequest_whenBothOrNeitherPasswordAndCodeGiven() {
        User user = activeLocalUser();
        user.setTotpEnabled(true);
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.disableTwoFactor(1L, "password1", "123456"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> authService.disableTwoFactor(1L, null, null))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> authService.disableTwoFactor(1L, "", ""))
                .isInstanceOf(BadRequestException.class);
        assertThat(user.getTotpEnabled()).isTrue();
    }

    @Test
    void disableTwoFactor_throwsBadRequest_whenAccountHasPasswordButOnlyCodeGiven() {
        User user = activeLocalUser();
        user.setTotpEnabled(true);
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.disableTwoFactor(1L, null, "123456"))
                .isInstanceOf(BadRequestException.class);
        verify(totpService, never()).matchStep(any(), any());
    }

    @Test
    void requestEmailChange_storesPendingEmail_andPublishesVerificationEventToTheNewAddress() {
        User user = activeLocalUser();
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password1", "hashed")).thenReturn(true);
        when(userDao.selectByEmail("new@b.com")).thenReturn(Optional.empty());

        var response = authService.requestEmailChange(1L, new ChangeEmailRequest("new@b.com", "password1", null));

        assertThat(response.message()).contains("new@b.com");
        assertThat(user.getEmail()).isEqualTo("a@b.com");
        assertThat(user.getPendingEmail()).isEqualTo("new@b.com");
        assertThat(user.getPendingEmailToken()).startsWith("ec.");
        assertThat(user.getPendingEmailExpiresAt()).isAfter(LocalDateTime.now().plusHours(23));
        verify(userDao).update(user);

        ArgumentCaptor<UserVerificationEvent> captor = ArgumentCaptor.forClass(UserVerificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo("new@b.com");
        assertThat(captor.getValue().verificationToken()).isEqualTo(user.getPendingEmailToken());
        assertThat(authService.isEmailChangeToken(user.getPendingEmailToken())).isTrue();
    }

    @Test
    void requestEmailChange_throwsConflict_whenNewEmailAlreadyUsed() {
        User user = activeLocalUser();
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password1", "hashed")).thenReturn(true);
        when(userDao.selectByEmail("taken@b.com")).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> authService.requestEmailChange(1L, new ChangeEmailRequest("taken@b.com", "password1", null)))
                .isInstanceOf(ConflictException.class);
        verify(userDao, never()).update(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void requestEmailChange_throwsUnauthorized_whenPasswordWrong() {
        User user = activeLocalUser();
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.requestEmailChange(1L, new ChangeEmailRequest("new@b.com", "wrong", null)))
                .isInstanceOf(UnauthorizedException.class);
        verify(userDao, never()).selectByEmail(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void requestEmailChange_throwsBadRequest_whenSameAsCurrentEmail() {
        User user = activeLocalUser();
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password1", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.requestEmailChange(1L, new ChangeEmailRequest("A@B.com", "password1", null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void requestEmailChange_throwsBadRequest_whenProviderOnlyAccountHasNoTwoFactor() {
        User user = activeLocalUser();
        user.setPasswordHash(null);
        user.setProvider("GOOGLE");
        user.setTotpEnabled(false);
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.requestEmailChange(1L, new ChangeEmailRequest("new@b.com", null, "123456")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("2FA");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void requestEmailChange_acceptsTotpCode_forProviderOnlyAccountWithTwoFactor() {
        User user = passwordlessUserWithTwoFactor();
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));
        when(totpService.matchStep("SECRET123", "123456")).thenReturn(Optional.of(100L));
        when(userDao.selectByEmail("new@b.com")).thenReturn(Optional.empty());

        authService.requestEmailChange(1L, new ChangeEmailRequest("new@b.com", null, "123456"));

        assertThat(user.getPendingEmail()).isEqualTo("new@b.com");
        assertThat(user.getTotpLastStep()).isEqualTo(100L);
        verify(eventPublisher).publishEvent(any(UserVerificationEvent.class));
    }

    @Test
    void requestEmailChange_throwsUnauthorized_whenTotpStepReplayed() {
        User user = passwordlessUserWithTwoFactor();
        user.setTotpLastStep(100L);
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));
        when(totpService.matchStep("SECRET123", "123456")).thenReturn(Optional.of(100L));

        assertThatThrownBy(() -> authService.requestEmailChange(1L, new ChangeEmailRequest("new@b.com", null, "123456")))
                .isInstanceOf(UnauthorizedException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void verifyEmailChange_swapsEmail_clearsPendingFields_andRevokesEverySession() {
        User user = userWithPendingEmail(LocalDateTime.now().plusHours(1));
        when(userDao.selectByPendingEmailToken("ec.tok")).thenReturn(Optional.of(user));
        when(userDao.selectByEmail("new@b.com")).thenReturn(Optional.empty());
        when(refreshTokenDao.selectActiveByUserId(1L)).thenReturn(List.of(refreshToken(2L, 1L)));

        authService.verifyEmailChange("ec.tok");

        assertThat(user.getEmail()).isEqualTo("new@b.com");
        assertThat(user.getPendingEmail()).isNull();
        assertThat(user.getPendingEmailToken()).isNull();
        assertThat(user.getPendingEmailExpiresAt()).isNull();
        verify(userDao).update(user);
        verify(refreshTokenDao).revokeAllByUserId(1L);
        verify(revokedSessionStore).markRevoked(2L, 15 * 60 * 1000L);
    }

    @Test
    void verifyEmailChange_throwsBadRequest_whenTokenUnknown() {
        when(userDao.selectByPendingEmailToken("ec.bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmailChange("ec.bad")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void verifyEmailChange_throwsBadRequest_whenExpired() {
        User user = userWithPendingEmail(LocalDateTime.now().minusMinutes(1));
        when(userDao.selectByPendingEmailToken("ec.tok")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.verifyEmailChange("ec.tok")).isInstanceOf(BadRequestException.class);
        assertThat(user.getEmail()).isEqualTo("a@b.com");
        verify(userDao, never()).update(any());
    }

    @Test
    void verifyEmailChange_throwsConflict_whenAddressTakenInTheMeantime() {
        User user = userWithPendingEmail(LocalDateTime.now().plusHours(1));
        User other = activeLocalUser();
        other.setId(9L);
        when(userDao.selectByPendingEmailToken("ec.tok")).thenReturn(Optional.of(user));
        when(userDao.selectByEmail("new@b.com")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> authService.verifyEmailChange("ec.tok")).isInstanceOf(ConflictException.class);
        assertThat(user.getEmail()).isEqualTo("a@b.com");
        verify(userDao, never()).update(any());
    }

    @Test
    void isEmailChangeToken_distinguishesRegistrationTokens() {
        assertThat(authService.isEmailChangeToken("ec.abc")).isTrue();
        assertThat(authService.isEmailChangeToken("abc-DEF_123")).isFalse();
        assertThat(authService.isEmailChangeToken(null)).isFalse();
    }

    @Test
    void exportPersonalData_returnsProfileMembershipsAndSessions_withoutSecrets() {
        User user = activeLocalUser();
        user.setTotpSecret("SECRET123");
        Family family = new Family();
        family.setId(1L);
        family.setName("Nhà Nguyễn");
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));
        when(familyMembershipDao.selectByUserId(1L)).thenReturn(List.of(membership(1L, 1L, "OWNER")));
        when(familyDao.selectById(1L)).thenReturn(Optional.of(family));
        when(refreshTokenDao.selectActiveByUserId(1L)).thenReturn(List.of(refreshToken(2L, 1L), refreshToken(3L, 1L)));

        var result = authService.exportPersonalData(1L, 2L);

        assertThat(result.profile().email()).isEqualTo("a@b.com");
        assertThat(result.memberships()).hasSize(1);
        assertThat(result.memberships().get(0).familyName()).isEqualTo("Nhà Nguyễn");
        assertThat(result.sessions()).hasSize(2);
        assertThat(result.sessions().get(0).isCurrent()).isTrue();
        assertThat(result.toString()).doesNotContain("hashed", "SECRET123");
    }

    @Test
    void deleteAccount_removesEverythingIncludingAFamilyLeftEmpty() {
        User user = activeLocalUser();
        FamilyMembership own = membership(1L, 1L, "OWNER");
        Family family = new Family();
        family.setId(1L);
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password1", "hashed")).thenReturn(true);
        when(familyMembershipDao.selectByUserId(1L)).thenReturn(List.of(own));
        when(familyMembershipDao.countByFamilyId(1L)).thenReturn(1L, 0L);
        when(familyDao.selectById(1L)).thenReturn(Optional.of(family));

        authService.deleteAccount(1L, new DeleteAccountRequest("password1", null));

        verify(refreshTokenDao).deleteByUserId(1L);
        verify(twoFactorRecoveryCodeDao).deleteByUserId(1L);
        verify(familyInviteDao).deleteByInvitedByUserId(1L);
        verify(familyMembershipDao).delete(own);
        verify(userDao).delete(user);
        verify(familyInviteDao).deleteByFamilyId(1L);
        verify(familyDao).delete(family);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void deleteAccount_keepsFamilyThatStillHasMembers_andPublishesMemberLeft() {
        User user = activeLocalUser();
        FamilyMembership own = membership(1L, 1L, "MEMBER");
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password1", "hashed")).thenReturn(true);
        when(familyMembershipDao.selectByUserId(1L)).thenReturn(List.of(own));
        when(familyMembershipDao.countByFamilyId(1L)).thenReturn(3L);

        authService.deleteAccount(1L, new DeleteAccountRequest("password1", null));

        verify(userDao).delete(user);
        verify(familyDao, never()).delete(any());
        verify(familyInviteDao, never()).deleteByFamilyId(any());
        assertMemberEventPublished(FamilyMemberEvent.MEMBER_LEFT, 1L, 1L, "An");
    }

    @Test
    void deleteAccount_throwsBadRequest_whenOwnerOfFamilyThatStillHasOtherMembers() {
        User user = activeLocalUser();
        Family family = new Family();
        family.setId(1L);
        family.setName("Nhà Nguyễn");
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password1", "hashed")).thenReturn(true);
        when(familyMembershipDao.selectByUserId(1L)).thenReturn(List.of(membership(1L, 1L, "OWNER")));
        when(familyMembershipDao.countByFamilyId(1L)).thenReturn(2L);
        when(familyDao.selectById(1L)).thenReturn(Optional.of(family));

        assertThatThrownBy(() -> authService.deleteAccount(1L, new DeleteAccountRequest("password1", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("chuyển quyền chủ hộ");
        verify(userDao, never()).delete(any());
        verify(refreshTokenDao, never()).deleteByUserId(any());
    }

    @Test
    void deleteAccount_throwsUnauthorized_whenPasswordWrong() {
        User user = activeLocalUser();
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.deleteAccount(1L, new DeleteAccountRequest("wrong", null)))
                .isInstanceOf(UnauthorizedException.class);
        verify(userDao, never()).delete(any());
    }

    @Test
    void deleteAccount_throwsBadRequest_whenProviderOnlyAccountHasNoTwoFactor() {
        User user = activeLocalUser();
        user.setPasswordHash(null);
        user.setProvider("GOOGLE");
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.deleteAccount(1L, new DeleteAccountRequest(null, "123456")))
                .isInstanceOf(BadRequestException.class);
        verify(userDao, never()).delete(any());
    }

    @Test
    void deleteAccount_acceptsTotpCode_forProviderOnlyAccountWithTwoFactor() {
        User user = passwordlessUserWithTwoFactor();
        FamilyMembership own = membership(1L, 1L, "MEMBER");
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));
        when(totpService.matchStep("SECRET123", "123456")).thenReturn(Optional.of(100L));
        when(familyMembershipDao.selectByUserId(1L)).thenReturn(List.of(own));
        when(familyMembershipDao.countByFamilyId(1L)).thenReturn(2L);

        authService.deleteAccount(1L, new DeleteAccountRequest(null, "123456"));

        verify(userDao).delete(user);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static User passwordlessUserWithTwoFactor() {
        User user = activeLocalUser();
        user.setPasswordHash(null);
        user.setProvider("GOOGLE");
        user.setTotpEnabled(true);
        user.setTotpSecret("SECRET123");
        return user;
    }

    private static User userWithPendingEmail(LocalDateTime expiresAt) {
        User user = activeLocalUser();
        user.setPendingEmail("new@b.com");
        user.setPendingEmailToken("ec.tok");
        user.setPendingEmailExpiresAt(expiresAt);
        return user;
    }

    private static RefreshToken refreshToken(Long id, Long userId) {
        RefreshToken token = new RefreshToken();
        token.setId(id);
        token.setUserId(userId);
        token.setRevoked(false);
        token.setExpiresAt(LocalDateTime.now().plusDays(7));
        token.setCreatedAt(LocalDateTime.now());
        return token;
    }

    private void assertMemberEventPublished(String eventType, Long familyId, Long memberUserId,
                                            String memberDisplayName) {
        ArgumentCaptor<FamilyMemberEvent> captor = ArgumentCaptor.forClass(FamilyMemberEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        FamilyMemberEvent event = captor.getValue();
        assertThat(event.eventType()).isEqualTo(eventType);
        assertThat(event.familyId()).isEqualTo(familyId);
        assertThat(event.memberUserId()).isEqualTo(memberUserId);
        assertThat(event.memberDisplayName()).isEqualTo(memberDisplayName);
    }

    private static User activeLocalUser() {
        User user = new User();
        user.setId(1L);
        user.setFamilyId(1L);
        user.setEmail("a@b.com");
        user.setPasswordHash("hashed");
        user.setDisplayName("An");
        user.setRole("OWNER");
        user.setActive(true);
        user.setProvider("LOCAL");
        return user;
    }

    private static FamilyMembership membership(Long userId, Long familyId, String role) {
        FamilyMembership membership = new FamilyMembership();
        membership.setUserId(userId);
        membership.setFamilyId(familyId);
        membership.setRole(role);
        return membership;
    }

    private static FamilyInvite validInvite() {
        FamilyInvite invite = new FamilyInvite();
        invite.setId(1L);
        invite.setFamilyId(1L);
        invite.setEmail("invitee@b.com");
        invite.setToken("tok");
        invite.setInvitedByUserId(10L);
        invite.setExpiresAt(LocalDateTime.now().plusHours(1));
        invite.setCreatedAt(LocalDateTime.now());
        return invite;
    }

    private NewUserRegisteredEvent capturedNewUserEvent() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues().stream()
                .filter(NewUserRegisteredEvent.class::isInstance)
                .map(NewUserRegisteredEvent.class::cast)
                .findFirst()
                .orElse(null);
    }

    private User pendingLocalUser() {
        User user = new User();
        user.setId(9L);
        user.setFamilyId(4L);
        user.setEmail("a@b.com");
        user.setPasswordHash("old-hash");
        user.setDisplayName("Old Name");
        user.setActive(false);
        user.setLocked(false);
        user.setProvider("LOCAL");
        user.setVerificationToken("old-token");
        user.setVerificationTokenExpiresAt(LocalDateTime.now().minusDays(3));
        return user;
    }

    @Test
    void register_reRegistersOverPendingAccount_withNewTokenAndVerificationEmail() {
        User pending = pendingLocalUser();
        Family family = new Family();
        family.setId(4L);
        family.setName("Old Family");
        when(userDao.selectByEmail("a@b.com")).thenReturn(Optional.of(pending));
        when(familyDao.selectById(4L)).thenReturn(Optional.of(family));
        when(passwordEncoder.encode("new-password1")).thenReturn("new-hash");

        var response = authService.register(new RegisterRequest("New Family", "a@b.com", "new-password1", "New Name"));

        assertThat(response.message()).contains("Đăng ký thành công");
        assertThat(pending.getPasswordHash()).isEqualTo("new-hash");
        assertThat(pending.getDisplayName()).isEqualTo("New Name");
        assertThat(pending.getVerificationToken()).isNotBlank().isNotEqualTo("old-token");
        assertThat(pending.getVerificationTokenExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(pending.getActive()).isFalse();
        assertThat(family.getName()).isEqualTo("New Family");
        verify(familyDao).update(family);
        verify(userDao).update(pending);
        verify(userDao, never()).insert(any());
        verify(familyDao, never()).insert(any());

        ArgumentCaptor<UserVerificationEvent> captor = ArgumentCaptor.forClass(UserVerificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().verificationToken()).isEqualTo(pending.getVerificationToken());
    }

    @Test
    void register_reRegistering_doesNotRotateTokenOrResend_whenLastEmailIsStillFresh() {
        User pending = pendingLocalUser();
        pending.setVerificationTokenExpiresAt(LocalDateTime.now().plusHours(24).minusSeconds(10));
        when(userDao.selectByEmail("a@b.com")).thenReturn(Optional.of(pending));
        when(familyDao.selectById(4L)).thenReturn(Optional.empty());
        when(passwordEncoder.encode("new-password1")).thenReturn("new-hash");

        authService.register(new RegisterRequest("F", "a@b.com", "new-password1", "New Name"));

        assertThat(pending.getVerificationToken()).isEqualTo("old-token");
        assertThat(pending.getPasswordHash()).isEqualTo("new-hash");
        verify(userDao).update(pending);
        verify(eventPublisher, never()).publishEvent(any(UserVerificationEvent.class));
    }

    @Test
    void register_stillConflicts_whenExistingAccountIsVerified() {
        User verified = pendingLocalUser();
        verified.setActive(true);
        when(userDao.selectByEmail("a@b.com")).thenReturn(Optional.of(verified));

        assertThatThrownBy(() -> authService.register(new RegisterRequest("F", "a@b.com", "password1", "An")))
                .isInstanceOf(ConflictException.class);
        verify(userDao, never()).update(any());
    }

    @Test
    void register_stillConflicts_whenExistingAccountCameFromAnotherProvider() {
        User google = pendingLocalUser();
        google.setProvider("GOOGLE");
        google.setPasswordHash(null);
        when(userDao.selectByEmail("a@b.com")).thenReturn(Optional.of(google));

        assertThatThrownBy(() -> authService.register(new RegisterRequest("F", "a@b.com", "password1", "An")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void resendVerification_sendsNewTokenEmail_toPendingAccount() {
        User pending = pendingLocalUser();
        when(userDao.selectByEmail("a@b.com")).thenReturn(Optional.of(pending));

        var response = authService.resendVerification(new ResendVerificationRequest("a@b.com"));

        assertThat(response.message()).contains("Nếu tài khoản đang chờ xác thực");
        assertThat(pending.getVerificationToken()).isNotEqualTo("old-token");
        verify(userDao).update(pending);
        verify(eventPublisher).publishEvent(any(UserVerificationEvent.class));
    }

    @Test
    void resendVerification_answersTheSameAndSendsNothing_forUnknownVerifiedOrRecentlySentAccounts() {
        when(userDao.selectByEmail("nobody@b.com")).thenReturn(Optional.empty());
        User verified = pendingLocalUser();
        verified.setActive(true);
        when(userDao.selectByEmail("verified@b.com")).thenReturn(Optional.of(verified));
        User fresh = pendingLocalUser();
        fresh.setVerificationTokenExpiresAt(LocalDateTime.now().plusHours(24).minusSeconds(5));
        when(userDao.selectByEmail("fresh@b.com")).thenReturn(Optional.of(fresh));

        var unknown = authService.resendVerification(new ResendVerificationRequest("nobody@b.com"));
        var verifiedResult = authService.resendVerification(new ResendVerificationRequest("verified@b.com"));
        var freshResult = authService.resendVerification(new ResendVerificationRequest("fresh@b.com"));

        assertThat(verifiedResult.message()).isEqualTo(unknown.message());
        assertThat(freshResult.message()).isEqualTo(unknown.message());
        assertThat(fresh.getVerificationToken()).isEqualTo("old-token");
        verify(eventPublisher, never()).publishEvent(any(UserVerificationEvent.class));
    }

    @Test
    void login_unverifiedAccount_isRejectedWithTheNotVerifiedMessage() {
        User pending = pendingLocalUser();
        when(userDao.selectByEmail("a@b.com")).thenReturn(Optional.of(pending));
        when(passwordEncoder.matches("password1", "old-hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("a@b.com", "password1"), null, null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(messages.get("auth.notVerified"));
    }

    @Test
    void purgeStaleUnverifiedAccounts_removesAccountAndItsEmptyFamily() {
        User stale = pendingLocalUser();
        FamilyMembership membership = new FamilyMembership();
        membership.setUserId(9L);
        membership.setFamilyId(4L);
        membership.setRole("OWNER");
        Family family = new Family();
        family.setId(4L);
        when(userDao.selectStaleUnverified(any(LocalDateTime.class))).thenReturn(List.of(stale));
        when(familyMembershipDao.selectByUserId(9L)).thenReturn(List.of(membership));
        when(familyMembershipDao.countByFamilyId(4L)).thenReturn(1L, 0L);
        when(familyDao.selectById(4L)).thenReturn(Optional.of(family));

        int purged = authService.purgeStaleUnverifiedAccounts(7);

        assertThat(purged).isEqualTo(1);
        verify(familyMembershipDao).delete(membership);
        verify(userDao).delete(stale);
        verify(familyDao).delete(family);
    }

    @Test
    void purgeStaleUnverifiedAccounts_skipsOwnerOfFamilyThatStillHasOtherMembers() {
        User stale = pendingLocalUser();
        FamilyMembership membership = new FamilyMembership();
        membership.setUserId(9L);
        membership.setFamilyId(4L);
        membership.setRole("OWNER");
        when(userDao.selectStaleUnverified(any(LocalDateTime.class))).thenReturn(List.of(stale));
        when(familyMembershipDao.selectByUserId(9L)).thenReturn(List.of(membership));
        when(familyMembershipDao.countByFamilyId(4L)).thenReturn(3L);

        int purged = authService.purgeStaleUnverifiedAccounts(7);

        assertThat(purged).isZero();
        verify(userDao, never()).delete(any());
    }

    private User userWithResetToken(String token, LocalDateTime expiresAt) {
        User user = new User();
        user.setId(5L);
        user.setEmail("a@b.com");
        user.setDisplayName("An");
        user.setResetPasswordToken(token);
        user.setResetPasswordTokenExpiresAt(expiresAt);
        return user;
    }

    @Test
    void forgotPassword_reusesLiveToken_soTheLinkAlreadyEmailedKeepsWorking() {
        User user = userWithResetToken("live-token", LocalDateTime.now().plusMinutes(30));
        when(userDao.selectByEmail("a@b.com")).thenReturn(Optional.of(user));

        authService.forgotPassword(new ForgotPasswordRequest("a@b.com"));

        assertThat(user.getResetPasswordToken()).isEqualTo("live-token");
        verify(userDao, never()).update(any());
        ArgumentCaptor<PasswordResetEvent> captor = ArgumentCaptor.forClass(PasswordResetEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().resetToken()).isEqualTo("live-token");
    }

    @Test
    void forgotPassword_sendsNothing_whenPreviousEmailWasSentLessThanAMinuteAgo() {
        User user = userWithResetToken("fresh-token", LocalDateTime.now().plusHours(1).minusSeconds(5));
        when(userDao.selectByEmail("a@b.com")).thenReturn(Optional.of(user));

        var response = authService.forgotPassword(new ForgotPasswordRequest("a@b.com"));

        assertThat(response.message()).contains("Nếu email tồn tại");
        assertThat(user.getResetPasswordToken()).isEqualTo("fresh-token");
        verify(userDao, never()).update(any());
        verify(eventPublisher, never()).publishEvent(any(PasswordResetEvent.class));
    }

    @Test
    void forgotPassword_issuesNewToken_whenPreviousOneExpired() {
        User user = userWithResetToken("old-token", LocalDateTime.now().minusMinutes(5));
        when(userDao.selectByEmail("a@b.com")).thenReturn(Optional.of(user));

        authService.forgotPassword(new ForgotPasswordRequest("a@b.com"));

        assertThat(user.getResetPasswordToken()).isNotBlank().isNotEqualTo("old-token");
        assertThat(user.getResetPasswordTokenExpiresAt()).isAfter(LocalDateTime.now());
        verify(userDao).update(user);
        verify(eventPublisher).publishEvent(any(PasswordResetEvent.class));
    }
}
