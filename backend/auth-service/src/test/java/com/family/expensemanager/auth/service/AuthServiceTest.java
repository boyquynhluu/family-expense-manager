package com.family.expensemanager.auth.service;

import com.family.expensemanager.auth.dao.FamilyDao;
import com.family.expensemanager.auth.dao.FamilyInviteDao;
import com.family.expensemanager.auth.dao.RefreshTokenDao;
import com.family.expensemanager.auth.dao.UserDao;
import com.family.expensemanager.auth.domain.entity.Family;
import com.family.expensemanager.auth.domain.entity.FamilyInvite;
import com.family.expensemanager.auth.domain.entity.User;
import com.family.expensemanager.auth.dto.AcceptInviteRequest;
import com.family.expensemanager.auth.dto.ChangePasswordRequest;
import com.family.expensemanager.auth.dto.ForgotPasswordRequest;
import com.family.expensemanager.auth.dto.InviteMemberRequest;
import com.family.expensemanager.auth.dto.LoginRequest;
import com.family.expensemanager.auth.dto.RegisterRequest;
import com.family.expensemanager.auth.dto.ResetPasswordRequest;
import com.family.expensemanager.auth.dto.UpdateProfileRequest;
import com.family.expensemanager.common.event.FamilyInviteEvent;
import com.family.expensemanager.common.event.PasswordResetEvent;
import com.family.expensemanager.common.event.UserVerificationEvent;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.ConflictException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.common.exception.UnauthorizedException;
import com.family.expensemanager.common.security.JwtUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
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
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                familyDao, userDao, refreshTokenDao, familyInviteDao, passwordEncoder, jwtUtil, eventPublisher,
                15, 7, 24, 1, 72);
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

        verify(eventPublisher).publishEvent(any(UserVerificationEvent.class));
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

        var response = authService.login(new LoginRequest("a@b.com", "password1"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
        verify(refreshTokenDao).insert(any());
    }

    @Test
    void login_throwsUnauthorized_whenPasswordWrong() {
        User user = activeLocalUser();
        when(userDao.selectByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("a@b.com", "wrong")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void login_throwsUnauthorized_whenAccountNotActive() {
        User user = activeLocalUser();
        user.setActive(false);
        when(userDao.selectByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password1", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("a@b.com", "password1")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void login_throwsUnauthorized_whenOAuth2OnlyAccount() {
        User user = activeLocalUser();
        user.setPasswordHash(null);
        user.setProvider("GOOGLE");
        when(userDao.selectByEmail("a@b.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("a@b.com", "password1")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void login_throwsUnauthorized_whenEmailUnknown() {
        when(userDao.selectByEmail("nobody@b.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@b.com", "password1")))
                .isInstanceOf(UnauthorizedException.class);
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
    void updateProfile_updatesDisplayName() {
        User user = activeLocalUser();
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));

        var response = authService.updateProfile(1L, new UpdateProfileRequest("Tên Mới"));

        assertThat(response.displayName()).isEqualTo("Tên Mới");
        verify(userDao).update(user);
    }

    @Test
    void inviteMember_throwsConflict_whenEmailAlreadyRegistered() {
        when(userDao.selectByEmail("existing@b.com")).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> authService.inviteMember(1L, 10L, new InviteMemberRequest("existing@b.com")))
                .isInstanceOf(ConflictException.class);
        verify(familyInviteDao, never()).insert(any());
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
    void acceptInvite_createsMemberUser_andMarksInviteAccepted_whenValid() {
        FamilyInvite invite = validInvite();
        when(familyInviteDao.selectByToken("tok")).thenReturn(Optional.of(invite));
        when(userDao.selectByEmail("invitee@b.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password1")).thenReturn("hashed");

        authService.acceptInvite("tok", new AcceptInviteRequest("Invitee", "password1"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userDao).insert(userCaptor.capture());
        assertThat(userCaptor.getValue().getRole()).isEqualTo("MEMBER");
        assertThat(userCaptor.getValue().getFamilyId()).isEqualTo(invite.getFamilyId());

        assertThat(invite.getAcceptedAt()).isNotNull();
        verify(familyInviteDao).update(invite);
    }

    @Test
    void acceptInvite_throwsConflict_whenEmailAlreadyRegistered() {
        FamilyInvite invite = validInvite();
        when(familyInviteDao.selectByToken("tok")).thenReturn(Optional.of(invite));
        when(userDao.selectByEmail("invitee@b.com")).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> authService.acceptInvite("tok", new AcceptInviteRequest("Invitee", "password1")))
                .isInstanceOf(ConflictException.class);
        verify(userDao, never()).insert(any());
        verify(familyInviteDao, never()).update(any());
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
}
