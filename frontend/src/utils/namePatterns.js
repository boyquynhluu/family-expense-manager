// Allowed characters for user-typed names and icons (wallets, categories).
// Same rules as backend expense domain/NameRules.java (NAME_REGEX / ICON_REGEX) — keep in sync.

// Letters (with accents), digits, spaces and - _ . , & ( ) / ' +, at least one letter.
export const NAME_PATTERN = /^(?=.*\p{L})[\p{L}\p{M}\p{N} .,&()/'+_-]+$/u;

// A short code ("AI") or emoji, incl. joiners, variation selectors, skin tones, keycaps and flags.
export const ICON_PATTERN =
  // The class lists emoji joiners/modifiers one by one on purpose (each code point is allowed alone).
  // eslint-disable-next-line no-misleading-character-class
  /^[\p{L}\p{M}\p{N}\p{Extended_Pictographic}‍️⃣\u{1F3FB}-\u{1F3FF}\u{1F1E6}-\u{1F1FF} _-]*$/u;

/** True when a non-blank value breaks the name rule (blank is left to `required`). */
export function hasInvalidNameChars(value) {
  return Boolean(value.trim()) && !NAME_PATTERN.test(value);
}

export function hasInvalidIconChars(value) {
  return Boolean(value) && !ICON_PATTERN.test(value);
}
