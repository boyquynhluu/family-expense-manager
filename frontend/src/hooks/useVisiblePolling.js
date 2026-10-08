import { useEffect, useRef } from "react";

/**
 * Calls `poll` now and then every `intervalMs` — but only while the tab is visible. A hidden tab stops polling
 * entirely, and coming back to it polls at once, so the data is fresh exactly when someone can see it.
 * (Polling hidden tabs every 15s used to send ~480 requests/hour per open tab for badges nobody was looking at.)
 * The latest `poll` is always used, so callers don't need to memoise it. Pass `immediate: false` when the
 * caller already loads on mount, so the first poll waits one interval (returning to the tab still polls at once).
 */
export function useVisiblePolling(poll, intervalMs, { immediate = true } = {}) {
  const pollRef = useRef(poll);
  useEffect(() => {
    pollRef.current = poll;
  });

  useEffect(() => {
    let timer = null;

    function start(pollNow) {
      if (timer !== null) return;
      if (pollNow) pollRef.current();
      timer = setInterval(() => pollRef.current(), intervalMs);
    }

    function stop() {
      clearInterval(timer);
      timer = null;
    }

    function onVisibilityChange() {
      if (document.hidden) stop();
      else start(true);
    }

    if (!document.hidden) start(immediate);
    document.addEventListener("visibilitychange", onVisibilityChange);
    return () => {
      stop();
      document.removeEventListener("visibilitychange", onVisibilityChange);
    };
  }, [intervalMs, immediate]);
}
