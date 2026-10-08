import { useCallback, useEffect, useRef, useState } from "react";
import client from "../api/client";
import { onTrashChanged } from "../utils/trashEvents";
import { useVisiblePolling } from "./useVisiblePolling";

/**
 * How many wallets/categories/transactions sit in the Trash, for the sidebar badge. Refreshed right after a
 * page deletes/restores something (onTrashChanged), plus a slow poll — only while the tab is visible — to
 * pick up changes made by other family members.
 */
export function useTrashCount(intervalMs = 60_000) {
  const [count, setCount] = useState(0);
  const mounted = useRef(true);

  const poll = useCallback(() => {
    client
      .get("/expenses/trash/count")
      .then((res) => {
        if (mounted.current) setCount(res.data.data.count);
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    mounted.current = true;
    const unsubscribe = onTrashChanged(poll);
    return () => {
      mounted.current = false;
      unsubscribe();
    };
  }, [poll]);

  useVisiblePolling(poll, intervalMs);

  return count;
}
