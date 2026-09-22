import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Only the GitHub Pages build needs a base path — it serves this as a project page at
// /family-expense-manager/ (set via VITE_BASE_PATH in .github/workflows/frontend-ci.yml).
// Every other production build (the Docker/nginx image, a local `npm run build`) is
// served from the root of its own origin and must keep base "/", or the browser
// requests /family-expense-manager/assets/... there, gets nginx's SPA-fallback
// index.html back for that "file", and the app never boots (blank page).
export default defineConfig(({ command }) => ({
  base: command === "build" ? process.env.VITE_BASE_PATH || "/" : "/",
  plugins: [react()],
  server: {
    port: 5173,
  },
}));
