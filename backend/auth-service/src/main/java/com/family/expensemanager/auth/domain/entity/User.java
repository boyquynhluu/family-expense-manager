package com.family.expensemanager.auth.domain.entity;

import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "USERS")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "family_id")
    private Long familyId;

    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "display_name")
    private String displayName;

    private String role;

    private Boolean active;

    @Column(name = "verification_token")
    private String verificationToken;

    @Column(name = "verification_token_expires_at")
    private LocalDateTime verificationTokenExpiresAt;

    @Column(name = "reset_password_token")
    private String resetPasswordToken;

    @Column(name = "reset_password_token_expires_at")
    private LocalDateTime resetPasswordTokenExpiresAt;

    /** {@code LOCAL}, {@code GOOGLE} or {@code FACEBOOK}. Null password_hash implies an OAuth2-only account. */
    private String provider;

    @Column(name = "provider_id")
    private String providerId;

    @Column(name = "is_system_admin")
    private Boolean isSystemAdmin;

    /** Purely informational family label (Bố, Mẹ, Anh, Chị, Em, ...) — no business logic reads it. */
    private String relationship;

    /** Base32 TOTP secret, AES-GCM encrypted at rest (see TotpSecretCipher). Set as soon as 2FA setup starts, but only enforced once totpEnabled is true. */
    @Column(name = "totp_secret")
    private String totpSecret;

    @Column(name = "totp_enabled")
    private Boolean totpEnabled;

    /** Last accepted TOTP time step; a code from that step or an older one is rejected (replay protection). */
    @Column(name = "totp_last_step")
    private Long totpLastStep;

    /** Set by a system admin; a locked account cannot log in, refresh or switch family. */
    private Boolean locked;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "pending_email")
    private String pendingEmail;

    @Column(name = "pending_email_token")
    private String pendingEmailToken;

    @Column(name = "pending_email_expires_at")
    private LocalDateTime pendingEmailExpiresAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFamilyId() {
        return familyId;
    }

    public void setFamilyId(Long familyId) {
        this.familyId = familyId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    public void setVerificationToken(String verificationToken) {
        this.verificationToken = verificationToken;
    }

    public LocalDateTime getVerificationTokenExpiresAt() {
        return verificationTokenExpiresAt;
    }

    public void setVerificationTokenExpiresAt(LocalDateTime verificationTokenExpiresAt) {
        this.verificationTokenExpiresAt = verificationTokenExpiresAt;
    }

    public String getResetPasswordToken() {
        return resetPasswordToken;
    }

    public void setResetPasswordToken(String resetPasswordToken) {
        this.resetPasswordToken = resetPasswordToken;
    }

    public LocalDateTime getResetPasswordTokenExpiresAt() {
        return resetPasswordTokenExpiresAt;
    }

    public void setResetPasswordTokenExpiresAt(LocalDateTime resetPasswordTokenExpiresAt) {
        this.resetPasswordTokenExpiresAt = resetPasswordTokenExpiresAt;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public Boolean getIsSystemAdmin() {
        return isSystemAdmin;
    }

    public void setIsSystemAdmin(Boolean isSystemAdmin) {
        this.isSystemAdmin = isSystemAdmin;
    }

    public String getRelationship() {
        return relationship;
    }

    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }

    public String getTotpSecret() {
        return totpSecret;
    }

    public void setTotpSecret(String totpSecret) {
        this.totpSecret = totpSecret;
    }

    public Boolean getTotpEnabled() {
        return totpEnabled;
    }

    public void setTotpEnabled(Boolean totpEnabled) {
        this.totpEnabled = totpEnabled;
    }

    public Long getTotpLastStep() {
        return totpLastStep;
    }

    public void setTotpLastStep(Long totpLastStep) {
        this.totpLastStep = totpLastStep;
    }

    public Boolean getLocked() {
        return locked;
    }

    public void setLocked(Boolean locked) {
        this.locked = locked;
    }

    public LocalDateTime getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(LocalDateTime lockedAt) {
        this.lockedAt = lockedAt;
    }

    public String getPendingEmail() {
        return pendingEmail;
    }

    public void setPendingEmail(String pendingEmail) {
        this.pendingEmail = pendingEmail;
    }

    public String getPendingEmailToken() {
        return pendingEmailToken;
    }

    public void setPendingEmailToken(String pendingEmailToken) {
        this.pendingEmailToken = pendingEmailToken;
    }

    public LocalDateTime getPendingEmailExpiresAt() {
        return pendingEmailExpiresAt;
    }

    public void setPendingEmailExpiresAt(LocalDateTime pendingEmailExpiresAt) {
        this.pendingEmailExpiresAt = pendingEmailExpiresAt;
    }
}
