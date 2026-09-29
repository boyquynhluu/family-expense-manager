import { useEffect, useState } from "react";
import client from "../api/client";
import { onTrashChanged } from "../utils/trashEvents";

/** Polls how many wallets/categories/transactions sit in the Trash, for the sidebar badge. */
export function useTrashCount(intervalMs = 15_000) {
  const [count, setCount] = useState(0);

  useEffect(() => {
    let cancelled = false;
    function poll() {
      client.get("/expenses/trash/count")
        .then((res) => {
          if (!cancelled) setCount(res.data.data.count);
        })
        .catch(() => {});
    }
    poll();
    const interval = setInterval(poll, intervalMs);
    const unsubscribe = onTrashChanged(poll);
    return () => {
      cancelled = true;
      clearInterval(interval);
      unsubscribe();
    };
  }, [intervalMs]);

  return count;
}
