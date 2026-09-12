import { createContext, useEffect, useMemo, useState } from "react";
import client from "../api/client";
import { clearTokens, getAccessToken, saveTokens } from "../utils/tokenStorage";

export const AuthContext = createContext(null);

function decodeJwt(token) {
  try {
    const payload = token.split(".")[1];
    const json = atob(payload.replace(/-/g, "+").replace(/_/g, "/"));
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

  async function login(email, password, remember = true) {
    const res = await client.post("/auth/login", { email, password });
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

  function logout() {
    clearTokens();
    setAccessToken(null);
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
      loginWithTokens,
      register,
      logout,
    }),
    [accessToken, claims]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
