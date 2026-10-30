import assert from "node:assert/strict";
import test from "node:test";
import { appliedPricePlan } from "../src/lib/price-plan.ts";

const plan = (id, effective_from, effective_until = null, archived_from = null) => ({ id, effective_from, effective_until, archived_from });

test("an archived latest plan masks older plans from its archived month", () => {
  const plans = [plan("V2", "2026-03", null, "2026-11"), plan("V1", "2026-01")];
  assert.equal(appliedPricePlan(plans, "2026-02")?.id, "V1");
  assert.equal(appliedPricePlan(plans, "2026-10")?.id, "V2");
  assert.equal(appliedPricePlan(plans, "2026-11"), undefined);
  assert.equal(appliedPricePlan([...plans].reverse(), "2026-11"), undefined);
});

test("a plan archived before it starts never applies and does not mask older plans", () => {
  const plans = [plan("V2", "2026-12", null, "2026-11"), plan("V1", "2026-01")];
  assert.equal(appliedPricePlan(plans, "2026-12")?.id, "V1");
});

test("an ended plan leaves no plan for later months", () => {
  assert.equal(appliedPricePlan([plan("V1", "2026-01", "2026-05")], "2026-04")?.id, "V1");
  assert.equal(appliedPricePlan([plan("V1", "2026-01", "2026-05")], "2026-05"), undefined);
});
