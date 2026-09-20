import { useEffect, useRef } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useAuth } from "../hooks/useAuth";

// Landed on after Google/Facebook login: auth-service redirects here with either
// ?accessToken=...&refreshToken=..., ?twoFactorToken=... (account has 2FA on — Login
// then asks for the code) or ?error=... appended (see auth-service
// OAuth2AuthenticationSuccessHandler / OAuth2AuthenticationFailureHandler).
export default function OAuth2Callback() {
  const { t } = useTranslation("oauth2Callback");
  const [searchParams] = useSearchParams();
  const { loginWithTokens } = useAuth();
  const navigate = useNavigate();
  const handled = useRef(false);

  useEffect(() => {
    if (handled.current) return;
    handled.current = true;

    const accessToken = searchParams.get("accessToken");
    const refreshToken = searchParams.get("refreshToken");
    const error = searchParams.get("error");
    const twoFactorToken = searchParams.get("twoFactorToken");

    if (twoFactorToken) {
      navigate("/login", { replace: true, state: { twoFactorToken } });
      return;
    }

    if (error || !accessToken || !refreshToken) {
      navigate("/login", { replace: true, state: { oauth2Error: true } });
      return;
    }

    loginWithTokens(accessToken, refreshToken);
    navigate("/", { replace: true });
  }, [searchParams, loginWithTokens, navigate]);

  return (
    <div className="auth-page">
      <p>{t("loggingIn")}</p>
    </div>
  );
}
