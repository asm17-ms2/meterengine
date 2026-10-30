import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { reasonHints } from "../src/lib/event-issue-hints.ts";

test("each issue reason's hint is shown once in order of first appearance", () => {
  const issues = [
    { reason: "unpriced_usage", action_hint: "단가를 등록하세요" },
    { reason: "unknown_event_type", action_hint: "미터를 등록하세요" },
    { reason: "unpriced_usage", action_hint: "단가를 등록하세요" },
  ];
  assert.deepEqual(reasonHints(issues), [{ reason: "unpriced_usage", hint: "단가를 등록하세요" }, { reason: "unknown_event_type", hint: "미터를 등록하세요" }]);
});

test("the diagnosis table drops the repeated hint column and links to where each problem is fixed", () => {
  const page = readFileSync(new URL("../src/app/(console)/event-issues/page.tsx", import.meta.url), "utf8");
  assert.ok(!page.includes('{ key: "action_hint", label: "확인할 내용" }'));
  for (const link of ['href="/billable-metrics"', "href={`/pricing?month=${month}", "href={`/events?month=${month}`}", "href={`/billing?month=${month}`}"]) assert.ok(page.includes(link), link);
});
