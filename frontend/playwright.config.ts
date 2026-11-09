import { defineConfig, devices } from "@playwright/test";
import { Buffer } from "node:buffer";
import process from "node:process";

const isWindows = process.platform === "win32";

// Throwaway values for the end-to-end backend only. Never use them anywhere real.
const backendEnv = {
  ...process.env,
  SERVER_PORT: "8091",
  DB_URL: "jdbc:postgresql://localhost:5443/vitalsafe_e2e",
  DB_USER: "vitalsafe_e2e",
  DB_PASSWORD: "e2e_password",
  JWT_SECRET: Buffer.alloc(32, "j").toString("base64"),
  ENCRYPTION_KEY: Buffer.alloc(32, "e").toString("base64"),
  APP_CORS_ALLOWED_ORIGIN: "http://localhost:5174",
  APP_RATE_LIMIT_PER_MINUTE: "1000",
};

export default defineConfig({
  testDir: "./e2e",
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  timeout: 30_000,
  expect: { timeout: 7_000 },
  reporter: process.env.CI ? [["github"], ["html", { open: "never" }]] : [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: "http://localhost:5174",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: [
    {
      command: isWindows ? "mvnw.cmd spring-boot:run" : "./mvnw spring-boot:run",
      cwd: "../backend",
      // The public health check answers 200 once the backend is up
      url: "http://127.0.0.1:8091/actuator/health",
      timeout: 240_000,
      reuseExistingServer: !process.env.CI,
      env: backendEnv,
    },
    {
      command: "npm run dev -- --port 5174 --strictPort",
      url: "http://localhost:5174",
      timeout: 60_000,
      reuseExistingServer: !process.env.CI,
      env: { ...process.env, API_PROXY_TARGET: "http://127.0.0.1:8091" },
    },
  ],
});