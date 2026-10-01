// Shared Tailwind form controls. Same 36px height (h-9) as the md Button so a field and
// its submit button line up in an .inline-form row. Preflight is off (see tailwind.css),
// so the UA border/background/font of every control is reset explicitly here.
import { forwardRef } from "react";

function cn(...classes) {
  return classes.filter(Boolean).join(" ");
}

// Shape/size only — colors live in LIGHT/DARK so two background utilities never end up
// on the same element (their CSS order, not the class order, would decide the winner).
const CONTROL =
  "block h-9 min-w-0 rounded-lg border-0 px-3 py-0 font-[inherit] text-sm font-normal " +
  "shadow-sm ring-1 ring-inset transition-shadow duration-150 focus:outline-none focus:ring-2 " +
  "disabled:cursor-not-allowed";

const LIGHT =
  "bg-white text-slate-900 ring-slate-300 placeholder:text-slate-400 hover:ring-slate-400 focus:ring-indigo-500 " +
  "disabled:bg-slate-100 disabled:text-slate-500 disabled:ring-slate-200 " +
  "read-only:bg-slate-50 read-only:hover:ring-slate-300 read-only:focus:ring-slate-300";

// Dark sidebar (FamilySwitcher).
const DARK =
  "bg-slate-800 normal-case tracking-normal text-slate-100 ring-slate-600 hover:ring-slate-500 focus:ring-indigo-400";

const COLOR_CONTROL =
  "block h-9 w-14 cursor-pointer rounded-lg border-0 bg-white p-1 shadow-sm ring-1 ring-inset ring-slate-300 " +
  "hover:ring-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500";

/** Text-like inputs: text/email/password/number/search/date/month/datetime-local/color. */
export const Input = forwardRef(function Input({ type = "text", className, ...props }, ref) {
  return (
    <input ref={ref} type={type} className={cn(type === "color" ? COLOR_CONTROL : `${CONTROL} ${LIGHT}`, className)} {...props} />
  );
});

/**
 * Native <select> (keeps the OS picker on phones) with the browser arrow replaced by a
 * chevron drawn on top — the grid stacks both in one cell so the select keeps its own
 * intrinsic width.
 */
export const Select = forwardRef(function Select({ variant = "default", className, children, ...props }, ref) {
  return (
    <div className="grid grid-cols-1">
      <select
        ref={ref}
        className={cn(
          CONTROL,
          variant === "dark" ? DARK : LIGHT,
          "col-start-1 row-start-1 cursor-pointer appearance-none pr-9",
          className
        )}
        {...props}
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

export const Checkbox = forwardRef(function Checkbox({ className, ...props }, ref) {
  return (
    <input
      ref={ref}
      type="checkbox"
      className={cn(
        "m-0 size-4 shrink-0 cursor-pointer rounded accent-indigo-600 align-middle disabled:cursor-not-allowed disabled:opacity-40",
        className
      )}
      {...props}
    />
  );
});
