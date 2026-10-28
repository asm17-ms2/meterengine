import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { isZeroPriceRate } from "../src/lib/price-rate.ts";

const editor = readFileSync(new URL("../src/components/pricing/PricingWorkspace.tsx", import.meta.url), "utf8");

test("a price that charges nothing is recognized so the editor can ask before saving it", () => {
  assert.equal(isZeroPriceRate("per_unit", "0", []), true);
  assert.equal(isZeroPriceRate("per_unit", "0.0", []), true);
  assert.equal(isZeroPriceRate("per_unit", "120", []), false);
  assert.equal(isZeroPriceRate("graduated", "", ["0", "0"]), true);
  assert.equal(isZeroPriceRate("volume", "", ["0", "15"]), false);
});

test("the price editor does not prefill unit prices with zero", () => {
  assert.doesNotMatch(editor, /name="unit_price"[^>]*defaultValue="0"/);
  assert.doesNotMatch(editor, /unitPrice:"0"/);
});
