import { useRef } from "react";

// Matches the backend's @Digits(integer = 16, fraction = 2) on every amount field (DECIMAL(18, 2)).
const MAX_INTEGER_DIGITS = 16;
const MAX_FRACTION_DIGITS = 2;

function toRaw(displayValue) {
  const raw = displayValue.replace(/,/g, "").replace(/[^\d.]/g, "");
  const firstDot = raw.indexOf(".");
  if (firstDot === -1) {
    return raw.slice(0, MAX_INTEGER_DIGITS);
  }
  const intPart = raw.slice(0, firstDot).slice(0, MAX_INTEGER_DIGITS);
  const decPart = raw.slice(firstDot + 1).replace(/\./g, "").slice(0, MAX_FRACTION_DIGITS);
  return `${intPart}.${decPart}`;
}

function format(raw) {
  if (!raw) return "";
  const [intPart, decPart] = raw.split(".");
  const groupedInt = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
  return decPart === undefined ? groupedInt : `${groupedInt}.${decPart}`;
}

/**
 * Amount input that groups digits with "," while typing (e.g. "1234567" -> "1,234,567").
 * `value`/`onChange` keep the plain unformatted number as a string (e.g. "1234567.5"), same
 * shape a native `<input type="number">` gives — every caller's `Number(form.amount)` etc.
 * keeps working unchanged; only the displayed text is formatted. Renders as `type="text"`
 * (native `type="number"` rejects the "," character), so `min`/`step` aren't enforced by the
 * browser here — negative and non-numeric characters are stripped as you type instead, and the
 * digits are capped at 16 before / 2 after the decimal point (a plain `maxLength` can't do this:
 * it would count the "," and "." the display inserts).
 */
export default function AmountInput({ value, onChange, ...props }) {
  const inputRef = useRef(null);

  function handleChange(e) {
    const input = e.target;
    const prevDisplay = input.value;
    const caret = input.selectionStart ?? prevDisplay.length;
    const digitsBeforeCaret = prevDisplay.slice(0, caret).replace(/[^\d.]/g, "").length;

    const raw = toRaw(prevDisplay);
    onChange(raw);

    const formatted = format(raw);
    requestAnimationFrame(() => {
      if (!inputRef.current) return;
      let seen = 0;
      let pos = formatted.length;
      for (let i = 0; i < formatted.length; i++) {
        if (/[\d.]/.test(formatted[i])) seen++;
        if (seen >= digitsBeforeCaret) {
          pos = i + 1;
          break;
        }
      }
      inputRef.current.setSelectionRange(pos, pos);
    });
  }

  return (
    <input {...props} ref={inputRef} type="text" inputMode="decimal" value={format(String(value ?? ""))} onChange={handleChange} />
  );
}
