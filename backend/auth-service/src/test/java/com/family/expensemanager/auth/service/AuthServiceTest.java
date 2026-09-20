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
import com.family.expensemanager.auth.dto.ChangePasswordRequest;
import com.family.expensemanager.auth.dto.ForgotPasswordRequest;
import com.family.expensemanager.auth.dto.InviteMemberRequest;
import com.family.expensemanager.auth.dto.LoginRequest;
import com.family.expensemanager.auth.dto.RegisterRequest;
import com.family.expensemanager.auth.dto.RenameFamilyRequest;
import com.family.expensemanager.auth.dto.ResetPasswordRequest;
import com.family.expensemanager.auth.dto.TransferOwnershipRequest;
import com.family.expensemanager.auth.dto.TwoFactorConfirmResponse;
import com.family.expensemanager.auth.dto.TwoFactorSetupResponse;
import com.family.expensemanager.auth.dto.UpdateProfileRequest;
import com.family.expensemanager.auth.security.TotpService;
import com.family.expensemanager.auth.security.TwoFactorChallengeStore;
import com.family.expensemanager.common.event.FamilyInviteEvent;
import com.family.expensemanager.common.event.PasswordResetEvent;
import com.family.expensemanager.common.event.UserVerificationEvent;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
    private ApplicationEventPublisher eventPublisher;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                familyDao, userDao, refreshTokenDao, familyInviteDao, familyMembershipDao, twoFactorRecoveryCodeDao,
                passwordEncoder, jwtUtil, revokedSessionStore, totpService, twoFactorChallengeStore, eventPublisher,
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

        var response = authService.login(new LoginRequest("a@b.com", "password1"), null, null);

        assertThat(response.requiresTwoFactor()).isFalse();
        assertThat(response.tokens().accessToken()).isEqualTo("access-token");
        assertThat(response.tokens().refreshToken()).isNotBlank();
        verify(refreshTokenDao).insert(any());
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
    }

    @Test
    void login_throwsUnauthorized_whenPasswordWrong() {
        User user = activeLocalUser();
        when(userDao.selectByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("a@b.com", "wrong"), null, null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void login_throwsUnauthorized_whenAccountNotActive() {
        User user = activeLocalUser();
        user.setActive(false);
        when(userDao.selectByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password1", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("a@b.com", "password1"), null, null))
                .isInstanceOf(UnauthorizedException.class);
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
        assertThat(user.getTotpSecret()).isEqualTo("SECRET123");
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
        when(totpService.verifyCode("SECRET123", "123456")).thenReturn(true);
        when(passwordEncoder.encode(any())).thenReturn("hashed-code");

        TwoFactorConfirmResponse response = authService.confirmTwoFactor(1L, "123456");

        assertThat(user.getTotpEnabled()).isTrue();
        assertThat(response.recoveryCodes()).hasSize(8);
        verify(twoFactorRecoveryCodeDao).deleteByUserId(1L);
        verify(twoFactorRecoveryCodeDao, times(8)).insert(any());
    }

    @Test
    void confirmTwoFactor_throwsBadRequest_whenCodeInvalid() {
        User user = activeLocalUser();
        user.setTotpSecret("SECRET123");
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));
        when(totpService.verifyCode("SECRET123", "000000")).thenReturn(false);

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

        authService.disableTwoFactor(1L, "password1");

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

        assertThatThrownBy(() -> authService.disableTwoFactor(1L, "wrong")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void verifyTwoFactorLogin_issuesTokens_whenTotpCodeValid() {
        User user = activeLocalUser();
        user.setTotpSecret("SECRET123");
        when(twoFactorChallengeStore.consumeChallenge("challenge-token")).thenReturn(Optional.of(1L));
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));
        when(totpService.verifyCode("SECRET123", "123456")).thenReturn(true);
        when(jwtUtil.generateToken(any(), any(), anyLong())).thenReturn("access-token");

        var response = authService.verifyTwoFactorLogin("challenge-token", "123456", null, null);

        assertThat(response.accessToken()).isEqualTo("access-token");
        verify(refreshTokenDao).insert(any());
    }

    @Test
    void verifyTwoFactorLogin_fallsBackToRecoveryCode_whenTotpCodeWrong() {
        User user = activeLocalUser();
        user.setTotpSecret("SECRET123");
        when(twoFactorChallengeStore.consumeChallenge("challenge-token")).thenReturn(Optional.of(1L));
        when(userDao.selectById(1L)).thenReturn(Optional.of(user));
        when(totpService.verifyCode("SECRET123", "recover1")).thenReturn(false);
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
        when(totpService.verifyCode("SECRET123", "000000")).thenReturn(false);
        when(twoFactorRecoveryCodeDao.selectUnusedByUserId(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> authService.verifyTwoFactorLogin("challenge-token", "000000", null, null))
                .isInstanceOf(UnauthorizedException.class);
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

    private static RefreshToken refreshToken(Long id, Long userId) {
        RefreshToken token = new RefreshToken();
        token.setId(id);
        token.setUserId(userId);
        token.setRevoked(false);
        token.setExpiresAt(LocalDateTime.now().plusDays(7));
        token.setCreatedAt(LocalDateTime.now());
        return token;
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
}
