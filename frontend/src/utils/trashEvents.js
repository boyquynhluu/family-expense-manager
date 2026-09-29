const TRASH_CHANGED = "trash-changed";

export function notifyTrashChanged() {
    window.dispatchEvent(new Event(TRASH_CHANGED));
}

export function onTrashChanged(listener) {
    window.addEventListener(TRASH_CHANGED, listener);
    return () => window.removeEventListener(TRASH_CHANGED, listener);
}
