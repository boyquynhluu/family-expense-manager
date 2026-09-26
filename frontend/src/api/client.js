import axios from "axios";
import { clearTokens, getAccessToken, getRefreshToken, saveTokens } from "../utils/tokenStorage";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api";

// API_BASE_URL is "<gateway-origin>/api"; strip the "/api" suffix to link straight to
// gateway-level, non-REST endpoints like the OAuth2 login redirect below.
const GATEWAY_ORIGIN = API_BASE_URL.replace(/\/api\/?$/, "");

export function oauth2AuthorizationUrl(provider) {
  return `${GATEWAY_ORIGIN}/api/auth/oauth2/authorization/${provider}`;
}

const client = axios.create({ baseURL: API_BASE_URL });

client.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

let refreshPromise = null;

// The refresh token lives in localStorage/sessionStorage, shared by every tab, and the backend
// rotates it on each /auth/refresh — so two tabs must never refresh with the same token at once.
// Web Locks serialise that across tabs where supported (the backend also tolerates a short race).
function withCrossTabLock(fn) {
  return navigator.locks ? navigator.locks.request("auth-refresh", fn) : fn();
}

function refreshAccessToken() {
  if (!refreshPromise) {
    const staleRefreshToken = getRefreshToken();
    refreshPromise = withCrossTabLock(async () => {
      // Another tab may have already rotated the token while this one waited for the lock.
      const currentRefreshToken = getRefreshToken();
      if (currentRefreshToken && currentRefreshToken !== staleRefreshToken) {
        return getAccessToken();
      }
      const res = await axios.post(`${API_BASE_URL}/auth/refresh`, { refreshToken: currentRefreshToken });
      const { accessToken, refreshToken: newRefreshToken } = res.data.data;
      saveTokens(accessToken, newRefreshToken);
      return accessToken;
    }).finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

client.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    const isAuthEndpoint = originalRequest?.url?.startsWith("/auth/");

    if (error.response?.status === 401 && !originalRequest._retry && !isAuthEndpoint) {
      originalRequest._retry = true;
      try {
        // If another tab already refreshed, the stored access token is newer than the one this request sent.
        const storedAccessToken = getAccessToken();
        const sentAccessToken = originalRequest.headers?.Authorization?.replace(/^Bearer /, "");
        const newAccessToken =
          storedAccessToken && storedAccessToken !== sentAccessToken ? storedAccessToken : await refreshAccessToken();
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
        return client(originalRequest);
      } catch (refreshError) {
        clearTokens();
        window.location.href = "/login";
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);

export default client;
