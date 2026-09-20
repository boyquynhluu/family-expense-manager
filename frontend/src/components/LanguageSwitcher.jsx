import { useTranslation } from "react-i18next";

const LANGUAGES = [
  { code: "vi", label: "VI" },
  { code: "en", label: "EN" },
];

/**
 * README "13. Không i18n" — persists the choice (see i18n.js's localStorage detector).
 * `variant="floating"` pins it to the top-right corner (used on the public auth pages,
 * which have no sidebar to put it in) — the default renders inline wherever it's placed
 * (the authenticated sidebar).
 */
export default function LanguageSwitcher({ variant }) {
  const { t, i18n } = useTranslation("common");

  return (
    <div
      className={`language-toggle${variant ? ` language-toggle-${variant}` : ""}`}
      role="group"
      aria-label={t("language")}
    >
      {LANGUAGES.map((lang) => (
        <button
          key={lang.code}
          type="button"
          className={`language-toggle-btn${i18n.language === lang.code ? " is-active" : ""}`}
          onClick={() => i18n.changeLanguage(lang.code)}
          aria-pressed={i18n.language === lang.code}
        >
          {lang.label}
        </button>
      ))}
    </div>
  );
}
