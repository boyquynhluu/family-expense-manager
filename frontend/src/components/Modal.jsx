import { useEffect, useId, useRef } from "react";
import { createPortal } from "react-dom";
import { CloseIcon } from "./AppIcons";

/**
 * Accessible dialog rendered into <body>: Esc and a click on the backdrop close it (unless
 * `dismissible` is false, e.g. while a request is in flight), the page behind stops scrolling,
 * and focus returns to whatever opened it once it closes.
 */
export default function Modal({
  open,
  onClose,
  title,
  subtitle,
  icon,
  footer,
  closeLabel = "Close",
  dismissible = true,
  className = "",
  children,
}) {
  const titleId = useId();
  const dialogRef = useRef(null);
  // Read through refs so the open/close effect below depends on `open` only — callers can pass an
  // inline onClose without it re-running (and stealing focus from an input) on every render.
  const onCloseRef = useRef(onClose);
  const dismissibleRef = useRef(dismissible);
  useEffect(() => {
    onCloseRef.current = onClose;
    dismissibleRef.current = dismissible;
  });

  useEffect(() => {
    if (!open) return undefined;
    const previouslyFocused = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    // Only take focus ourselves if nothing inside (e.g. an autoFocus input) already has it.
    if (!dialogRef.current?.contains(document.activeElement)) {
      dialogRef.current?.focus();
    }

    function handleKeyDown(e) {
      if (e.key === "Escape" && dismissibleRef.current) {
        onCloseRef.current();
      }
    }
    document.addEventListener("keydown", handleKeyDown);

    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = previousOverflow;
      previouslyFocused?.focus?.();
    };
  }, [open]);

  if (!open) return null;

  return createPortal(
    <div
      className="modal-backdrop"
      onMouseDown={(e) => {
        // mousedown (not click) so a drag that starts inside the dialog and ends outside doesn't close it.
        if (e.target === e.currentTarget && dismissible) onClose();
      }}
    >
      <div
        ref={dialogRef}
        className={`modal-dialog ${className}`.trim()}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
      >
        <div className="modal-header">
          {icon && <div className="modal-header-icon">{icon}</div>}
          <div className="modal-header-text">
            <h2 id={titleId}>{title}</h2>
            {subtitle && <p>{subtitle}</p>}
          </div>
          <button type="button" className="modal-close" onClick={onClose} disabled={!dismissible} aria-label={closeLabel}>
            <CloseIcon />
          </button>
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-footer">{footer}</div>}
      </div>
    </div>,
    document.body
  );
}
