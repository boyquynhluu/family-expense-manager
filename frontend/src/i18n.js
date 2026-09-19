import i18n from "i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import { initReactI18next } from "react-i18next";

// One namespace JSON per page/component (src/locales/<lng>/<namespace>.json), picked up
// automatically via Vite's import.meta.glob — adding a new namespace file is all a page
// needs to do; nothing here has to be edited (README "13. Không i18n").
const modules = import.meta.glob("./locales/*/*.json", { eager: true });

const resources = {};
for (const path in modules) {
  const match = path.match(/\.\/locales\/([^/]+)\/([^/]+)\.json$/);
  if (!match) continue;
  const [, lng, ns] = match;
  resources[lng] ??= {};
  resources[lng][ns] = modules[path].default;
}

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources,
    fallbackLng: "vi",
    supportedLngs: ["vi", "en"],
    ns: Object.keys(resources.vi ?? {}),
    defaultNS: "common",
    interpolation: { escapeValue: false },
    detection: {
      order: ["localStorage", "navigator"],
      caches: ["localStorage"],
      lookupLocalStorage: "fem-language",
    },
  });

function syncHtmlLang(lng) {
  document.documentElement.lang = lng;
}
syncHtmlLang(i18n.resolvedLanguage);
i18n.on("languageChanged", syncHtmlLang);

export default i18n;
