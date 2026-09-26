import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import { LIMITS } from "../utils/inputLimits";
import { ShieldIcon } from "./AppIcons";
import Modal from "./Modal";

const FORM_ID = "two-factor-setup-form";

/** "JBSWY3DPEHPK3PXP" -> "JBSW Y3DP EHPK 3PXP": easier to read and retype into an authenticator app. */
function groupSecret(secret) {
  return secret.replace(/(.{4})(?=.)/g, "$1 ");
}

/**
 * Step 1 of enabling 2FA (Profile page): scan the QR / type the key into an authenticator app, then
 * prove it works with the first 6-digit code. `setup` is `{ secret, qrDataUrl }` from /auth/2fa/setup.
 */
export default function TwoFactorSetupModal({ setup, code, onCodeChange, onSubmit, onClose, confirming, error }) {
  const { t } = useTranslation(["profile", "common"]);

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
          <button type="button" className="btn-secondary" onClick={onClose} disabled={confirming}>
            {t("common:cancel")}
          </button>
          <button type="submit" form={FORM_ID} disabled={confirming || code.length !== LIMITS.totpCode}>
            {confirming ? t("confirmingTwoFactorLoading") : t("confirmAndEnableButton")}
          </button>
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
                  <button type="button" className="btn-secondary two-factor-copy" onClick={copySecret}>
                    {t("copySecretButton")}
                  </button>
                </div>
              </div>
            </div>
          </section>

          <section className="two-factor-step">
            <span className="two-factor-step-number">2</span>
            <form id={FORM_ID} className="two-factor-step-content" onSubmit={onSubmit}>
              <h3>{t("twoFactorStepVerifyTitle")}</h3>
              <p className="two-factor-hint">{t("twoFactorStepVerifyHint")}</p>
              <input
                className="two-factor-code-input"
                value={code}
                // Digits only, capped here rather than with maxLength: a pasted "123 456" is 7 characters,
                // and maxLength would cut it to "123 45" before the space could be stripped.
                onChange={(e) => onCodeChange(e.target.value.replace(/\D/g, "").slice(0, LIMITS.totpCode))}
                placeholder="000000"
                aria-label={t("verificationCodeLabel")}
                inputMode="numeric"
                autoComplete="one-time-code"
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
