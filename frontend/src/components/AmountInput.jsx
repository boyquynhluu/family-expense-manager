import { useRef } from "react";

function toRaw(displayValue) {
  let raw = displayValue.replace(/,/g, "").replace(/[^\d.]/g, "");
  const firstDot = raw.indexOf(".");
  if (firstDot !== -1) {
    raw = raw.slice(0, firstDot + 1) + raw.slice(firstDot + 1).replace(/\./g, "");
  }
  return raw;
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
 * browser here — negative and non-numeric characters are stripped as you type instead.
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
