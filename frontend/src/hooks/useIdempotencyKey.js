import { useCallback, useRef } from "react";

/**
 * An Idempotency-Key per save attempt (the backend's IdempotencyGuard): `keyFor(payload)` returns the same key
 * while the same payload is being re-sent — so if a save timed out but actually went through, sending it again
 * gets the first result back instead of creating a duplicate — and a fresh key as soon as the payload changes.
 *
 * Call `reset()` only after a successful save, so saving the same values again on purpose (two identical
 * coffees) is a new operation. After an error keep the key: if the server rejected the request it released
 * the key and a retry simply runs again; if it is still processing it answers 409 instead of saving twice.
 */
export function useIdempotencyKey() {
  const current = useRef(null); // { key, payloadJson }

  const keyFor = useCallback((payload) => {
    const payloadJson = JSON.stringify(payload);
    if (current.current?.payloadJson !== payloadJson) {
      current.current = { key: crypto.randomUUID(), payloadJson };
    }
    return current.current.key;
  }, []);

  const reset = useCallback(() => {
    current.current = null;
  }, []);

  return { keyFor, reset };
}
