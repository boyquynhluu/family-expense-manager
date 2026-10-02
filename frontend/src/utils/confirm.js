import Swal from "sweetalert2";
import i18n from "../i18n";

// Same look as components/ui/Button.jsx (Tailwind classes; buttonsStyling is off so
// SweetAlert2 adds no button styles of its own).
const SWAL_BUTTON_BASE =
  "mx-1 mt-3.5 inline-flex h-9 cursor-pointer items-center justify-center rounded-lg border-0 px-4 " +
  "font-[inherit] text-sm font-medium shadow-sm transition-colors duration-150 " +
  "focus-visible:outline-2 focus-visible:outline-offset-2";

const SWAL_CONFIRM_TONES = {
  danger: "bg-red-600 text-white hover:bg-red-700 focus-visible:outline-red-500",
  warning: "bg-amber-500 text-white hover:bg-amber-600 focus-visible:outline-amber-500",
  primary: "bg-indigo-600 text-white hover:bg-indigo-700 focus-visible:outline-indigo-500",
};

const SWAL_CANCEL_CLASS =
  `${SWAL_BUTTON_BASE} bg-white text-slate-700 ring-1 ring-inset ring-slate-300 hover:bg-slate-50 ` +
  "focus-visible:outline-indigo-500";

function swalCustomClass(tone) {
  return {
    popup: "custom-swal-popup",
    title: "custom-swal-title",
    htmlContainer: "custom-swal-text",
    confirmButton: `${SWAL_BUTTON_BASE} ${SWAL_CONFIRM_TONES[tone] ?? SWAL_CONFIRM_TONES.danger}`,
    cancelButton: SWAL_CANCEL_CLASS,
    icon: "custom-swal-icon",
  };
}

/**
 * SweetAlert2 confirm dialog used everywhere instead of the browser's native
 * `window.confirm` (which can't be styled and looks jarring next to the rest of the
 * app). Returns a plain boolean — same shape as a `window.confirm` call — so every
 * call site keeps the exact guard it already had, just with an `await` added:
 * `if (!(await confirmDialog(t("...deleteConfirm")))) return;`
 *
 * `options.tone` colors the confirm button by what it does: "danger" (default — almost
 * every call site confirms a delete/revoke), "warning" for undoable-but-significant
 * actions (leave family, pause, transfer ownership), "primary" for harmless ones.
 */
export async function confirmDialog(text, options = {}) {
  const result = await Swal.fire({
    title: options.title ?? i18n.t("common:confirmTitle"),
    text,
    icon: options.icon ?? "warning",
    showCancelButton: true,
    confirmButtonText: options.confirmButtonText ?? i18n.t("common:confirm"),
    cancelButtonText: options.cancelButtonText ?? i18n.t("common:cancel"),
    customClass: swalCustomClass(options.tone ?? "danger"),
    buttonsStyling: false,
  });
  return result.isConfirmed;
}

/**
 * Like confirmDialog, but the user must type `word` (e.g. "XOÁ") before the confirm button
 * does anything — for actions that can't be undone and touch many rows at once (emptying the trash).
 */
export async function confirmTypedDialog(text, word, options = {}) {
  const result = await Swal.fire({
    title: options.title ?? i18n.t("common:confirmTitle"),
    text,
    icon: options.icon ?? "warning",
    input: "text",
    inputPlaceholder: word,
    inputAttributes: { autocapitalize: "characters", autocomplete: "off", "aria-label": word },
    inputValidator: (value) =>
      value.trim().toLocaleUpperCase() === word.toLocaleUpperCase()
        ? undefined
        : i18n.t("common:typeToConfirm", { word }),
    showCancelButton: true,
    confirmButtonText: options.confirmButtonText ?? i18n.t("common:confirm"),
    cancelButtonText: options.cancelButtonText ?? i18n.t("common:cancel"),
    customClass: { ...swalCustomClass(options.tone ?? "danger"), input: "custom-swal-input" },
    buttonsStyling: false,
  });
  return result.isConfirmed;
}
