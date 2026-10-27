import { defineConfig } from "@playwright/test";

const mode = process.env.E2E_MODE === "real" ? "real" : "contract";
const port = mode === "real" ? 33147 : 33148;
const apiUrl = mode === "real" ? (process.env.S1_API_URL ?? "http://127.0.0.1:38147") : "http://127.0.0.1:38148";
const parsedApi = new URL(apiUrl);
if (!["127.0.0.1", "localhost", "[::1]"].includes(parsedApi.hostname) || parsedApi.protocol !== "http:") throw new Error("E2E requires a local HTTP backend");

export default defineConfig({
  testDir: "./e2e",
  outputDir: process.env.E2E_OUTPUT_DIR ?? `/tmp/meterengine-fe-onboarding-results-${mode}`,
  timeout: 45_000,
  expect: { timeout: 10_000 },
  workers: 1,
  retries: 0,
  reporter: "list",
  use: { baseURL: `http://localhost:${port}`, browserName: "chromium", trace: "off" },
  projects: [
    { name: "contract", testMatch: "auth.contract.spec.ts" },
    { name: "real-stack", testMatch: "auth.real.spec.ts" },
  ],
  webServer: [
    ...(mode === "contract" ? [{ command: "node e2e/contract-server.mjs", url: `${apiUrl}/health`, reuseExistingServer: false }] : []),
    {
      command: "node e2e/start-frontend.mjs",
      url: `http://127.0.0.1:${port}/health`,
      env: { METERENGINE_API_BASE_URL: apiUrl, NEXT_TELEMETRY_DISABLED: "1", PORT: String(port) },
      reuseExistingServer: mode === "real" && process.env.E2E_REUSE_FRONTEND === "1",
      timeout: 60_000,
    },
  ],
});
