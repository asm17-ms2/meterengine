import { defineConfig } from "@playwright/test";

const baseURL = process.env.LUMEN_E2E_URL ?? process.env.LUMEN_E2E_BASE_URL ?? "http://localhost:3200";
const origin = new URL(baseURL);
if (origin.protocol !== "http:" || !["localhost", "127.0.0.1", "[::1]"].includes(origin.hostname)) {
  throw new Error("도입사 예제 E2E는 로컬 HTTP 서버에서만 실행합니다.");
}

export default defineConfig({
  testDir: "./e2e",
  testMatch: "*.real.spec.ts",
  outputDir: "/private/tmp/meterengine-llm-tenant-2026-10-27/results",
  timeout: 180_000,
  expect: { timeout: 15_000 },
  retries: 0,
  workers: 1,
  reporter: "list",
  use: {
    baseURL,
    browserName: "chromium",
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
    trace: "off",
    screenshot: "off",
    video: "off",
  },
});
