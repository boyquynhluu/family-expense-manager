import { useEffect, useRef } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";

// Landed on after Google/Facebook login: auth-service redirects here with either
// ?accessToken=...&refreshToken=... or ?error=... appended (see auth-service
// OAuth2AuthenticationSuccessHandler / OAuth2AuthenticationFailureHandler).
export default function OAuth2Callback() {
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

    if (error || !accessToken || !refreshToken) {
      navigate("/login", { replace: true, state: { oauth2Error: true } });
      return;
    }

    loginWithTokens(accessToken, refreshToken);
    navigate("/", { replace: true });
  }, [searchParams, loginWithTokens, navigate]);

  return (
    <div className="auth-page">
      <p>Đang đăng nhập...</p>
    </div>
  );
}
