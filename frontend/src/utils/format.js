// Backend containers run in UTC and serialize LocalDateTime without an offset, so a bare
// "2026-09-20T02:31:16" is a UTC wall time; without the "Z" the browser would read it as local.
export function formatServerDateTime(value, locale = "vi-VN") {
  if (!value) return "";
  const hasOffset = /(Z|[+-]\d{2}:?\d{2})$/.test(value);
  return new Date(hasOffset ? value : `${value}Z`).toLocaleString(locale);
}

export function formatCurrency(value, currency = "VND") {
  return new Intl.NumberFormat("vi-VN", { style: "currency", currency }).format(value ?? 0);
}
