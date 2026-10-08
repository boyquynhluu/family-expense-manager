// Every namespace of this language as one chunk ({ <namespace>: <json> }), imported lazily by src/i18n.js —
// so a visitor only downloads the language they use. Adding src/locales/<lng>/<namespace>.json is still all a
// page needs; it is picked up here automatically.
const files = import.meta.glob("./*.json", { eager: true, import: "default" });

export default Object.fromEntries(Object.entries(files).map(([path, json]) => [path.slice(2, -".json".length), json]));
