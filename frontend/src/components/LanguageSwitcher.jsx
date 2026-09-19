import { useTranslation } from "react-i18next";

/**
 * README "13. Không i18n" — persists the choice (see i18n.js's localStorage detector).
 * `variant="light"` is for placing this over a dark/photo background (auth pages) —
 * the default styling assumes the dark sidebar it was originally built for.
 */
export default function LanguageSwitcher({ variant }) {
  const { t, i18n } = useTranslation("common");

  return (
    <label className={`language-switcher${variant ? ` language-switcher-${variant}` : ""}`}>
      {t("language")}
      <select value={i18n.language} onChange={(e) => i18n.changeLanguage(e.target.value)}>
        <option value="vi">{t("languageVi")}</option>
        <option value="en">{t("languageEn")}</option>
      </select>
    </label>
  );
}
