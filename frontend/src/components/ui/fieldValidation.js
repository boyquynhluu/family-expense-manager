// Validation logic behind components/ui/Field.jsx — see the comment at the top of that file.
import { createContext, useCallback, useContext, useEffect } from "react";
import { useTranslation } from "react-i18next";

export const FieldContext = createContext(null);

// Visible label text of a control, lower-cased for use mid-sentence ("Vui lòng nhập tên ví").
// Taken from its <label> minus the control itself, the "*" mark, "(tuỳ chọn)"-style notes
// and the error line; falls back to aria-label / placeholder (the auth pages have no labels).
function fieldLabel(el) {
  let text = "";
  const label = el.labels?.[0];
  if (label) {
    text = Array.from(label.childNodes)
      .filter((n) => !n.contains(el) && !(n.nodeType === 1 && n.hasAttribute("data-field-error")))
      .map((n) => n.textContent)
      .join(" ");
  }
  text = text.replace(/\*/g, "").replace(/\([^)]*\)/g, "").replace(/\s+/g, " ").trim();
  if (!text) text = (el.getAttribute("aria-label") || el.placeholder || "").trim();
  return text ? text.charAt(0).toLocaleLowerCase() + text.slice(1) : "";
}

const DATE_TYPES = new Set(["date", "month", "datetime-local", "time"]);

// "2026-10-01T00:00" → "01/10/2026 00:00", "2026-10" → "10/2026" — the min/max bounds of
// date inputs are ISO strings, unreadable as-is in a message.
function formatBound(type, value) {
  if (!value) return value;
  if (type === "month") {
    const [y, m] = value.split("-");
    return `${m}/${y}`;
  }
  if (type === "date" || type === "datetime-local") {
    const [date, time] = value.split("T");
    const [y, m, d] = date.split("-");
    return time ? `${d}/${m}/${y} ${time.slice(0, 5)}` : `${d}/${m}/${y}`;
  }
  return value;
}

function capitalize(s) {
  return s ? s.charAt(0).toLocaleUpperCase() + s.slice(1) : s;
}

/** Which constraint failed → a specific, human message. `messages` overrides per rule. */
function describe(el, t, messages = {}) {
  const v = el.validity;
  const label = fieldLabel(el);
  const isDate = DATE_TYPES.has(el.type);
  const pick = (key, fallback) => messages[key] ?? fallback;

  if (v.customError) return el.validationMessage;
  if (v.valueMissing) {
    if (el.type === "checkbox") return pick("valueMissing", t("checkboxRequired"));
    if (el.tagName === "SELECT") return pick("valueMissing", label ? t("selectRequired", { label }) : t("selectRequiredGeneric"));
    if (isDate) return pick("valueMissing", label ? t("dateRequired", { label }) : t("requiredGeneric"));
    return pick("valueMissing", label ? t("required", { label }) : t("requiredGeneric"));
  }
  if (v.badInput) return pick("badInput", isDate ? t("dateIncomplete", { label }) : t("invalid", { label }));
  if (v.typeMismatch) return pick("typeMismatch", el.type === "email" ? t("emailInvalid") : t("invalid", { label }));
  if (v.tooShort) return pick("tooShort", t("tooShort", { label, min: el.minLength, length: el.value.length }));
  if (v.tooLong) return pick("tooLong", t("tooLong", { label, max: el.maxLength }));
  if (v.rangeUnderflow || v.rangeOverflow) {
    if (isDate) {
      return v.rangeUnderflow
        ? pick("rangeUnderflow", t("dateMin", { label, min: formatBound(el.type, el.min) }))
        : pick("rangeOverflow", t("dateMax", { label, max: formatBound(el.type, el.max) }));
    }
    if (el.min !== "" && el.max !== "") return pick("rangeUnderflow", t("numberRange", { label, min: el.min, max: el.max }));
    return v.rangeUnderflow
      ? pick("rangeUnderflow", t("numberMin", { label, min: el.min }))
      : pick("rangeOverflow", t("numberMax", { label, max: el.max }));
  }
  // `title` already explains the expected format (e.g. the currency code field).
  if (v.patternMismatch) return pick("patternMismatch", el.title || t("invalid", { label }));
  return label ? t("invalid", { label }) : t("invalidGeneric");
}

/**
 * Wires a control (by ref) into the enclosing <Field>. Returns the props to spread on the
 * element and whether it is currently shown as invalid.
 */
export function useFieldValidation(ref, { validate, messages } = {}) {
  const field = useContext(FieldContext);
  const { t } = useTranslation("validation");
  const message = useCallback((el) => capitalize(describe(el, t, messages)), [t, messages]);

  // Every render: re-apply the custom rule (it may depend on OTHER fields, e.g. end ≥
  // start), and once an error is showing, keep it in sync as the user edits — updated
  // while still wrong, cleared the moment the value becomes valid.
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    el.setCustomValidity((validate && validate(el.value, el)) || "");
    if (field?.error) field.setError(el.validity.valid ? "" : message(el));
  });

  const onInvalid = (e) => {
    e.preventDefault(); // no native bubble
    const el = e.currentTarget;
    field?.setError(message(el));
    // Cancelling the event also stops the browser from focusing the first bad field.
    if (el.form?.querySelector(":invalid") === el) el.focus();
  };

  const invalid = Boolean(field?.error);
  return {
    invalid,
    props: {
      onInvalid,
      "aria-invalid": invalid || undefined,
      "aria-describedby": invalid ? field.errorId : undefined,
    },
  };
}
