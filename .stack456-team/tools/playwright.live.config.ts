// 밖에서 띄운 백엔드, 프론트, PG 대역에 frontend/e2e/*.real.spec.ts를 하나씩 돌리는 설정. 경로는 이 파일 위치 기준이다.
const API_URL = process.env.S1_API_URL ?? "http://127.0.0.1:38147";
const parsed = new URL(API_URL);
if (!["127.0.0.1", "localhost", "[::1]"].includes(parsed.hostname) || parsed.protocol !== "http:") throw new Error("로컬 HTTP 백엔드가 필요합니다");

export default {
  testDir: "../../frontend/e2e",
  testMatch: "*.real.spec.ts",
  outputDir: process.env.E2E_OUTPUT_DIR ?? "../../.s456-work/pw-results",
  timeout: 180_000,
  expect: { timeout: 10_000 },
  workers: 1,
  retries: 0,
  reporter: "list",
  use: { baseURL: "http://localhost:33147", browserName: "chromium", trace: "off", actionTimeout: 15_000, navigationTimeout: 30_000 },
};
