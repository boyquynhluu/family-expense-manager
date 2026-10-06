// Where to go after logging in. A page that needs a login sends the user to /login?redirect=<that page>, and a
// session that could not be refreshed adds &expired=1 so Login can say why. Google login leaves the app and comes
// back on /oauth2/callback, so that flow keeps the target in sessionStorage instead of the URL.

const STASH_KEY = "postLoginRedirect";

// Going "back" to one of these after logging in makes no sense (or loops) — land on the dashboard instead.
const AUTH_PAGES = ["/login", "/register", "/verify", "/forgot-password", "/reset-password", "/accept-invite", "/oauth2/callback"];

/**
 * Only an in-app path is followed: "/loans?x=1" yes; "https://evil.example", "//evil.example" or "/\evil.example"
 * no — the redirect parameter comes from the URL, so anyone can craft it. Anything else falls back to "/".
 */
export function safeRedirect(path) {
  if (typeof path !== "string" || !path.startsWith("/") || path.startsWith("//") || path.includes("\\")) {
    return "/";
  }
  const pathname = path.split(/[?#]/)[0];
  return AUTH_PAGES.includes(pathname) ? "/" : path;
}

export function currentPath() {
  return window.location.pathname + window.location.search + window.location.hash;
}

/** "/login", "/login?redirect=%2Floans" or "/login?redirect=%2Floans&expired=1". */
export function loginUrl(redirect, { expired = false } = {}) {
  const params = new URLSearchParams();
  const target = safeRedirect(redirect);
  if (target !== "/") params.set("redirect", target);
  if (expired) params.set("expired", "1");
  const query = params.toString();
  return query ? `/login?${query}` : "/login";
}

export function stashRedirect(path) {
  try {
    sessionStorage.setItem(STASH_KEY, safeRedirect(path));
  } catch {
    // storage blocked (private mode…) — the user just lands on the dashboard
  }
}

/** The stashed target, removed so a later login doesn't reuse it; "/" when there is none. */
export function takeStashedRedirect() {
  try {
    const path = sessionStorage.getItem(STASH_KEY);
    sessionStorage.removeItem(STASH_KEY);
    return safeRedirect(path);
  } catch {
    return "/";
  }
}
