// Custom validation messages instead of the browser's native bubble.
//
// The constraints themselves stay as plain HTML attributes (required, minLength, min/max,
// type="email", pattern...) so the browser still blocks the submit exactly as before; the
// controls (Input/Select/Checkbox/AmountInput) just cancel the `invalid` event — which
// suppresses the native bubble — and hand a message describing WHICH rule failed to the
// enclosing <Field>, which renders it under the control. Rules HTML can't express (amount
// > 0, end date ≥ start date...) go through the control's `validate` prop, which feeds
// setCustomValidity so they block the submit the same way.
import { useId, useMemo, useState } from "react";
import { FieldContext } from "./fieldValidation";

function ErrorIcon() {
  return (
    <svg viewBox="0 0 20 20" fill="currentColor" aria-hidden="true" className="mt-px size-3.5 shrink-0">
      <path
        fillRule="evenodd"
        d="M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0Zm-8-5a.75.75 0 0 1 .75.75v4.5a.75.75 0 0 1-1.5 0v-4.5A.75.75 0 0 1 10 5Zm0 10a1 1 0 1 0 0-2 1 1 0 0 0 0 2Z"
        clipRule="evenodd"
      />
    </svg>
  );
}

/**
 * Drop-in for `<label className="field">`: same markup, plus the error line.
 * `errorPlacement="after"` puts the message after the element instead of inside it —
 * for containers that lay their children out in a row (the auth pages' pill groups).
 */
export function Field({ as: Tag = "label", className = "field", errorPlacement = "inside", children, ...props }) {
  const [error, setError] = useState("");
  const errorId = useId();
  const value = useMemo(() => ({ error, setError, errorId }), [error, errorId]);

  // w-0 + min-w-full: the message wraps to the field's width instead of stretching the
  // field (and shoving the rest of an .inline-form row sideways) when it's long.
  const message = error ? (
    <span
      id={errorId}
      role="alert"
      data-field-error=""
      className="flex w-0 min-w-full basis-full items-start gap-1 text-xs font-medium normal-case tracking-normal text-red-600"
    >
      <ErrorIcon />
      <span>{error}</span>
    </span>
  ) : null;

  return (
    <FieldContext.Provider value={value}>
      {errorPlacement === "after" ? (
        <>
          <Tag className={className} {...props}>
            {children}
          </Tag>
          {message}
        </>
      ) : (
        <Tag className={className} {...props}>
          {children}
          {message}
        </Tag>
      )}
    </FieldContext.Provider>
  );
}
