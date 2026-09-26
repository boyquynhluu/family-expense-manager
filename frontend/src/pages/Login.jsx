import { useEffect, useRef, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import client, { oauth2AuthorizationUrl } from "../api/client";
import { EyeIcon, EyeOffIcon, FacebookIcon, GithubIcon, GoogleIcon, KeyIcon, MailIcon } from "../components/AuthIcons";
import LanguageSwitcher from "../components/LanguageSwitcher";
import { useAuth } from "../hooks/useAuth";
import { LIMITS } from "../utils/inputLimits";

export default function Login() {
  const { t } = useTranslation("login");
  const { login, verifyTwoFactor } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(true);
  const [error, setError] = useState(
    location.state?.oauth2Error
      ? t(location.state.oauth2ErrorCode === "account_locked" ? "accountLocked" : "oauth2Error")
      : ""
  );
  const [loading, setLoading] = useState(false);
  const [resending, setResending] = useState(false);

  // Set only when the backend says the account has 2FA enabled (README "9. Không có
  // 2FA") — while set, the form below switches to asking for the 6-digit code instead
  // of email/password.
  const [twoFactorToken, setTwoFactorToken] = useState(location.state?.twoFactorToken ?? null);
  const [totpCode, setTotpCode] = useState("");

  // Guards against StrictMode's dev-only double-invoke of effects (mount → cleanup →
  // mount again), which would otherwise show the success toast twice — see
  // OAuth2Callback.jsx/Verify.jsx for the same pattern.
  const handledLocationState = useRef(false);

  useEffect(() => {
    if (handledLocationState.current) return;
    handledLocationState.current = true;

    if (location.state?.passwordResetSuccess) {
      toast.success(t("passwordResetSuccess"));
      navigate(location.pathname, { replace: true, state: {} });
    } else if (location.state?.twoFactorToken) {
      // Already copied into component state above; drop it from history so a refresh
      // doesn't replay a (single-use) challenge.
      navigate(location.pathname, { replace: true, state: {} });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const result = await login(email, password, rememberMe);
      if (result.requiresTwoFactor) {
        setTwoFactorToken(result.twoFactorToken);
      } else {
        navigate("/");
      }
    } catch (err) {
      setError(err.response?.data?.message || t("loginFailed"));
    } finally {
      setLoading(false);
    }
  }

  // Must match AuthService.NOT_VERIFIED_MESSAGE on the backend.
  const showResendVerification = error.includes("chưa được xác thực");

  async function handleResendVerification() {
    if (!email) return;
    setResending(true);
    try {
      const res = await client.post("/auth/resend-verification", { email });
      toast.success(res.data.data?.message || t("resendVerificationSent"));
    } catch (err) {
      toast.error(err.response?.data?.message || t("resendVerificationFailed"));
    } finally {
      setResending(false);
    }
  }

  async function handleTwoFactorSubmit(e) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      await verifyTwoFactor(twoFactorToken, totpCode, rememberMe);
      navigate("/");
    } catch (err) {
      setError(err.response?.data?.message || t("twoFactorCodeInvalid"));
    } finally {
      setLoading(false);
    }
  }

  if (twoFactorToken) {
    return (
      <div className="auth-page">
        <LanguageSwitcher variant="floating" />
        <form className="auth-form" onSubmit={handleTwoFactorSubmit}>
          <h1 className="auth-title">{t("twoFactorTitle")}</h1>

          <div className="auth-card">
            <p className="auth-card-title">{t("twoFactorCardTitle")}</p>
            <p className="auth-card-subtitle">{t("twoFactorCardSubtitle")}</p>

            {error && <p className="error-text">{error}</p>}

            <div className="auth-input-group no-required-mark">
              <span className="auth-input-icon">
                <KeyIcon />
              </span>
              <input
                value={totpCode}
                maxLength={LIMITS.twoFactorCode}
                onChange={(e) => setTotpCode(e.target.value)}
                placeholder={t("twoFactorCodePlaceholder")}
                aria-label={t("twoFactorCodePlaceholder")}
                autoFocus
                required
              />
            </div>

            <button type="submit" className="auth-submit" disabled={loading}>
              {loading ? t("verifying") : t("confirm")}
            </button>
          </div>

          <p className="auth-footer-text">
            <button type="button" className="auth-forgot" onClick={() => setTwoFactorToken(null)}>
              {t("backToLogin")}
            </button>
          </p>
        </form>
      </div>
    );
  }

  return (
    <div className="auth-page">
      <LanguageSwitcher variant="floating" />
      <form className="auth-form" onSubmit={handleSubmit}>
        <h1 className="auth-title">{t("title")}</h1>

        <div className="auth-card">
          <p className="auth-card-title">{t("welcomeBack")}</p>
          <p className="auth-card-subtitle">{t("subtitle")}</p>

          {error && <p className="error-text">{error}</p>}
          {showResendVerification && (
            <button type="button" className="auth-forgot" onClick={handleResendVerification} disabled={resending}>
              {resending ? t("resendVerificationSending") : t("resendVerification")}
            </button>
          )}

          <div className="auth-input-group no-required-mark">
            <span className="auth-input-icon">
              <MailIcon />
            </span>
            <input
              type="email"
              value={email}
              maxLength={LIMITS.email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder={t("emailPlaceholder")}
              aria-label="Email"
              required
            />
          </div>

          <div className="auth-input-group no-required-mark">
            <span className="auth-input-icon">
              <KeyIcon />
            </span>
            <input
              type={showPassword ? "text" : "password"}
              value={password}
              maxLength={LIMITS.password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder={t("passwordPlaceholder")}
              aria-label={t("passwordPlaceholder")}
              required
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

          <div className="auth-options">
            <label className="auth-remember">
              <span className="auth-toggle">
                <input type="checkbox" checked={rememberMe} onChange={(e) => setRememberMe(e.target.checked)} />
                <span className="auth-toggle-track" />
              </span>
              {t("rememberMe")}
            </label>
            <Link to="/forgot-password" className="auth-forgot">
              {t("forgotPassword")}
            </Link>
          </div>

          <button type="submit" className="auth-submit" disabled={loading}>
            {loading ? t("loggingIn") : t("submit")}
          </button>
        </div>

        <div className="auth-divider">
          <span>{t("orContinueWith")}</span>
        </div>

        <div className="oauth2-row">
          <a
            className="oauth2-icon-button"
            href={oauth2AuthorizationUrl("google")}
            title={t("loginWithGoogle")}
          >
            <span className="oauth2-icon-badge oauth2-badge-google">
              <GoogleIcon />
            </span>
            <span>Google</span>
          </a>
          <span className="oauth2-icon-button is-disabled" title={t("facebookComingSoon")}>
            <span className="oauth2-icon-badge oauth2-badge-facebook">
              <FacebookIcon />
            </span>
            <span>Facebook</span>
          </span>
          <span className="oauth2-icon-button is-disabled" title={t("githubComingSoon")}>
            <span className="oauth2-icon-badge oauth2-badge-github">
              <GithubIcon />
            </span>
            <span>GitHub</span>
          </span>
        </div>

        <p className="auth-footer-text">
          {t("noAccount")} <Link to="/register">{t("registerLink")}</Link>
        </p>
      </form>
    </div>
  );
}
