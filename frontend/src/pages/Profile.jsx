import QRCode from "qrcode";
import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import { TrashIcon } from "../components/AppIcons";
import { EyeIcon, EyeOffIcon } from "../components/AuthIcons";
import Pagination from "../components/Pagination";
import { usePagedList } from "../hooks/usePagedList";
import { confirmDialog } from "../utils/confirm";
import { formatServerDateTime } from "../utils/format";

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

export default function Profile() {
  const { t } = useTranslation(["profile", "common"]);

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

  const { pageData: sessionsPage, setPage: setSessionsPage, reload: loadSessions } = usePagedList("/auth/sessions");
  const sessions = sessionsPage.content;
  const [revokingOthers, setRevokingOthers] = useState(false);

  const [twoFactorSetup, setTwoFactorSetup] = useState(null); // { secret, otpAuthUri, qrDataUrl }
  const [twoFactorCode, setTwoFactorCode] = useState("");
  const [twoFactorError, setTwoFactorError] = useState("");
  const [confirmingTwoFactor, setConfirmingTwoFactor] = useState(false);
  const [recoveryCodes, setRecoveryCodes] = useState(null);
  const [disablingTwoFactor, setDisablingTwoFactor] = useState(false);
  const [disablePassword, setDisablePassword] = useState("");
  const [disableError, setDisableError] = useState("");
  const [savingTwoFactor, setSavingTwoFactor] = useState(false);

  useEffect(() => {
    client.get("/auth/me").then((res) => {
      setProfile(res.data.data);
      setDisplayName(res.data.data.displayName);
      setRelationship(res.data.data.relationship ?? "");
    });
  }, []);

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
    try {
      const res = await client.post("/auth/2fa/setup");
      const { secret, otpAuthUri } = res.data.data;
      const qrDataUrl = await QRCode.toDataURL(otpAuthUri);
      setTwoFactorSetup({ secret, otpAuthUri, qrDataUrl });
      setTwoFactorCode("");
      setTwoFactorError("");
    } catch (err) {
      toast.error(err.response?.data?.message || t("twoFactorSetupStartFailed"));
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
      await client.post("/auth/2fa/disable", { password: disablePassword });
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

  async function handleInviteSubmit(e) {
    e.preventDefault();
    setInviteError("");
    setInviting(true);
    try {
      const res = await client.post("/auth/invite", { email: inviteEmail });
      toast.success(res.data.data?.message || t("inviteSentDefault", { email: inviteEmail }));
      setInviteEmail("");
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
    } catch (err) {
      setPasswordError(err.response?.data?.message || t("passwordChangeFailed"));
    } finally {
      setSavingPassword(false);
    }
  }

  if (!profile) return null;

  const isLocalAccount = profile.provider === "LOCAL";

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
            <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} required />
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
        <h2>{t("familyMembersTitle")}</h2>
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
                        <button
                          type="button"
                          className="icon-btn icon-btn-danger"
                          onClick={() => handleRemoveMember(m)}
                          aria-label={t("common:delete")}
                        >
                          <TrashIcon />
                        </button>
                      )}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <Pagination pageData={membersPage} onPageChange={setMembersPage} />

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
                  <td data-label={t("deviceHeader")}>
                    {s.deviceInfo || t("unknownDevice")}
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
        ) : twoFactorSetup ? (
          <form className="inline-form" onSubmit={handleConfirmTwoFactor}>
            <p>{t("scanQrNotice")}</p>
            <img src={twoFactorSetup.qrDataUrl} alt={t("qrCodeAlt")} width={200} height={200} />
            <p>
              {t("secretKeyLabel")} <code>{twoFactorSetup.secret}</code>
            </p>
            <label className="field">
              <span>
                {t("verificationCodeLabel")}
                <span className="required-mark" aria-hidden="true"> *</span>
              </span>
              <input
                value={twoFactorCode}
                onChange={(e) => setTwoFactorCode(e.target.value)}
                placeholder={t("sixDigitCodePlaceholder")}
                autoFocus
                required
              />
            </label>
            <button type="submit" disabled={confirmingTwoFactor}>
              {confirmingTwoFactor ? t("confirmingTwoFactorLoading") : t("confirmAndEnableButton")}
            </button>
            <button type="button" onClick={() => setTwoFactorSetup(null)}>
              {t("common:cancel")}
            </button>
            {twoFactorError && <p className="error-text">{twoFactorError}</p>}
          </form>
        ) : profile.totpEnabled ? (
          disablingTwoFactor ? (
            <form className="inline-form" onSubmit={handleDisableTwoFactor}>
              <label className="field">
                <span>
                  {t("confirmDisablePasswordLabel")}
                  <span className="required-mark" aria-hidden="true"> *</span>
                </span>
                <input
                  type="password"
                  value={disablePassword}
                  onChange={(e) => setDisablePassword(e.target.value)}
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
            <button type="button" onClick={handleStartTwoFactorSetup}>
              {t("enableTwoFactorButton")}
            </button>
          </>
        )}
      </div>
    </div>
  );
}
