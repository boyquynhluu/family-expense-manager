import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import { EyeIcon, EyeOffIcon, KeyIcon } from "../components/AuthIcons";
import LanguageSwitcher from "../components/LanguageSwitcher";

export default function ResetPassword() {
  const { t } = useTranslation("resetPassword");
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get("token");
  const [newPassword, setNewPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState(token ? "" : t("missingToken"));
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      await client.post("/auth/reset-password", { token, newPassword });
      navigate("/login", { replace: true, state: { passwordResetSuccess: true } });
    } catch (err) {
      setError(err.response?.data?.message || t("resetFailed"));
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
          <p className="auth-card-title">{t("cardTitle")}</p>
          <p className="auth-card-subtitle">{t("subtitle")}</p>

          {error && <p className="error-text">{error}</p>}

          {token && (
            <>
              <div className="auth-input-group">
                <span className="auth-input-icon">
                  <KeyIcon />
                </span>
                <input
                  type={showPassword ? "text" : "password"}
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  placeholder={t("newPasswordPlaceholder")}
                  aria-label={t("newPasswordLabel")}
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

              <button type="submit" className="auth-submit" disabled={loading}>
                {loading ? t("saving") : t("submit")}
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
