import { useEffect, useState } from "react";
import client from "../api/client";

/** Polls the unread notification count so the sidebar badge stays close to real-time. */
export function useUnreadNotifications(intervalMs = 15_000) {
  const [count, setCount] = useState(0);

  useEffect(() => {
    let cancelled = false;
    function poll() {
      client
        .get("/notifications/unread-count")
        .then((res) => {
          if (!cancelled) setCount(res.data.data.count);
        })
        .catch(() => {});
    }
    poll();
    const interval = setInterval(poll, intervalMs);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [intervalMs]);

  return count;
}
