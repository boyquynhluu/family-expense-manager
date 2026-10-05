import { useCallback, useEffect, useState } from "react";
import client from "../api/client";

/**
 * Months the family has closed ("chốt sổ", GET /expenses/period-locks). Nothing dated in a closed month may be
 * created, edited, deleted or restored — the backend answers 409 — so pages use `isLocked` to hide those actions
 * up front. `isLocked` takes anything starting with "yyyy-MM" (a month, a date or a server LocalDateTime).
 */
export function usePeriodLocks() {
  const [locks, setLocks] = useState([]);

  const reload = useCallback(() => {
    client
      .get("/expenses/period-locks")
      .then((res) => setLocks(res.data.data))
      .catch(() => {});
  }, []);

  useEffect(reload, [reload]);

  const isLocked = useCallback(
    (value) => !!value && locks.some((l) => l.periodMonth === String(value).slice(0, 7)),
    [locks],
  );

  return { locks, isLocked, reload };
}
