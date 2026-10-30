import { existsSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { defineConfig } from "@playwright/test";

const API_URL = process.env.S1_API_URL ?? "http://127.0.0.1:38147";
const parsed = new URL(API_URL);
if (!["127.0.0.1", "localhost", "[::1]"].includes(parsed.hostname) || parsed.protocol !== "http:") throw new Error("MVP 검증에는 로컬 HTTP 백엔드가 필요합니다");

const scenarios = ["mvp.real.spec.ts", "environment-cycle.real.spec.ts", "people.real.spec.ts", "events.real.spec.ts", "operator-create.real.spec.ts", "archive-filters.real.spec.ts", "aggregation.real.spec.ts", "invoice-links.real.spec.ts", "customer-payment-retry.real.spec.ts", "permissions.real.spec.ts", "query-workspace.real.spec.ts"];
const missingScenarios = scenarios.filter(file => !existsSync(join(__dirname, "e2e", file)));
if (missingScenarios.length) throw new Error(`MVP E2E 시나리오가 없습니다: ${missingScenarios.join(", ")}. 해당 기능과 검증 파일이 포함된 브랜치에서 실행하세요.`);

export default defineConfig({
  testDir: "./e2e",
  testMatch: scenarios,
  outputDir: join(tmpdir(), "meterengine-mvp-real-results"),
  timeout: 180_000,
  expect: { timeout: 10_000 },
  workers: 1,
  retries: 0,
  reporter: "list",
  use: { baseURL: "http://localhost:33147", browserName: "chromium", trace: "off", actionTimeout: 15_000, navigationTimeout: 30_000 },
  webServer: [
    ...(process.env.MVP_EXTERNAL_PG_FIXTURE === "1" ? [] : [{ command: "node e2e/toss-server.mjs", url: "http://127.0.0.1:38447/health", reuseExistingServer: false,
      env: { MVP_PG_STATE_FILE: process.env.MVP_PG_STATE_FILE ?? join(tmpdir(), "meterengine-mvp-real", "toss-state.json") } }]),
    {
      command: "node e2e/start-frontend.mjs",
      url: "http://127.0.0.1:33147/health",
      env: { METERENGINE_API_BASE_URL: API_URL, NEXT_TELEMETRY_DISABLED: "1", PORT: "33147" },
      reuseExistingServer: process.env.MVP_REUSE_FRONTEND === "1",
      timeout: 60_000,
    },
  ],
});
