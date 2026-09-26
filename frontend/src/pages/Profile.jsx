import QRCode from "qrcode";
import { useEffect, useRef, useState } from "react";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import client from "../api/client";
import { LogoutIcon, TrashIcon } from "../components/AppIcons";
import { EyeIcon, EyeOffIcon } from "../components/AuthIcons";
import Pagination from "../components/Pagination";
import TwoFactorSetupModal from "../components/TwoFactorSetupModal";
import { useAuth } from "../hooks/useAuth";
import { usePagedList } from "../hooks/usePagedList";
import { confirmDialog } from "../utils/confirm";
import { formatServerDateTime, truncate } from "../utils/format";
import { clearTokens } from "../utils/tokenStorage";
import { LIMITS } from "../utils/inputLimits";

// The stored value is always this fixed Vietnamese word regardless of UI language —
// only the displayed label is translated (see relationshipLabelFor below) — otherwise
// switching languages would change what gets saved to the DB and orphan existing data
// saved under the other language's word.
const RELATIONSHIP_OPTIONS = [
  ["Bố", "relationshipFather"],
  ["Mẹ", "relationshipMother"],
  ["Ông", "relationshipGrandfather"],
  ["Bà", "relationshipGrandmother"],
  ["Anh", "relationshipOlderBrother"],
  ["Chị", "relationshipOlderSister"],
  ["Em", "relationshipYoungerSibling"],
  ["Con", "relationshipChild"],
  ["Cháu", "relationshipGrandchild"],
  ["Chồng", "relationshipHusband"],
  ["Vợ", "relationshipWife"],
  ["Khác", "relationshipOther"],
];

// Its own component so the owner-only /auth/invites request is never fired (and rejected with 403) for regular members.
function PendingInvitesSection({ reloadSignal }) {
  const { t } = useTranslation(["profile", "common"]);
  const { pageData, page, setPage, reload } = usePagedList("/auth/invites");
  const invites = pageData.content;
  const [busyInviteId, setBusyInviteId] = useState(null);
  const handledSignal = useRef(reloadSignal);

  // A new invite lands on the first page (newest first), so jump back there.
  useEffect(() => {
    if (handledSignal.current === reloadSignal) return;
    handledSignal.current = reloadSignal;
    if (page === 0) reload();
    else setPage(0);
  }, [reloadSignal, page, reload, setPage]);

  async function handleCancelInvite(invite) {
    if (!(await confirmDialog(t("cancelInviteConfirm", { email: invite.email })))) return;
    setBusyInviteId(invite.id);
    try {
      await client.delete(`/auth/invites/${invite.id}`);
      toast.success(t("inviteCancelled"));
      reload();
    } catch (err) {
      toast.error(err.response?.data?.message || t("cancelInviteFailed"));
    } finally {
      setBusyInviteId(null);
    }
  }

  async function handleResendInvite(invite) {
    setBusyInviteId(invite.id);
    try {
      await client.post(`/auth/invites/${invite.id}/resend`);
      toast.success(t("inviteResent", { email: invite.email }));
      reload();
    } catch (err) {
      toast.error(err.response?.data?.message || t("resendInviteFailed"));
    } finally {
      setBusyInviteId(null);
    }
  }

  return (
    <div className="section-card">
      <h2>{t("pendingInvitesTitle")}</h2>
      {invites.length === 0 ? (
        <p className="empty-state">{t("noPendingInvites")}</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>{t("emailLabel")}</th>
              <th>{t("invitedAtHeader")}</th>
              <th>{t("expiresAtHeader")}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {invites.map((invite) => (
              <tr key={invite.id}>
                <td data-label={t("emailLabel")}>{invite.email}</td>
                <td data-label={t("invitedAtHeader")}>{formatServerDateTime(invite.createdAt)}</td>
                <td data-label={t("expiresAtHeader")}>{formatServerDateTime(invite.expiresAt)}</td>
                <td className="row-actions">
                  <button
                    type="button"
                    className="btn-secondary"
                    onClick={() => handleResendInvite(invite)}
                    disabled={busyInviteId === invite.id}
                  >
                    {t("resendInviteButton")}
                  </button>
                  <button
                    type="button"
                    className="btn-secondary"
                    onClick={() => handleCancelInvite(invite)}
                    disabled={busyInviteId === invite.id}
                  >
                    {t("cancelInviteButton")}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <Pagination pageData={pageData} onPageChange={setPage} />
    </div>
  );
}

export default function Profile() {
  const { t } = useTranslation(["profile", "common"]);
  const { familyId, loginWithTokens } = useAuth();

  function relationshipLabelFor(value) {
    const entry = RELATIONSHIP_OPTIONS.find(([v]) => v === value);
    return entry ? t(entry[1]) : value;
  }

  const [profile, setProfile] = useState(null);
  const [displayName, setDisplayName] = useState("");
  const [relationship, setRelationship] = useState("");
  const [profileError, setProfileError] = useState("");
  const [savingProfile, setSavingProfile] = useState(false);

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [showCurrentPassword, setShowCurrentPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [passwordError, setPasswordError] = useState("");
  const [savingPassword, setSavingPassword] = useState(false);

  const { pageData: membersPage, setPage: setMembersPage, reload: loadMembers } = usePagedList("/auth/family/members");
  const members = membersPage.content;
  const [inviteEmail, setInviteEmail] = useState("");
  const [inviteError, setInviteError] = useState("");
  const [inviting, setInviting] = useState(false);
  const [invitesReloadSignal, setInvitesReloadSignal] = useState(0);
  const [familyName, setFamilyName] = useState("");
  const [renamingFamily, setRenamingFamily] = useState(false);
  const [leavingFamily, setLeavingFamily] = useState(false);

  const { pageData: sessionsPage, setPage: setSessionsPage, reload: loadSessions } = usePagedList("/auth/sessions");
  const sessions = sessionsPage.content;
  const [revokingOthers, setRevokingOthers] = useState(false);

  const [twoFactorSetup, setTwoFactorSetup] = useState(null); // { secret, otpAuthUri, qrDataUrl }
  const [twoFactorCode, setTwoFactorCode] = useState("");
  const [twoFactorError, setTwoFactorError] = useState("");
  const [confirmingTwoFactor, setConfirmingTwoFactor] = useState(false);
  const [startingTwoFactor, setStartingTwoFactor] = useState(false);
  const [recoveryCodes, setRecoveryCodes] = useState(null);
  const [disablingTwoFactor, setDisablingTwoFactor] = useState(false);
  const [disablePassword, setDisablePassword] = useState("");
  const [disableError, setDisableError] = useState("");
  const [savingTwoFactor, setSavingTwoFactor] = useState(false);

  const [newEmail, setNewEmail] = useState("");
  const [emailCredential, setEmailCredential] = useState("");
  const [emailError, setEmailError] = useState("");
  const [changingEmail, setChangingEmail] = useState(false);

  const [exporting, setExporting] = useState(false);

  const [deleteCredential, setDeleteCredential] = useState("");
  const [deleteError, setDeleteError] = useState("");
  const [deletingAccount, setDeletingAccount] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    client.get("/auth/me").then((res) => {
      setProfile(res.data.data);
      setDisplayName(res.data.data.displayName);
      setRelationship(res.data.data.relationship ?? "");
    });
  }, []);

  useEffect(() => {
    client.get("/auth/my-families").then((res) => {
      const current = res.data.data.find((f) => f.familyId === familyId);
      setFamilyName(current?.familyName ?? "");
    });
  }, [familyId]);

  async function handleRevokeSession(session) {
    if (!(await confirmDialog(t("revokeSessionConfirm")))) return;
    try {
      await client.delete(`/auth/sessions/${session.id}`);
      toast.success(t("sessionRevoked"));
      loadSessions();
    } catch (err) {
      toast.error(err.response?.data?.message || t("revokeSessionFailed"));
    }
  }

  async function handleRevokeOtherSessions() {
    if (!(await confirmDialog(t("revokeOtherSessionsConfirm")))) return;
    setRevokingOthers(true);
    try {
      await client.post("/auth/sessions/revoke-others");
      toast.success(t("otherSessionsRevoked"));
      loadSessions();
    } catch (err) {
      toast.error(err.response?.data?.message || t("actionFailed"));
    } finally {
      setRevokingOthers(false);
    }
  }

  async function handleStartTwoFactorSetup() {
    setStartingTwoFactor(true);
    try {
      const res = await client.post("/auth/2fa/setup");
      const { secret, otpAuthUri } = res.data.data;
      const qrDataUrl = await QRCode.toDataURL(otpAuthUri);
      setTwoFactorSetup({ secret, otpAuthUri, qrDataUrl });
      setTwoFactorCode("");
      setTwoFactorError("");
    } catch (err) {
      toast.error(err.response?.data?.message || t("twoFactorSetupStartFailed"));
    } finally {
      setStartingTwoFactor(false);
    }
  }

  async function handleConfirmTwoFactor(e) {
    e.preventDefault();
    setTwoFactorError("");
    setConfirmingTwoFactor(true);
    try {
      const res = await client.post("/auth/2fa/confirm", { code: twoFactorCode });
      setRecoveryCodes(res.data.data.recoveryCodes);
      setTwoFactorSetup(null);
      setProfile((p) => ({ ...p, totpEnabled: true }));
    } catch (err) {
      setTwoFactorError(err.response?.data?.message || t("twoFactorCodeInvalid"));
    } finally {
      setConfirmingTwoFactor(false);
    }
  }

  async function handleDisableTwoFactor(e) {
    e.preventDefault();
    setDisableError("");
    setSavingTwoFactor(true);
    try {
      // Accounts without a password (Google-only) confirm with a TOTP/recovery code instead.
      const hasPassword = profile.hasPassword !== false;
      await client.post("/auth/2fa/disable", hasPassword ? { password: disablePassword } : { code: disablePassword });
      setProfile((p) => ({ ...p, totpEnabled: false }));
      setDisablingTwoFactor(false);
      setDisablePassword("");
      toast.success(t("twoFactorDisabled"));
    } catch (err) {
      setDisableError(err.response?.data?.message || t("disableTwoFactorFailed"));
    } finally {
      setSavingTwoFactor(false);
    }
  }

  function formatDateTime(value) {
    if (!value) return t("notAvailable");
    return formatServerDateTime(value);
  }

  async function handleRemoveMember(member) {
    if (!(await confirmDialog(t("removeMemberConfirm", { name: member.displayName })))) return;
    try {
      await client.delete(`/auth/family/members/${member.id}`);
      toast.success(t("memberRemoved"));
      loadMembers();
    } catch (err) {
      toast.error(err.response?.data?.message || t("removeMemberFailed"));
    }
  }

  async function handleRenameFamily(e) {
    e.preventDefault();
    setRenamingFamily(true);
    try {
      const res = await client.put("/auth/family", { name: familyName.trim() });
      setFamilyName(familyName.trim());
      toast.success(res.data.data?.message || t("familyRenamed"));
    } catch (err) {
      toast.error(err.response?.data?.message || t("renameFamilyFailed"));
    } finally {
      setRenamingFamily(false);
    }
  }

  async function handleTransferOwnership(member) {
    if (!(await confirmDialog(t("transferOwnershipConfirm", { name: member.displayName })))) return;
    try {
      const res = await client.post("/auth/family/transfer-ownership", { userId: member.id });
      // The role claim in the old token would still say OWNER, so swap in the fresh tokens.
      loginWithTokens(res.data.data.accessToken, res.data.data.refreshToken);
      toast.success(t("ownershipTransferred"));
      const me = await client.get("/auth/me");
      setProfile(me.data.data);
      loadMembers();
    } catch (err) {
      toast.error(err.response?.data?.message || t("transferOwnershipFailed"));
    }
  }

  async function handleLeaveFamily() {
    if (!(await confirmDialog(t("leaveFamilyConfirm")))) return;
    setLeavingFamily(true);
    try {
      const res = await client.post("/auth/family/leave");
      loginWithTokens(res.data.data.accessToken, res.data.data.refreshToken);
      // Everything on screen was loaded under the family just left, so a full reload refetches it all.
      window.location.href = "/";
    } catch (err) {
      toast.error(err.response?.data?.message || t("leaveFamilyFailed"));
      setLeavingFamily(false);
    }
  }

  async function handleInviteSubmit(e) {
    e.preventDefault();
    setInviteError("");
    setInviting(true);
    try {
      const res = await client.post("/auth/invite", { email: inviteEmail });
      toast.success(res.data.data?.message || t("inviteSentDefault", { email: inviteEmail }));
      setInviteEmail("");
      setInvitesReloadSignal((n) => n + 1);
    } catch (err) {
      setInviteError(err.response?.data?.message || t("inviteFailed"));
    } finally {
      setInviting(false);
    }
  }

  async function handleProfileSubmit(e) {
    e.preventDefault();
    setProfileError("");
    setSavingProfile(true);
    try {
      const res = await client.put("/auth/me", { displayName, relationship: relationship || null });
      setProfile(res.data.data);
      loadMembers();
      toast.success(t("profileUpdateSuccess"));
    } catch (err) {
      setProfileError(err.response?.data?.message || t("profileUpdateFailed"));
    } finally {
      setSavingProfile(false);
    }
  }

  async function handlePasswordSubmit(e) {
    e.preventDefault();
    setPasswordError("");
    setSavingPassword(true);
    try {
      await client.put("/auth/me/password", { currentPassword, newPassword });
      setCurrentPassword("");
      setNewPassword("");
      toast.success(t("passwordChangeSuccess"));
      navigate("/login");
    } catch (err) {
      setPasswordError(err.response?.data?.message || t("passwordChangeFailed"));
    } finally {
      setSavingPassword(false);
    }
  }

  async function handleChangeEmailSubmit(e) {
    e.preventDefault();
    setEmailError("");
    setChangingEmail(true);
    try {
      const email = newEmail.trim();
      await client.post("/auth/me/email", { newEmail: email, ...credentialBody(emailCredential) });
      toast.success(t("changeEmailSent", { email }));
      setNewEmail("");
      setEmailCredential("");
    } catch (err) {
      setEmailError(err.response?.data?.message || t("changeEmailFailed"));
    } finally {
      setChangingEmail(false);
    }
  }

  async function handleExportData() {
    setExporting(true);
    try {
      const res = await client.get("/auth/me/export");
      const blob = new Blob([JSON.stringify(res.data.data, null, 2)], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = "du-lieu-ca-nhan.json";
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
      toast.success(t("exportSuccess"));
    } catch (err) {
      toast.error(err.response?.data?.message || t("exportFailed"));
    } finally {
      setExporting(false);
    }
  }

  async function handleDeleteAccount(e) {
    e.preventDefault();
    setDeleteError("");
    const confirmed = await confirmDialog(t("deleteAccountConfirm"), {
      title: t("deleteAccountConfirmTitle"),
      confirmButtonText: t("deleteAccountConfirmButton"),
    });
    if (!confirmed) return;
    setDeletingAccount(true);
    try {
      await client.delete("/auth/me", { data: credentialBody(deleteCredential) });
      // The account and its sessions are gone server-side, so there is nothing left to log out of.
      clearTokens();
      window.location.href = "/login";
    } catch (err) {
      setDeleteError(err.response?.data?.message || t("deleteAccountFailed"));
      setDeletingAccount(false);
    }
  }

  if (!profile) return null;

  const isLocalAccount = profile.provider === "LOCAL";
  const hasPassword = profile.hasPassword !== false;
  const canReauthenticate = hasPassword || profile.totpEnabled;

  // Sensitive actions re-check the password, or (for accounts without one) a live 2FA code.
  function credentialBody(value) {
    return hasPassword ? { password: value } : { code: value };
  }

  function renderCredentialField(value, onChange) {
    return hasPassword ? (
      <label className="field">
        <span>
          {t("currentPasswordLabel")}
          <span className="required-mark" aria-hidden="true"> *</span>
        </span>
        <input
          type="password"
          value={value}
          maxLength={LIMITS.password}
          onChange={(e) => onChange(e.target.value)}
          autoComplete="current-password"
          required
        />
      </label>
    ) : (
      <label className="field">
        <span>
          {t("verificationCodeLabel")}
          <span className="required-mark" aria-hidden="true"> *</span>
        </span>
        <input
          value={value}
          maxLength={LIMITS.totpCode}
          onChange={(e) => onChange(e.target.value)}
          placeholder={t("sixDigitCodePlaceholder")}
          inputMode="numeric"
          autoComplete="one-time-code"
          required
        />
      </label>
    );
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>{t("title")}</h1>
          <p className="page-header-subtitle">{t("subtitle")}</p>
        </div>
      </div>

      <div className="section-card">
        <h2>{t("personalInfoTitle")}</h2>
        <form className="inline-form" onSubmit={handleProfileSubmit}>
          <label className="field">
            {t("emailLabel")}
            <input value={profile.email} disabled />
          </label>
          <label className="field">
            <span>
              {t("displayNameLabel")}
              <span className="required-mark" aria-hidden="true"> *</span>
            </span>
            <input value={displayName} maxLength={LIMITS.displayName} onChange={(e) => setDisplayName(e.target.value)} required />
          </label>
          <label className="field">
            {t("relationshipLabel")}
            <select value={relationship} onChange={(e) => setRelationship(e.target.value)}>
              <option value="">{t("relationshipNone")}</option>
              {RELATIONSHIP_OPTIONS.map(([value, key]) => (
                <option key={value} value={value}>
                  {t(key)}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            {t("roleLabel")}
            <input value={profile.role} disabled />
          </label>
          <button type="submit" disabled={savingProfile}>
            {savingProfile ? t("common:saving") : t("saveChanges")}
          </button>
        </form>
        {profileError && <p className="error-text">{profileError}</p>}
      </div>

      <div className="section-card">
        <h2>{t("changePasswordTitle")}</h2>
        {!isLocalAccount ? (
          <p className="empty-state">
            {t("oauthNoPasswordNotice", { provider: profile.provider })}
          </p>
        ) : (
          <form className="inline-form" onSubmit={handlePasswordSubmit}>
            <label className="field">
              <span>
                {t("currentPasswordLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <div className="password-field-wrapper">
                <input
                  type={showCurrentPassword ? "text" : "password"}
                  value={currentPassword}
                  maxLength={LIMITS.password}
                  onChange={(e) => setCurrentPassword(e.target.value)}
                  required
                />
                <button
                  type="button"
                  className="password-toggle-btn"
                  onClick={() => setShowCurrentPassword((v) => !v)}
                  aria-label={showCurrentPassword ? t("hidePassword") : t("showPassword")}
                >
                  {showCurrentPassword ? <EyeOffIcon /> : <EyeIcon />}
                </button>
              </div>
            </label>
            <label className="field">
              <span>
                {t("newPasswordLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <div className="password-field-wrapper">
                <input
                  type={showNewPassword ? "text" : "password"}
                  value={newPassword}
                  minLength={LIMITS.newPasswordMin}
                  maxLength={LIMITS.newPasswordMax}
                  onChange={(e) => setNewPassword(e.target.value)}
                  minLength={8}
                  required
                />
                <button
                  type="button"
                  className="password-toggle-btn"
                  onClick={() => setShowNewPassword((v) => !v)}
                  aria-label={showNewPassword ? t("hidePassword") : t("showPassword")}
                >
                  {showNewPassword ? <EyeOffIcon /> : <EyeIcon />}
                </button>
              </div>
            </label>
            <button type="submit" disabled={savingPassword}>
              {savingPassword ? t("common:saving") : t("changePasswordButton")}
            </button>
          </form>
        )}
        {passwordError && <p className="error-text">{passwordError}</p>}
      </div>

      <div className="section-card">
        <h2>{t("changeEmailTitle")}</h2>
        <p>{t("changeEmailNotice")}</p>
        {!canReauthenticate ? (
          <p className="empty-state">{t("reauthNeedsTwoFactor", { provider: profile.provider })}</p>
        ) : (
          <form className="inline-form" onSubmit={handleChangeEmailSubmit}>
            <label className="field">
              <span>
                {t("newEmailLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <input
                type="email"
                value={newEmail}
                maxLength={LIMITS.email}
                onChange={(e) => setNewEmail(e.target.value)}
                placeholder={t("emailPlaceholderExample")}
                required
              />
            </label>
            {renderCredentialField(emailCredential, setEmailCredential)}
            <button type="submit" disabled={changingEmail}>
              {changingEmail ? t("sendingChangeEmail") : t("changeEmailButton")}
            </button>
          </form>
        )}
        {emailError && <p className="error-text">{emailError}</p>}
      </div>

      <div className="section-card">
        <h2>{t("familyMembersTitle")}</h2>
        {profile.role === "OWNER" && (
          <form className="inline-form" onSubmit={handleRenameFamily}>
            <label className="field">
              <span>
                {t("familyNameLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <input value={familyName} onChange={(e) => setFamilyName(e.target.value)} maxLength={LIMITS.familyName} required />
            </label>
            <button type="submit" disabled={renamingFamily || !familyName.trim()}>
              {renamingFamily ? t("common:saving") : t("renameFamilyButton")}
            </button>
          </form>
        )}
        {members.length === 0 ? (
          <p className="empty-state">{t("noMembers")}</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>{t("displayNameLabel")}</th>
                <th>{t("emailLabel")}</th>
                <th>{t("roleLabel")}</th>
                <th>{t("relationshipColumnHeader")}</th>
                {profile.role === "OWNER" && <th></th>}
              </tr>
            </thead>
            <tbody>
              {members.map((m) => (
                <tr key={m.id}>
                  <td data-label={t("displayNameLabel")}>{m.displayName}</td>
                  <td data-label={t("emailLabel")}>{m.email}</td>
                  <td data-label={t("roleLabel")}>{m.role}</td>
                  <td data-label={t("relationshipColumnHeader")}>
                    {m.relationship ? relationshipLabelFor(m.relationship) : t("notAvailable")}
                  </td>
                  {profile.role === "OWNER" && (
                    <td className="row-actions">
                      {m.role !== "OWNER" && (
                        <>
                          <button type="button" className="btn-secondary" onClick={() => handleTransferOwnership(m)}>
                            {t("transferOwnershipButton")}
                          </button>
                          <button
                            type="button"
                            className="icon-btn icon-btn-danger"
                            onClick={() => handleRemoveMember(m)}
                            aria-label={t("common:delete")}
                          >
                            <TrashIcon />
                          </button>
                        </>
                      )}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <Pagination pageData={membersPage} onPageChange={setMembersPage} />

        {profile.role !== "OWNER" && (
          <div className="leave-family-action">
            <button type="button" className="btn-outline-warning" onClick={handleLeaveFamily} disabled={leavingFamily}>
              <LogoutIcon />
              {leavingFamily ? t("common:saving") : t("leaveFamilyButton")}
            </button>
          </div>
        )}

        {profile.role === "OWNER" && (
          <>
            <h3>{t("inviteMemberTitle")}</h3>
            <form className="inline-form" onSubmit={handleInviteSubmit}>
              <label className="field">
                <span>
                  {t("emailLabel")}
                  <span className="required-mark" aria-hidden="true"> *</span>
                </span>
                <input
                  type="email"
                  value={inviteEmail}
                  maxLength={LIMITS.email}
                  onChange={(e) => setInviteEmail(e.target.value)}
                  placeholder={t("emailPlaceholderExample")}
                  required
                />
              </label>
              <button type="submit" disabled={inviting}>
                {inviting ? t("sendingInvite") : t("sendInviteButton")}
              </button>
            </form>
            {inviteError && <p className="error-text">{inviteError}</p>}
          </>
        )}
      </div>

      {profile.role === "OWNER" && <PendingInvitesSection reloadSignal={invitesReloadSignal} />}

      <div className="section-card">
        <h2>{t("sessionsTitle")}</h2>
        {sessions.length === 0 ? (
          <p className="empty-state">{t("noSessions")}</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>{t("deviceHeader")}</th>
                <th>{t("ipAddressHeader")}</th>
                <th>{t("loginAtHeader")}</th>
                <th>{t("lastActiveHeader")}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {sessions.map((s) => (
                <tr key={s.id}>
                  <td data-label={t("deviceHeader")} title={s.deviceInfo || undefined}>
                    {truncate(s.deviceInfo, 50) || t("unknownDevice")}
                    {s.isCurrent && <span className="badge">{t("currentSessionBadge")}</span>}
                  </td>
                  <td data-label={t("ipAddressHeader")}>{s.ipAddress || t("notAvailable")}</td>
                  <td data-label={t("loginAtHeader")}>{formatDateTime(s.createdAt)}</td>
                  <td data-label={t("lastActiveHeader")}>{formatDateTime(s.lastUsedAt)}</td>
                  <td className="row-actions">
                    {!s.isCurrent && (
                      <button
                        type="button"
                        className="icon-btn icon-btn-danger"
                        onClick={() => handleRevokeSession(s)}
                        aria-label={t("revokeSessionAriaLabel")}
                      >
                        <TrashIcon />
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <Pagination pageData={sessionsPage} onPageChange={setSessionsPage} />
        {(sessionsPage.totalElements > 1 || sessions.some((s) => !s.isCurrent)) && (
          <button type="button" onClick={handleRevokeOtherSessions} disabled={revokingOthers}>
            {revokingOthers ? t("revokingOthersLoading") : t("revokeOtherSessionsButton")}
          </button>
        )}
      </div>

      <div className="section-card">
        <h2>{t("twoFactorTitle")}</h2>

        {recoveryCodes ? (
          <>
            <p>
              {t("recoveryCodesNotice")}
            </p>
            <ul className="recovery-codes-list">
              {recoveryCodes.map((code) => (
                <li key={code}>
                  <code>{code}</code>
                </li>
              ))}
            </ul>
            <button type="button" onClick={() => setRecoveryCodes(null)}>
              {t("recoveryCodesSavedButton")}
            </button>
          </>
        ) : profile.totpEnabled ? (
          disablingTwoFactor ? (
            <form className="inline-form" onSubmit={handleDisableTwoFactor}>
              <label className="field">
                <span>
                  {hasPassword ? t("confirmDisablePasswordLabel") : t("confirmDisableCodeLabel")}
                  <span className="required-mark" aria-hidden="true"> *</span>
                </span>
                <input
                  type={hasPassword ? "password" : "text"}
                  value={disablePassword}
                  maxLength={hasPassword ? LIMITS.password : LIMITS.twoFactorCode}
                  onChange={(e) => setDisablePassword(e.target.value)}
                  placeholder={hasPassword ? undefined : t("codeOrRecoveryPlaceholder")}
                  autoComplete={hasPassword ? "current-password" : "one-time-code"}
                  required
                />
              </label>
              <button type="submit" disabled={savingTwoFactor}>
                {savingTwoFactor ? t("disablingTwoFactorLoading") : t("confirmDisableButton")}
              </button>
              <button type="button" onClick={() => setDisablingTwoFactor(false)}>
                {t("common:cancel")}
              </button>
              {disableError && <p className="error-text">{disableError}</p>}
            </form>
          ) : (
            <>
              <p>{t("twoFactorStatusPrefix")} <strong>{t("enabledWord")}</strong> {t("twoFactorStatusEnabledSuffix")}</p>
              <button type="button" onClick={() => setDisablingTwoFactor(true)}>
                {t("disableTwoFactorButton")}
              </button>
            </>
          )
        ) : (
          <>
            <p>{t("twoFactorStatusPrefix")} <strong>{t("disabledWord")}</strong> {t("twoFactorStatusDisabledSuffix")}</p>
            <button type="button" onClick={handleStartTwoFactorSetup} disabled={startingTwoFactor}>
              {startingTwoFactor ? t("startingTwoFactorLoading") : t("enableTwoFactorButton")}
            </button>
          </>
        )}
      </div>

      <TwoFactorSetupModal
        setup={twoFactorSetup}
        code={twoFactorCode}
        onCodeChange={setTwoFactorCode}
        onSubmit={handleConfirmTwoFactor}
        onClose={() => setTwoFactorSetup(null)}
        confirming={confirmingTwoFactor}
        error={twoFactorError}
      />

      <div className="section-card">
        <h2>{t("exportDataTitle")}</h2>
        <p>{t("exportDataNotice")}</p>
        <button type="button" onClick={handleExportData} disabled={exporting}>
          {exporting ? t("exportingData") : t("exportDataButton")}
        </button>
      </div>

      <div className="section-card">
        <h2>{t("deleteAccountTitle")}</h2>
        <p>{t("deleteAccountNotice")}</p>
        {!canReauthenticate ? (
          <p className="empty-state">{t("reauthNeedsTwoFactor", { provider: profile.provider })}</p>
        ) : (
          <form className="inline-form" onSubmit={handleDeleteAccount}>
            {renderCredentialField(deleteCredential, setDeleteCredential)}
            <button type="submit" disabled={deletingAccount} style={{ background: "#dc2626" }}>
              {deletingAccount ? t("deletingAccountLoading") : t("deleteAccountButton")}
            </button>
          </form>
        )}
        {deleteError && <p className="error-text">{deleteError}</p>}
      </div>
    </div>
  );
}
