// Mirrors the backend's Bean Validation limits (the @Size on each request DTO / VARCHAR column),
// so the browser stops input at the same length the API would reject with a 400.
// Keep in sync with backend/**/dto/*Request.java.
export const LIMITS = {
  // Smallest thu/chi amount (and recurring rule) — backend expense TransactionAmounts.MIN.
  minTransactionAmount: 10000,
  email: 255,
  // Typed form, separators included ("+84 912.345.678" = 15); stored normalised as +84XXXXXXXXX.
  phone: 20,
  password: 128, // an existing password (login, re-authentication): LoginRequest/ChangePasswordRequest
  newPasswordMin: 8, // a password being set: RegisterRequest/ResetPasswordRequest/... @Size(min = 8, max = 72)
  newPasswordMax: 72,
  displayName: 100,
  familyName: 100,
  walletName: 255,
  categoryName: 255,
  categoryIcon: 50,
  transactionNote: 500,
  transferNote: 255,
  totpCode: 6,
  twoFactorCode: 32, // TOTP or recovery code
  search: 100,
};
