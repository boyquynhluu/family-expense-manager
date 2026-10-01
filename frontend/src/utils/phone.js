// Vietnamese mobile numbers — mirrors backend auth PhoneNumbers: "0912 345 678", "84912345678"
// and "+84 912.345.678" are all the same number, stored as +84912345678.

const NORMALIZED = /^\+84[35789]\d{8}$/;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Normalised +84… form, or null when blank / not a valid mobile number. */
export function normalizePhone(raw) {
  const digits = (raw ?? "").replace(/[\s.\-()]/g, "");
  if (!digits) return null;
  let candidate = digits;
  if (digits.startsWith("84") && digits.length === 11) candidate = `+${digits}`;
  else if (digits.startsWith("0") && digits.length === 10) candidate = `+84${digits.slice(1)}`;
  return NORMALIZED.test(candidate) ? candidate : null;
}

export function isValidPhone(raw) {
  return normalizePhone(raw) !== null;
}

/** "+84912345678" → "0912 345 678", the way people write it locally. */
export function formatPhone(normalized) {
  if (!normalized || !NORMALIZED.test(normalized)) return normalized ?? "";
  const local = `0${normalized.slice(3)}`;
  return `${local.slice(0, 4)} ${local.slice(4, 7)} ${local.slice(7)}`;
}

/** Login accepts an email or a phone number; anything with an '@' is treated as an email. */
export function looksLikeEmail(identifier) {
  return (identifier ?? "").includes("@");
}

export function isValidLoginIdentifier(identifier) {
  const value = (identifier ?? "").trim();
  return looksLikeEmail(value) ? EMAIL.test(value) : isValidPhone(value);
}
