import Swal from "sweetalert2";
import i18n from "../i18n";

const SWAL_CUSTOM_CLASS = {
  popup: "custom-swal-popup",
  title: "custom-swal-title",
  htmlContainer: "custom-swal-text",
  confirmButton: "custom-swal-confirm",
  cancelButton: "custom-swal-cancel",
  icon: "custom-swal-icon",
};

/**
 * SweetAlert2 confirm dialog used everywhere instead of the browser's native
 * `window.confirm` (which can't be styled and looks jarring next to the rest of the
 * app). Returns a plain boolean — same shape as a `window.confirm` call — so every
 * call site keeps the exact guard it already had, just with an `await` added:
 * `if (!(await confirmDialog(t("...deleteConfirm")))) return;`
 */
export async function confirmDialog(text, options = {}) {
  const result = await Swal.fire({
    title: options.title ?? i18n.t("common:confirmTitle"),
    text,
    icon: options.icon ?? "warning",
    showCancelButton: true,
    confirmButtonText: options.confirmButtonText ?? i18n.t("common:confirm"),
    cancelButtonText: options.cancelButtonText ?? i18n.t("common:cancel"),
    customClass: SWAL_CUSTOM_CLASS,
    buttonsStyling: false,
  });
  return result.isConfirmed;
}
