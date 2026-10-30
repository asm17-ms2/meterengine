import assert from "node:assert/strict";
import test from "node:test";
import { usageEmptyKind } from "../src/lib/usage-empty-kind.ts";

test("usage empty states lead to registration instead of a filter reset when nothing exists yet", () => {
  assert.equal(usageEmptyKind({ customerCount: 0, totalUnfiltered: 0, metricLineCount: 0, hasFilter: true }), "no_customers");
  assert.equal(usageEmptyKind({ customerCount: 0, totalUnfiltered: 5, metricLineCount: 0, hasFilter: false }), "no_customers");
  assert.equal(usageEmptyKind({ customerCount: 0, totalUnfiltered: 5, metricLineCount: 0, hasFilter: true }), "no_matching_customers");
  assert.equal(usageEmptyKind({ customerCount: 3, totalUnfiltered: 3, metricLineCount: 0, metricCode: "tokens", hasFilter: true }), "no_metric_lines");
  assert.equal(usageEmptyKind({ customerCount: 3, totalUnfiltered: 3, metricLineCount: 0, hasFilter: false }), "no_metrics");
  assert.equal(usageEmptyKind({ customerCount: 3, totalUnfiltered: 3, metricLineCount: 4, hasFilter: false }), "has_data");
});
