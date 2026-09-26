import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import LanguageSwitcher from "../components/LanguageSwitcher";
import { LIMITS } from "../utils/inputLimits";

// Landed on from the verification link emailed after registration (see
// notification-service UserVerificationEventListener). Calls the API to activate the
// account, then routes to /login on success.
export default function Verify() {
  const { t } = useTranslation("verify");
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const handled = useRef(false);
  const [status, setStatus] = useState("verifying");
  const [error, setError] = useState("");
  const [resendEmail, setResendEmail] = useState("");
  const [resending, setResending] = useState(false);
  // notification-service links both registration and e-mail change confirmations to /verify?token=...;
  // change tokens are recognisable by their "ec." prefix (see auth-service AuthService).
  const isEmailChange = (searchParams.get("token") ?? "").startsWith("ec.");

  useEffect(() => {
    if (handled.current) return;
    handled.current = true;

    const token = searchParams.get("token");
    if (!token) {
      setStatus("error");
      setError(t("missingToken"));
      return;
    }

    client
      .get("/auth/verify", { params: { token } })
      .then((res) => {
        toast.success(isEmailChange ? t("emailChangeSuccess") : res.data.data?.message || t("verifySuccess"));
        navigate("/login", { replace: true });
      })
      .catch((err) => {
        setStatus("error");
        setError(err.response?.data?.message || t(isEmailChange ? "emailChangeFailed" : "verifyFailed"));
      });
    // handled.current guarantees this runs once, so listing every value it reads is safe.
  }, [searchParams, navigate, isEmailChange, t]);

  async function handleResend(e) {
    e.preventDefault();
    setResending(true);
    try {
      const res = await client.post("/auth/resend-verification", { email: resendEmail });
      toast.success(res.data.data?.message || t("resendSent"));
    } catch (err) {
      toast.error(err.response?.data?.message || t("resendFailed"));
    } finally {
      setResending(false);
    }
  }

  return (
    <div className="auth-page">
      <LanguageSwitcher variant="floating" />
      <div className="auth-form">
        {status === "verifying" && <p className="verify-message">{isEmailChange ? t("emailChangeVerifying") : t("verifying")}</p>}
        {status === "error" && (
          <>
            <p className="verify-message is-error">{error}</p>
            {!isEmailChange && (
              <form className="verify-resend" onSubmit={handleResend}>
                <p className="verify-message">{t("resendHint")}</p>
                <div className="auth-input-group no-required-mark">
                  <input
                    type="email"
                    value={resendEmail}
                    maxLength={LIMITS.email}
                    onChange={(e) => setResendEmail(e.target.value)}
                    placeholder={t("resendEmailPlaceholder")}
                    aria-label={t("resendEmailPlaceholder")}
                    required
                  />
                </div>
                <button type="submit" className="auth-submit" disabled={resending}>
                  {resending ? t("resendSending") : t("resendButton")}
                </button>
              </form>
            )}
            <p className="auth-footer-text">
              <Link to="/login">{t("backToLogin")}</Link>
            </p>
          </>
        )}
      </div>
    </div>
  );
}
