import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import { i18nReady } from "./i18n";
import "./tailwind.css";
import "./index.css";

// Translations are a separate chunk per language now (see i18n.js) — render once they're in, so no page ever
// flashes raw translation keys. If they fail to load, render anyway rather than leave a blank page.
i18nReady
  .catch((err) => console.error("Failed to load translations", err))
  .finally(() => {
    ReactDOM.createRoot(document.getElementById("root")).render(
      <React.StrictMode>
        <App />
      </React.StrictMode>
    );
  });
