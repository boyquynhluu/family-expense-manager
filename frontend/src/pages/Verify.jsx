import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import client from "../api/client";
import LanguageSwitcher from "../components/LanguageSwitcher";

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
  }, [searchParams, navigate]);

  return (
    <div className="auth-page">
      <LanguageSwitcher variant="floating" />
      <div className="auth-form">
        {status === "verifying" && <p className="verify-message">{isEmailChange ? t("emailChangeVerifying") : t("verifying")}</p>}
        {status === "error" && (
          <>
            <p className="verify-message is-error">{error}</p>
            <p className="auth-footer-text">
              <Link to="/login">{t("backToLogin")}</Link>
            </p>
          </>
        )}
      </div>
    </div>
  );
}
