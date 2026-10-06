import { useRef } from "react";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import { LIMITS } from "../utils/inputLimits";
import { ShieldIcon } from "./AppIcons";
import Modal from "./Modal";
import { Button } from "./ui/Button";
import { Input } from "./ui/Input";

const FORM_ID = "two-factor-setup-form";

/** "JBSWY3DPEHPK3PXP" -> "JBSW Y3DP EHPK 3PXP": easier to read and retype into an authenticator app. */
function groupSecret(secret) {
  return secret.replace(/(.{4})(?=.)/g, "$1 ");
}

// A phone (touch screen) can open the otpauth:// link straight in its authenticator app — no second device needed
// to scan the QR. Desktop browsers usually have no handler for it, so the button is only offered on touch screens.
function isTouchDevice() {
  return typeof window !== "undefined" && window.matchMedia?.("(pointer: coarse)").matches;
}

/**
 * Step 1 of enabling 2FA (Profile page): scan the QR / type the key into an authenticator app, then
 * prove it works with the first 6-digit code. `setup` is `{ secret, otpAuthUri, qrDataUrl }` from /auth/2fa/setup.
 * The 6th digit submits on its own.
 */
export default function TwoFactorSetupModal({ setup, code, onCodeChange, onSubmit, onClose, confirming, error }) {
  const { t } = useTranslation(["profile", "common"]);
  const formRef = useRef(null);

  function handleCodeChange(value) {
    // Digits only, capped here rather than with maxLength: a pasted "123 456" is 7 characters,
    // and maxLength would cut it to "123 45" before the space could be stripped.
    const digits = value.replace(/\D/g, "").slice(0, LIMITS.totpCode);
    onCodeChange(digits);
    if (digits.length === LIMITS.totpCode && !confirming) {
      // After React has the new code, so onSubmit reads it.
      setTimeout(() => formRef.current?.requestSubmit());
    }
  }

  async function copySecret() {
    try {
      await navigator.clipboard.writeText(setup.secret);
      toast.success(t("secretCopied"));
    } catch {
      toast.error(t("copyFailed"));
    }
  }

  return (
    <Modal
      open={Boolean(setup)}
      onClose={onClose}
      dismissible={!confirming}
      closeLabel={t("common:close")}
      className="two-factor-modal"
      icon={<ShieldIcon />}
      title={t("twoFactorModalTitle")}
      subtitle={t("twoFactorModalSubtitle")}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={confirming}>
            {t("common:cancel")}
          </Button>
          <Button type="submit" form={FORM_ID} disabled={confirming || code.length !== LIMITS.totpCode}>
            {confirming ? t("confirmingTwoFactorLoading") : t("confirmAndEnableButton")}
          </Button>
        </>
      }
    >
      {setup && (
        <>
          <section className="two-factor-step">
            <span className="two-factor-step-number">1</span>
            <div className="two-factor-step-content">
              <h3>{t("twoFactorStepScanTitle")}</h3>
              <p className="two-factor-hint">{t("twoFactorStepScanHint")}</p>
              <div className="two-factor-scan">
                <img
                  className="two-factor-qr"
                  src={setup.qrDataUrl}
                  alt={t("qrCodeAlt")}
                  width={184}
                  height={184}
                />
                <div className="two-factor-manual">
                  <span className="two-factor-hint">{t("twoFactorManualEntryLabel")}</span>
                  <code className="two-factor-secret">{groupSecret(setup.secret)}</code>
                  <Button variant="secondary" size="sm" className="self-start" onClick={copySecret}>
                    {t("copySecretButton")}
                  </Button>
                  {setup.otpAuthUri && isTouchDevice() && (
                    <a className="self-start text-sm font-semibold text-indigo-600 underline underline-offset-2" href={setup.otpAuthUri}>
                      {t("openInAuthenticatorButton")}
                    </a>
                  )}
                </div>
              </div>
            </div>
          </section>

          <section className="two-factor-step">
            <span className="two-factor-step-number">2</span>
            <form id={FORM_ID} ref={formRef} className="two-factor-step-content" onSubmit={onSubmit}>
              <h3>{t("twoFactorStepVerifyTitle")}</h3>
              <p className="two-factor-hint">{t("twoFactorStepVerifyHint")}</p>
              <Input
                className="two-factor-code-input"
                value={code}
                onChange={(e) => handleCodeChange(e.target.value)}
                placeholder="000000"
                aria-label={t("verificationCodeLabel")}
                inputMode="numeric"
                autoComplete="one-time-code"
                pattern="[0-9]{6}"
                readOnly={confirming}
                autoFocus
                required
              />
              {error && <p className="error-text">{error}</p>}
            </form>
          </section>
        </>
      )}
    </Modal>
  );
}
