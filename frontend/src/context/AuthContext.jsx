import { createContext, useEffect, useMemo, useState } from "react";
import client from "../api/client";
import { clearTokens, getAccessToken, saveTokens } from "../utils/tokenStorage";

export const AuthContext = createContext(null);

function decodeJwt(token) {
  try {
    const payload = token.split(".")[1];

    const base64 = payload
      .replace(/-/g, "+")
      .replace(/_/g, "/");

    const binary = atob(base64);

    const bytes = Uint8Array.from(binary, char => char.charCodeAt(0));

    const json = new TextDecoder("utf-8").decode(bytes);

    return JSON.parse(json);
  } catch {
    return null;
  }
}

export function AuthProvider({ children }) {
  const [accessToken, setAccessToken] = useState(() => getAccessToken());
  const [claims, setClaims] = useState(() => {
    const token = getAccessToken();
    return token ? decodeJwt(token) : null;
  });

  useEffect(() => {
    setClaims(accessToken ? decodeJwt(accessToken) : null);
  }, [accessToken]);

  function persistTokens(newAccessToken, newRefreshToken, remember) {
    saveTokens(newAccessToken, newRefreshToken, remember);
    setAccessToken(newAccessToken);
  }

  // Returns { requiresTwoFactor: true, twoFactorToken } instead of logging in when the
  // account has 2FA enabled (README "9. Không có 2FA") — the caller must then collect a
  // code from the user and call verifyTwoFactor() to actually get tokens.
  async function login(email, password, remember = true) {
    const res = await client.post("/auth/login", { email, password });
    const data = res.data.data;
    if (data.requiresTwoFactor) {
      return { requiresTwoFactor: true, twoFactorToken: data.twoFactorToken };
    }
    persistTokens(data.tokens.accessToken, data.tokens.refreshToken, remember);
    return { requiresTwoFactor: false };
  }

  async function verifyTwoFactor(twoFactorToken, code, remember = true) {
    const res = await client.post("/auth/2fa/verify-login", { challengeToken: twoFactorToken, code });
    const { accessToken: token, refreshToken } = res.data.data;
    persistTokens(token, refreshToken, remember);
  }

  // Called by OAuth2Callback with the tokens auth-service appended to the redirect
  // after a Google/Facebook login — there's no "remember me" step in that flow, so it
  // always persists to localStorage (same as password login left checked by default).
  function loginWithTokens(newAccessToken, newRefreshToken) {
    persistTokens(newAccessToken, newRefreshToken, true);
  }

  async function register(familyName, email, password, displayName) {
    const res = await client.post("/auth/register", { familyName, email, password, displayName });
    // Backend no longer logs the user in on register — the account stays inactive
    // until they click the verification link emailed to them (see auth-service
    // AuthService.register()). Just hand back the confirmation message.
    return res.data.data.message;
  }

  // Best-effort: revoke the session server-side (see README "8. Không quản lý được
  // phiên đăng nhập") so it stops working immediately instead of just expiring
  // naturally — but local logout must succeed even if this call fails (e.g. already
  // expired token, network hiccup), so it's never allowed to block clearing tokens.
  async function logout() {
    try {
      await client.post("/auth/logout");
    } catch {
      // ignore — clearing local tokens below is what actually logs this device out
    }
    clearTokens();
    setAccessToken(null);
  }

  // Re-points this session at a different family the account belongs to (see README
  // "6. 1 tài khoản chỉ thuộc đúng 1 gia đình") — the backend re-issues fresh tokens
  // scoped to it, same mechanism as login/refresh.
  async function switchFamily(familyId) {
    const res = await client.post("/auth/switch-family", { familyId });
    const { accessToken: token, refreshToken } = res.data.data;
    persistTokens(token, refreshToken);
  }

  const value = useMemo(
    () => ({
      isAuthenticated: Boolean(accessToken),
      familyId: claims?.familyId ?? null,
      role: claims?.role ?? null,
      userId: claims?.sub ?? null,
      displayName: claims?.displayName ?? null,
      isSystemAdmin: claims?.isSystemAdmin ?? false,
      login,
      verifyTwoFactor,
      loginWithTokens,
      register,
      logout,
      switchFamily,
    }),
    [accessToken, claims]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
