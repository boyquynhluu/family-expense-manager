import { useState } from "react";
import toast from "react-hot-toast";
import { Link, useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { EyeIcon, EyeOffIcon, HomeIcon, KeyIcon, MailIcon, UserIcon } from "../components/AuthIcons";
import LanguageSwitcher from "../components/LanguageSwitcher";
import { useAuth } from "../hooks/useAuth";

export default function Register() {
  const { t } = useTranslation("register");
  const { register } = useAuth();
  const navigate = useNavigate();
  const [familyName, setFamilyName] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const message = await register(familyName, email, password, displayName);
      toast.success(message || t("registerSuccess"), {
        duration: 6000,
      });
      navigate("/login");
    } catch (err) {
      setError(err.response?.data?.message || t("registerFailed"));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-page">
      <LanguageSwitcher variant="light" />
      <form className="auth-form" onSubmit={handleSubmit}>
        <h1 className="auth-title">{t("title")}</h1>

        <div className="auth-card">
          <p className="auth-card-title">{t("welcome")}</p>
          <p className="auth-card-subtitle">{t("subtitle")}</p>

          {error && <p className="error-text">{error}</p>}

          <div className="auth-input-group">
            <span className="auth-input-icon">
              <HomeIcon />
            </span>
            <input
              value={familyName}
              onChange={(e) => setFamilyName(e.target.value)}
              placeholder={t("familyNamePlaceholder")}
              aria-label={t("familyNamePlaceholder")}
              required
            />
          </div>

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
              <MailIcon />
            </span>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder={t("emailPlaceholder")}
              aria-label="Email"
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

          <label className="auth-terms">
            <input type="checkbox" required />
            {t("termsAgreement")}
          </label>

          <button type="submit" className="auth-submit" disabled={loading}>
            {loading ? t("registering") : t("submit")}
          </button>
        </div>

        <p className="auth-footer-text">
          {t("haveAccount")} <Link to="/login">{t("loginLink")}</Link>
        </p>
      </form>
    </div>
  );
}
