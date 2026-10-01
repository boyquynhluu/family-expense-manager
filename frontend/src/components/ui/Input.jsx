// Shared Tailwind form controls. Same 36px height (h-9) as the md Button so a field and
// its submit button line up in an .inline-form row. Preflight is off (see tailwind.css),
// so the UA border/background/font of every control is reset explicitly here.
//
// Every control takes part in the custom validation of ./Field.jsx: inside a <Field> its
// failed constraint shows as a message under it (no native browser bubble), and an
// optional `validate(value, element) => message | ""` adds a rule HTML can't express.
// `messages` overrides the text for a specific constraint, e.g. { valueMissing: "..." }.
import { forwardRef, useEffect, useImperativeHandle, useRef } from "react";
import { useFieldValidation } from "./fieldValidation";

function cn(...classes) {
  return classes.filter(Boolean).join(" ");
}

// Shape/size only — colors live in LIGHT/INVALID/DARK so two background or ring utilities
// never end up on the same element (their CSS order, not the class order, would decide).
const CONTROL =
  "block h-9 min-w-0 rounded-lg border-0 px-3 py-0 font-[inherit] text-sm font-normal " +
  "shadow-sm ring-1 ring-inset transition-shadow duration-150 focus:outline-none focus:ring-2 " +
  "disabled:cursor-not-allowed";

const LIGHT =
  "bg-white text-slate-900 ring-slate-300 placeholder:text-slate-400 hover:ring-slate-400 focus:ring-indigo-500 " +
  "disabled:bg-slate-100 disabled:text-slate-500 disabled:ring-slate-200 " +
  "read-only:bg-slate-50 read-only:hover:ring-slate-300 read-only:focus:ring-slate-300";

const INVALID =
  "bg-red-50/40 text-slate-900 ring-red-400 placeholder:text-slate-400 hover:ring-red-500 focus:ring-red-500";

// Dark sidebar (FamilySwitcher).
const DARK =
  "bg-slate-800 normal-case tracking-normal text-slate-100 ring-slate-600 hover:ring-slate-500 focus:ring-indigo-400";

const COLOR_CONTROL =
  "block h-9 w-14 cursor-pointer rounded-lg border-0 bg-white p-1 shadow-sm ring-1 ring-inset ring-slate-300 " +
  "hover:ring-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500";

/**
 * Text-like inputs: text/email/password/number/search/date/month/datetime-local/color.
 * `variant="bare"` drops all styling (the auth pages' inputs live inside their own
 * pill-shaped .auth-input-group) but keeps the validation behavior.
 */
export const Input = forwardRef(function Input(
  { type = "text", variant = "default", validate, messages, className, ...props },
  ref
) {
  const innerRef = useRef(null);
  useImperativeHandle(ref, () => innerRef.current);
  const validation = useFieldValidation(innerRef, { validate, messages });

  let styles;
  if (variant === "bare") styles = "";
  else if (type === "color") styles = COLOR_CONTROL;
  else styles = `${CONTROL} ${validation.invalid ? INVALID : LIGHT}`;

  return <input ref={innerRef} type={type} className={cn(styles, className) || undefined} {...props} {...validation.props} />;
});

/**
 * Native <select> (keeps the OS picker on phones) with the browser arrow replaced by a
 * chevron drawn on top — the grid stacks both in one cell so the select keeps its own
 * intrinsic width.
 */
export const Select = forwardRef(function Select(
  { variant = "default", validate, messages, className, children, ...props },
  ref
) {
  const innerRef = useRef(null);
  useImperativeHandle(ref, () => innerRef.current);
  const validation = useFieldValidation(innerRef, { validate, messages });

  let colors = LIGHT;
  if (variant === "dark") colors = DARK;
  else if (validation.invalid) colors = INVALID;

  return (
    <div className="grid grid-cols-1">
      <select
        ref={innerRef}
        className={cn(CONTROL, colors, "col-start-1 row-start-1 cursor-pointer appearance-none pr-9", className)}
        {...props}
        {...validation.props}
      >
        {children}
      </select>
      <svg
        viewBox="0 0 20 20"
        fill="currentColor"
        aria-hidden="true"
        className={cn(
          "pointer-events-none col-start-1 row-start-1 mr-2.5 size-4 self-center justify-self-end",
          variant === "dark" ? "text-slate-400" : "text-slate-500"
        )}
      >
        <path
          fillRule="evenodd"
          d="M5.22 8.22a.75.75 0 0 1 1.06 0L10 11.94l3.72-3.72a.75.75 0 1 1 1.06 1.06l-4.25 4.25a.75.75 0 0 1-1.06 0L5.22 9.28a.75.75 0 0 1 0-1.06Z"
          clipRule="evenodd"
        />
      </svg>
    </div>
  );
});

/**
 * `indeterminate` shows the "−" mixed state (e.g. a select-all box when only some rows
 * are ticked). It only exists as a DOM property, not an HTML attribute, hence the ref.
 */
export const Checkbox = forwardRef(function Checkbox(
  { indeterminate = false, validate, messages, className, ...props },
  ref
) {
  const innerRef = useRef(null);
  useImperativeHandle(ref, () => innerRef.current);
  useEffect(() => {
    if (innerRef.current) innerRef.current.indeterminate = indeterminate;
  }, [indeterminate]);
  const validation = useFieldValidation(innerRef, { validate, messages });
  return (
    <input
      ref={innerRef}
      type="checkbox"
      className={cn(
        "m-0 size-4 shrink-0 cursor-pointer rounded accent-indigo-600 align-middle disabled:cursor-not-allowed disabled:opacity-40",
        validation.invalid && "outline-2 outline-offset-2 outline-red-500",
        className
      )}
      {...props}
      {...validation.props}
    />
  );
});
