import { configDefaults, defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import process from "node:process";

export default defineConfig({
  plugins: [react()],
  server: {
    // The backend only allows this origin, so a busy port must be an error, not a silent switch to 5174
    port: 5173,
    strictPort: true,
    proxy: {
      "/api": {
        // The end-to-end tests point this at their own backend
        target: process.env.API_PROXY_TARGET ?? "http://localhost:8090",
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    css: false,
    // Playwright specs are run by Playwright, not Vitest
    exclude: [...configDefaults.exclude, "e2e/**"],
  },
});