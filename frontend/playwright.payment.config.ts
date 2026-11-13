import { defineConfig } from "@playwright/test";

import { contractWebServers } from "./e2e/contract-stack";

export default defineConfig({
  testDir: "./e2e",
  testMatch: "payment-registration.contract.spec.ts",
  outputDir: "/tmp/meterengine-payment-registration-results",
  timeout: 45_000,
  expect: { timeout: 10_000 },
  workers: 1,
  retries: 0,
  reporter: "list",
  use: { browserName: "chromium", trace: "off" },
  webServer: contractWebServers("payment-registration-server.mjs"),
});
