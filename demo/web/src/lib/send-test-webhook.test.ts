import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

test("시험 웹훅은 실제 통보와 같은 User-Agent를 싣는다", () => {
  const source = readFileSync(new URL("../../scripts/send-test-webhook.ts", import.meta.url), "utf8");
  assert.match(source, /"user-agent": "MeterEngine-Webhooks\/1\.0"/);
});
