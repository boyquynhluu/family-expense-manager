// Shared Tailwind buttons. Pick the variant by what the button DOES, not how it should
// look: the main action of a form/section is `primary`; cancel / navigation / neutral
// actions are `secondary`; irreversible destructive actions (delete account, confirm
// disable 2FA, bulk delete) are `danger`; destructive-but-recoverable or "take something
// away" row actions (cancel invite, lock user, log out other sessions) are `danger-outline`;
// cautionary but undoable actions (leave family, pause, revoke admin, transfer ownership)
// are `warning-outline`; restoring / re-enabling is `success-outline`; inline text actions
// are `link`. Preflight is off (see tailwind.css), so every UA button default (border,
// background, font) is reset explicitly in BASE.

function cn(...classes) {
  return classes.filter(Boolean).join(" ");
}

const BASE =
  "inline-flex shrink-0 cursor-pointer items-center justify-center border-0 font-[inherit] font-medium " +
  "whitespace-nowrap transition-all duration-150 select-none " +
  "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-500 " +
  "disabled:cursor-not-allowed disabled:opacity-50 disabled:shadow-none";

const VARIANTS = {
  primary: "bg-indigo-600 text-white shadow-sm hover:enabled:bg-indigo-700 active:enabled:bg-indigo-800",
  secondary:
    "bg-white text-slate-700 shadow-sm ring-1 ring-inset ring-slate-300 hover:enabled:bg-slate-50 hover:enabled:text-slate-900",
  danger: "bg-red-600 text-white shadow-sm hover:enabled:bg-red-700 active:enabled:bg-red-800 focus-visible:outline-red-500",
  "danger-outline":
    "bg-white text-red-600 shadow-sm ring-1 ring-inset ring-red-200 hover:enabled:bg-red-50 hover:enabled:ring-red-300 focus-visible:outline-red-500",
  "warning-outline":
    "bg-white text-amber-700 shadow-sm ring-1 ring-inset ring-amber-300 hover:enabled:bg-amber-50 hover:enabled:ring-amber-500 focus-visible:outline-amber-500",
  "success-outline":
    "bg-white text-emerald-700 shadow-sm ring-1 ring-inset ring-emerald-300 hover:enabled:bg-emerald-50 hover:enabled:ring-emerald-500 focus-visible:outline-emerald-500",
  ghost: "bg-transparent text-slate-600 hover:enabled:bg-slate-100 hover:enabled:text-slate-900",
  // File-format actions, colored the way people already recognize each format:
  // Excel = green, CSV = sky blue, PDF = red; importing a file = indigo.
  excel:
    "bg-emerald-50 text-emerald-700 shadow-sm ring-1 ring-inset ring-emerald-200 hover:enabled:bg-emerald-100 hover:enabled:ring-emerald-300 focus-visible:outline-emerald-500",
  csv: "bg-sky-50 text-sky-700 shadow-sm ring-1 ring-inset ring-sky-200 hover:enabled:bg-sky-100 hover:enabled:ring-sky-300 focus-visible:outline-sky-500",
  pdf: "bg-rose-50 text-rose-700 shadow-sm ring-1 ring-inset ring-rose-200 hover:enabled:bg-rose-100 hover:enabled:ring-rose-300 focus-visible:outline-rose-500",
  import:
    "bg-indigo-50 text-indigo-700 shadow-sm ring-1 ring-inset ring-indigo-200 hover:enabled:bg-indigo-100 hover:enabled:ring-indigo-300",
  // Sidebar (dark background) actions.
  "dark-outline":
    "bg-transparent text-slate-300 ring-1 ring-inset ring-slate-600 hover:enabled:bg-white/5 hover:enabled:text-white",
  // Big pill call-to-action on the auth pages (Login/Register/...).
  hero:
    "bg-linear-to-br from-indigo-500 to-indigo-600 font-bold tracking-wide text-white " +
    "shadow-[0_12px_28px_-6px_rgba(79,70,229,0.65)] hover:enabled:-translate-y-px hover:enabled:from-indigo-600 " +
    "hover:enabled:to-indigo-700 hover:enabled:shadow-[0_16px_34px_-6px_rgba(79,70,229,0.8)]",
  link: "bg-transparent p-0 font-semibold text-indigo-600 underline underline-offset-2 hover:enabled:text-indigo-800",
};

const SIZES = {
  sm: "h-8 gap-1.5 px-3 text-xs",
  md: "h-9 gap-2 px-4 text-sm",
  lg: "h-11 gap-2 px-5 text-sm",
  // `link` buttons sit inline in text, so they get no box at all.
  none: "gap-1 text-xs",
};

function radius(variant, size, pill) {
  if (variant === "link") return "";
  if (pill || variant === "hero") return "rounded-full";
  return size === "sm" ? "rounded-md" : "rounded-lg";
}

/** `pill` rounds the button fully — for tab/segment-style toggles. */
export function Button({ variant = "primary", size = "md", pill = false, type = "button", className, children, ...props }) {
  return (
    <button
      type={type}
      className={cn(
        BASE,
        SIZES[variant === "link" ? "none" : size],
        radius(variant, size, pill),
        VARIANTS[variant],
        className
      )}
      {...props}
    >
      {children}
    </button>
  );
}

const ICON_VARIANTS = {
  neutral: "bg-slate-100 text-slate-600 hover:enabled:bg-slate-200 hover:enabled:text-slate-900",
  danger: "bg-red-50 text-red-600 hover:enabled:bg-red-100 hover:enabled:text-red-700 focus-visible:outline-red-500",
  ghost: "bg-transparent text-slate-500 hover:enabled:bg-slate-100 hover:enabled:text-slate-900",
  "ghost-danger": "bg-transparent text-slate-500 hover:enabled:bg-red-50 hover:enabled:text-red-600",
  // Show/hide password eye inside an input: no box, just a color change.
  bare: "bg-transparent p-0 text-slate-500 hover:enabled:text-slate-800",
  // On the dark sidebar / mobile top bar.
  dark: "bg-transparent text-slate-300 hover:enabled:bg-white/10 hover:enabled:text-white",
};

const ICON_SIZES = {
  sm: "size-7 rounded-full",
  md: "size-8 rounded-lg",
  bare: "",
};

/** Icon-only button — always pass an `aria-label` (and usually a matching `title`). */
export function IconButton({ variant = "neutral", size = "md", type = "button", className, children, ...props }) {
  return (
    <button
      type={type}
      className={cn(BASE, "p-0", ICON_SIZES[variant === "bare" ? "bare" : size], ICON_VARIANTS[variant], className)}
      {...props}
    >
      {children}
    </button>
  );
}
