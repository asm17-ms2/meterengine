import { test as base } from "@playwright/test";

export const test = base.extend({
  page: async ({ page }, runTest) => {
    if (process.env.E2E_SLOW_BROWSER === "1") {
      const session = await page.context().newCDPSession(page);
      await session.send("Emulation.setCPUThrottlingRate", { rate: 4 });
      await session.send("Network.enable");
      await session.send("Network.emulateNetworkConditions", {
        offline: false, latency: 150, downloadThroughput: 500_000, uploadThroughput: 125_000,
      });
    }
    await runTest(page);
  },
});
