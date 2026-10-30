import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { usageQualityReasons } from "../src/lib/usage-quality.ts";

const counts = { month: "2026-10", unmatched_type: { event_count: 2 }, non_numeric_value: { event_count: 1 }, future_timestamp: { event_count: 0 }, negative_quantity: { event_count: 3 } };

test("ingestion exceptions link to the diagnosis filtered by their reason and month", () => {
  const [unmatched, invalid] = usageQualityReasons(counts);
  assert.equal(unmatched.href, "/event-issues?reason=unknown_event_type&month=2026-10");
  assert.equal(invalid.href, "/event-issues?reason=invalid_target_property&month=2026-10");
  assert.equal(unmatched.action, "대상 이벤트 보기");
  assert.deepEqual(usageQualityReasons(counts).map(reason => reason.count), [2, 1, 0, 3]);
});

test("the usage screen shows ingestion exceptions above the long usage table", () => {
  const page = readFileSync(new URL("../src/app/(console)/usage/page.tsx", import.meta.url), "utf8");
  assert.ok(page.indexOf("<UsageQuality") < page.indexOf("<ScreenCard>"));
});
