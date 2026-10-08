const UNREAD_CHANGED = "unread-notifications-changed";

/** Tells the sidebar badge (useUnreadNotifications) to re-read the count now, e.g. after marking items read. */
export function notifyUnreadChanged() {
  window.dispatchEvent(new Event(UNREAD_CHANGED));
}

export function onUnreadChanged(listener) {
  window.addEventListener(UNREAD_CHANGED, listener);
  return () => window.removeEventListener(UNREAD_CHANGED, listener);
}
