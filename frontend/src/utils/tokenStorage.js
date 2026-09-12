// "Remember me" storage: localStorage persists across browser restarts,
// sessionStorage clears when the tab/browser closes. Whichever storage currently
// holds a refreshToken is treated as "active" so reads/refreshes don't need to be
// told which one was chosen at login time.
function activeStorage() {
  return localStorage.getItem("refreshToken") ? localStorage : sessionStorage;
}

export function getAccessToken() {
  return activeStorage().getItem("accessToken");
}

export function getRefreshToken() {
  return activeStorage().getItem("refreshToken");
}

export function saveTokens(accessToken, refreshToken, remember) {
  const storage = remember === undefined ? activeStorage() : remember ? localStorage : sessionStorage;
  const other = storage === localStorage ? sessionStorage : localStorage;
  storage.setItem("accessToken", accessToken);
  storage.setItem("refreshToken", refreshToken);
  other.removeItem("accessToken");
  other.removeItem("refreshToken");
}

export function clearTokens() {
  localStorage.removeItem("accessToken");
  localStorage.removeItem("refreshToken");
  sessionStorage.removeItem("accessToken");
  sessionStorage.removeItem("refreshToken");
}
