// Mirrors the backend's Bean Validation limits (the @Size on each request DTO / VARCHAR column),
// so the browser stops input at the same length the API would reject with a 400.
// Keep in sync with backend/**/dto/*Request.java.
export const LIMITS = {
  email: 255,
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
