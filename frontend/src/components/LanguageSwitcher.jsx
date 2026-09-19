import { useTranslation } from "react-i18next";

/** README "13. Không i18n" — persists the choice (see i18n.js's localStorage detector). */
export default function LanguageSwitcher() {
  const { t, i18n } = useTranslation("common");

  return (
    <label className="language-switcher">
      {t("language")}
      <select value={i18n.language} onChange={(e) => i18n.changeLanguage(e.target.value)}>
        <option value="vi">{t("languageVi")}</option>
        <option value="en">{t("languageEn")}</option>
      </select>
    </label>
  );
}
