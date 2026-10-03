// Shared Tailwind table. Desktop: bordered rounded card, sticky-looking slate header,
// zebra rows with hover, right-aligned tabular numbers. Below md (768px) every body row
// becomes its own card and each <Td data-label="..."> shows its label above the value —
// the same phone layout the old global table CSS in index.css used to provide.

import { Children } from "react";

function cn(...classes) {
  return classes.filter(Boolean).join(" ");
}

export function Table({ className, children, ...props }) {
  return (
    <div
      className={cn(
        // Space from whatever sits above it (e.g. an .inline-form, which has no bottom
        // margin) — collapses with the previous sibling's own margin (h2, .filter-bar).
        "overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-sm [*+&]:mt-4",
        "max-md:overflow-visible max-md:rounded-none max-md:border-0 max-md:bg-transparent max-md:shadow-none",
        className
      )}
    >
      <table className="w-full border-collapse text-sm max-md:block" {...props}>
        {children}
      </table>
    </div>
  );
}

export function THead({ children }) {
  return <thead className="bg-slate-50 max-md:hidden">{children}</thead>;
}

export function TBody({ children }) {
  return (
    <tbody
      className={cn(
        "[&>tr]:transition-colors [&>tr:nth-child(even)]:bg-slate-50/70 [&>tr:hover]:bg-indigo-50/70",
        "[&>tr:last-child>td]:border-b-0",
        "max-md:block max-md:space-y-3",
        "max-md:[&>tr]:block max-md:[&>tr]:rounded-xl max-md:[&>tr]:border max-md:[&>tr]:border-slate-200",
        "max-md:[&>tr]:bg-white max-md:[&>tr]:px-4 max-md:[&>tr]:py-3 max-md:[&>tr]:shadow-sm",
        "max-md:[&>tr:nth-child(even)]:bg-white max-md:[&>tr:hover]:bg-white"
      )}
    >
      {children}
    </tbody>
  );
}

export function TFoot({ children }) {
  return (
    <tfoot
      className={cn(
        "bg-slate-50 font-semibold [&_td]:border-t [&_td]:border-b-0 [&_td]:border-slate-200",
        "max-md:mt-3 max-md:block max-md:[&>tr]:block max-md:[&>tr]:rounded-xl max-md:[&>tr]:border",
        "max-md:[&>tr]:border-slate-200 max-md:[&>tr]:px-4 max-md:[&>tr]:py-3 max-md:[&_td]:border-t-0"
      )}
    >
      {children}
    </tfoot>
  );
}

const ALIGN = { left: "text-left", right: "text-right", center: "text-center" };

export function Th({ align = "left", className, children, ...props }) {
  return (
    <th
      className={cn(
        "whitespace-nowrap border-b border-slate-200 px-4 py-3 text-xs font-semibold uppercase tracking-wider text-slate-500",
        ALIGN[align],
        className
      )}
      {...props}
    >
      {children}
    </th>
  );
}

/**
 * `align="right"` is meant for money/number columns (tabular digits, no wrapping).
 * `actions` wraps the children in the shared `.row-actions` flex container instead of
 * turning the <td> itself into a flexbox, which would break the table's cell layout.
 */
export function Td({ align = "left", actions = false, className, children, ...props }) {
  return (
    <td
      className={cn(
        "border-b border-slate-100 px-4 py-3 align-middle text-slate-700",
        ALIGN[align],
        align === "right" && "whitespace-nowrap tabular-nums",
        actions && "w-px whitespace-nowrap",
        "max-md:block max-md:w-auto max-md:border-0 max-md:px-0 max-md:py-1 max-md:text-left max-md:whitespace-normal",
        "max-md:wrap-anywhere",
        "max-md:before:block max-md:before:text-[0.68rem] max-md:before:font-bold max-md:before:uppercase",
        "max-md:before:tracking-wider max-md:before:text-slate-400 max-md:before:content-[attr(data-label)]",
        className
      )}
      {...props}
    >
      {/* No wrapper when there's nothing to show (e.g. no actions allowed on this row) —
          on phones the wrapper's top divider would otherwise draw an empty line. */}
      {actions && Children.toArray(children).length > 0 ? (
        <div className="row-actions justify-end">{children}</div>
      ) : (
        children
      )}
    </td>
  );
}
