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
      {LANGUAGES.map((lang) => {
        const active = i18n.language === lang.code;
        return (
          <button
            key={lang.code}
            type="button"
            className={`cursor-pointer rounded-full border-0 px-4 py-1.5 text-[0.72rem] font-bold tracking-wide font-[inherit] transition-colors duration-150 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-400 ${
              active ? "bg-indigo-600 text-white shadow-sm" : "bg-transparent text-slate-400 hover:text-slate-100"
            }`}
            onClick={() => i18n.changeLanguage(lang.code)}
            aria-pressed={active}
          >
            {lang.label}
          </button>
        );
      })}
    </div>
  );
}
