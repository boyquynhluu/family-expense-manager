import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// GitHub Pages serves this as a project page at /family-expense-manager/, so production
// builds need that as the base path; the dev server keeps serving from / for convenience.
export default defineConfig(({ command }) => ({
  base: command === "build" ? "/family-expense-manager/" : "/",
  plugins: [react()],
  server: {
    port: 5173,
  },
}));
