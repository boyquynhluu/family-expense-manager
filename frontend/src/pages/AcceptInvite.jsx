import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import { Trans, useTranslation } from "react-i18next";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import client from "../api/client";
import { EyeIcon, EyeOffIcon, KeyIcon, UserIcon } from "../components/AuthIcons";
import LanguageSwitcher from "../components/LanguageSwitcher";

export default function AcceptInvite() {
  const { t } = useTranslation("acceptInvite");
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get("token");

  const [invite, setInvite] = useState(null);
  const [loadError, setLoadError] = useState(token ? "" : t("missingToken"));
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!token) return;
    client
      .get(`/auth/invite/${token}`)
      .then((res) => setInvite(res.data.data))
      .catch((err) => setLoadError(err.response?.data?.message || t("invalidInvite")));
  }, [token]);

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      // An email that already has an account just needs the token to prove they own
      // it — no password/displayName to set, since the account already has both (see
      // AuthService#acceptInvite).
      const payload = invite.isExistingAccount ? {} : { displayName, password };
      const res = await client.post(`/auth/invite/${token}/accept`, payload);
      toast.success(res.data.data?.message || t("joinSuccess"), {
        duration: 6000,
      });
      navigate("/login", { replace: true });
    } catch (err) {
      setError(err.response?.data?.message || t("joinFailed"));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-page">
      <LanguageSwitcher variant="floating" />
      <form className="auth-form" onSubmit={handleSubmit}>
        <h1 className="auth-title">{t("title")}</h1>

        <div className="auth-card">
          {invite ? (
            <>
              <p className="auth-card-title">{t("welcome")}</p>
              <p className="auth-card-subtitle">
                <Trans
                  i18nKey="invitedSubtitle"
                  t={t}
                  values={{ familyName: invite.familyName, email: invite.email }}
                  components={{ strong: <strong /> }}
                />
              </p>
            </>
          ) : (
            <p className="auth-card-subtitle">{t("checkingInvite")}</p>
          )}

          {(error || loadError) && <p className="error-text">{error || loadError}</p>}

          {invite && (
            <>
              {invite.isExistingAccount ? (
                <p className="auth-card-subtitle">{t("existingAccountSubtitle")}</p>
              ) : (
                <>
                  <div className="auth-input-group">
                    <span className="auth-input-icon">
                      <UserIcon />
                    </span>
                    <input
                      value={displayName}
                      onChange={(e) => setDisplayName(e.target.value)}
                      placeholder={t("displayNamePlaceholder")}
                      aria-label={t("displayNamePlaceholder")}
                      required
                    />
                  </div>

                  <div className="auth-input-group">
                    <span className="auth-input-icon">
                      <KeyIcon />
                    </span>
                    <input
                      type={showPassword ? "text" : "password"}
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      placeholder={t("passwordPlaceholder")}
                      aria-label={t("passwordLabel")}
                      minLength={8}
                    />
                    <button
                      type="button"
                      className="auth-input-toggle"
                      onClick={() => setShowPassword((v) => !v)}
                      aria-label={showPassword ? t("hidePassword") : t("showPassword")}
                    >
                      {showPassword ? <EyeOffIcon /> : <EyeIcon />}
                    </button>
                  </div>
                </>
              )}

              <button type="submit" className="auth-submit" disabled={loading}>
                {loading
                  ? t("joining")
                  : invite.isExistingAccount
                  ? t("confirmJoin")
                  : t("joinFamily")}
              </button>
            </>
          )}
        </div>

        <p className="auth-footer-text">
          <Link to="/login">{t("backToLogin")}</Link>
        </p>
      </form>
    </div>
  );
}
