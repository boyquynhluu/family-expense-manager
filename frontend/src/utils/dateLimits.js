// Mirrors the backend's @ReasonableDate (backend/common/.../validation/ReasonableDateValidator.java):
// not before 2000-01-01, not more than `maxYearsAhead` years after today. Passed as `min`/`max` on native
// date/datetime-local inputs so the picker itself keeps the user from choosing something the API would
// reject with a 400 — matching the values on each field's @ReasonableDate(maxYearsAhead = ...):
//   - TransactionRequest.occurredAt / CreateWalletTransferRequest.occurredAt: default (1)
//   - CreateRecurringTransactionRequest.startDate: 5, .endDate: 50
const MIN_YEAR = 2000;

function pad(n) {
  return String(n).padStart(2, "0");
}

function plusYears(years) {
  const d = new Date();
  d.setFullYear(d.getFullYear() + years);
  return d;
}

export function minDate() {
  return `${MIN_YEAR}-01-01`;
}

export function maxDate(maxYearsAhead = 1) {
  const d = plusYears(maxYearsAhead);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function minDateTime() {
  return `${minDate()}T00:00`;
}

export function maxDateTime(maxYearsAhead = 1) {
  return `${maxDate(maxYearsAhead)}T23:59`;
}
