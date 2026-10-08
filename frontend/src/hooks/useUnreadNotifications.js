import { useCallback, useEffect, useRef, useState } from "react";
import client from "../api/client";
import { onUnreadChanged } from "../utils/notificationEvents";
import { useVisiblePolling } from "./useVisiblePolling";

/**
 * Unread notification count for the sidebar badge: refreshed right after the Notifications page changes it
 * (onUnreadChanged), plus a slow poll — only while the tab is visible — to pick up new notifications.
 */
export function useUnreadNotifications(intervalMs = 60_000) {
  const [count, setCount] = useState(0);
  const mounted = useRef(true);

  const poll = useCallback(() => {
    client
      .get("/notifications/unread-count")
      .then((res) => {
        if (mounted.current) setCount(res.data.data.count);
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    mounted.current = true;
    const unsubscribe = onUnreadChanged(poll);
    return () => {
      mounted.current = false;
      unsubscribe();
    };
  }, [poll]);

  useVisiblePolling(poll, intervalMs);

  return count;
}
