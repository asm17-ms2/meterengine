import assert from "node:assert/strict";
import test from "node:test";
import { planAmount } from "../src/lib/plan-amount.ts";

test("plan amounts show the billed amount when billing drops the part under 10 won", () => {
  assert.equal(planAmount(9995), "9,995원 (청구 9,990원)");
  assert.equal(planAmount(5), "5원 (청구 0원)");
  assert.equal(planAmount(9990), "9,990원");
  assert.equal(planAmount(0), "0원");
});
