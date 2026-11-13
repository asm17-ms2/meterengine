import { existsSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { defineConfig } from "@playwright/test";

import { contractWebServers } from "./e2e/contract-stack";

if (!existsSync(join(__dirname, "e2e", "workspace.contract.spec.ts"))) throw new Error("Workspace E2E 시나리오가 없습니다. 해당 화면과 검증 파일이 포함된 브랜치에서 실행하세요.");

export default defineConfig({
  testDir: "./e2e",
  testMatch: "workspace.contract.spec.ts",
  outputDir: process.env.WORKSPACE_CONTRACT_OUTPUT_DIR ?? join(tmpdir(), "meterengine-workspace-contract-results"),
  timeout: 45_000,
  expect: { timeout: 10_000 },
  workers: 1,
  retries: 0,
  reporter: "list",
  use: {
    browserName: "chromium",
    trace: "off",
    screenshot: "off",
    video: "off",
  },
  webServer: contractWebServers("workspace-contract-server.mjs"),
});
