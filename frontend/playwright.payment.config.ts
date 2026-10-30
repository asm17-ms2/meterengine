import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  testMatch: "payment-registration.contract.spec.ts",
  outputDir: "/tmp/meterengine-payment-registration-results",
  timeout: 45_000,
  expect: { timeout: 10_000 },
  workers: 1,
  retries: 0,
  reporter: "list",
  use: { baseURL: "http://localhost:33149", browserName: "chromium", trace: "off" },
  webServer: [
    { command: "node e2e/payment-registration-server.mjs", url: "http://127.0.0.1:38149/health", reuseExistingServer: false },
    {
      command: "node e2e/start-frontend.mjs",
      url: "http://127.0.0.1:33149/health",
      env: { METERENGINE_API_BASE_URL: "http://127.0.0.1:38149", NEXT_TELEMETRY_DISABLED: "1", PORT: "33149" },
      reuseExistingServer: false,
      timeout: 60_000,
    },
  ],
});
