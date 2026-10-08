import i18n from "i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import { initReactI18next } from "react-i18next";

// One namespace JSON per page/component (src/locales/<lng>/<namespace>.json), gathered per language by
// src/locales/<lng>/index.js — adding a new namespace file is all a page needs to do; nothing here has to be
// edited (README "13. Không i18n"). Each language is its own lazily-loaded chunk: bundling both eagerly put
// every translation of both languages (~120 KB) into the main script that every visitor downloads first.
const LANGUAGE_BUNDLES = {
  vi: () => import("./locales/vi/index.js"),
  en: () => import("./locales/en/index.js"),
};

const loadedBundles = {};
function loadLanguage(lng) {
  const load = LANGUAGE_BUNDLES[lng] ?? LANGUAGE_BUNDLES.vi;
  loadedBundles[lng] ??= load().then((module) => module.default);
  return loadedBundles[lng];
}

// Minimal i18next backend: hands out namespaces from the language's bundle (fetched once per language).
// i18next calls it on init for the active language plus the "vi" fallback, and again on changeLanguage.
const lazyBundleBackend = {
  type: "backend",
  init() {},
  read(lng, ns, callback) {
    loadLanguage(lng).then(
      (namespaces) => callback(null, namespaces[ns] ?? {}),
      (err) => callback(err, null),
    );
  },
};

function syncHtmlLang(lng) {
  document.documentElement.lang = lng;
}

/** Resolves once the active language's translations are loaded — main.jsx renders the app after this. */
export const i18nReady = loadLanguage("vi").then((viNamespaces) =>
  i18n
    .use(LanguageDetector)
    .use(lazyBundleBackend)
    .use(initReactI18next)
    .init({
      fallbackLng: "vi",
      supportedLngs: ["vi", "en"],
      // Load every namespace up front (they arrive together anyway), so no page ever renders an untranslated key.
      ns: Object.keys(viNamespaces),
      defaultNS: "common",
      interpolation: { escapeValue: false },
      // Deliberately no "navigator" here — this app defaults to Vietnamese for every
      // visitor regardless of browser/OS locale (matching its pre-i18n behavior), only
      // switching once someone explicitly picks a language via LanguageSwitcher, which
      // then sticks (localStorage) for that browser.
      detection: {
        order: ["localStorage"],
        caches: ["localStorage"],
        lookupLocalStorage: "fem-language",
      },
    })
    .then(() => {
      syncHtmlLang(i18n.resolvedLanguage);
      i18n.on("languageChanged", syncHtmlLang);
    }),
);

export default i18n;
