import { useState } from "react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import { MailIcon } from "../components/AuthIcons";
import LanguageSwitcher from "../components/LanguageSwitcher";

export default function ForgotPassword() {
  const { t } = useTranslation("forgotPassword");
  const [email, setEmail] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    setMessage("");
    setLoading(true);
    try {
      const res = await client.post("/auth/forgot-password", { email });
      setMessage(res.data.data?.message || t("resetLinkSent"));
    } catch (err) {
      setError(err.response ? err.response.data?.message || t("genericError") : t("networkError"));
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
          <p className="auth-card-title">{t("cardTitle")}</p>
          <p className="auth-card-subtitle">{t("subtitle")}</p>

          {error && <p className="error-text">{error}</p>}
          {message && <p className="success-text">{message}</p>}

          {!message && (
            <>
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

              <button type="submit" className="auth-submit" disabled={loading}>
                {loading ? t("sending") : t("submit")}
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
