import { createContext, useEffect, useMemo, useState } from "react";
import client from "../api/client";

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
  const [accessToken, setAccessToken] = useState(() => localStorage.getItem("accessToken"));
  const [claims, setClaims] = useState(() => {
    const token = localStorage.getItem("accessToken");
    return token ? decodeJwt(token) : null;
  });

  useEffect(() => {
    setClaims(accessToken ? decodeJwt(accessToken) : null);
  }, [accessToken]);

  function persistTokens(newAccessToken, newRefreshToken) {
    localStorage.setItem("accessToken", newAccessToken);
    localStorage.setItem("refreshToken", newRefreshToken);
    setAccessToken(newAccessToken);
  }

  async function login(email, password) {
    const res = await client.post("/auth/login", { email, password });
    const { accessToken: token, refreshToken } = res.data.data;
    persistTokens(token, refreshToken);
  }

  async function register(familyName, email, password, displayName) {
    const res = await client.post("/auth/register", { familyName, email, password, displayName });
    const { accessToken: token, refreshToken } = res.data.data;
    persistTokens(token, refreshToken);
  }

  function logout() {
    localStorage.removeItem("accessToken");
    localStorage.removeItem("refreshToken");
    setAccessToken(null);
  }

  const value = useMemo(
    () => ({
      isAuthenticated: Boolean(accessToken),
      familyId: claims?.familyId ?? null,
      role: claims?.role ?? null,
      userId: claims?.sub ?? null,
      login,
      register,
      logout,
    }),
    [accessToken, claims]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
