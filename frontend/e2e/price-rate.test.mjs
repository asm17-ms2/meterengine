import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { isRepresentableUnitPrice, isZeroPriceRate, UNIT_PRICE_PRECISION_MESSAGE } from "../src/lib/price-rate.ts";

const editor = readFileSync(new URL("../src/components/pricing/PricingWorkspace.tsx", import.meta.url), "utf8");

test("a price that charges nothing is recognized so the editor can ask before saving it", () => {
  assert.equal(isZeroPriceRate("per_unit", "0", []), true);
  assert.equal(isZeroPriceRate("per_unit", "0.0", []), true);
  assert.equal(isZeroPriceRate("per_unit", "120", []), false);
  assert.equal(isZeroPriceRate("graduated", "", ["0", "0"]), true);
  assert.equal(isZeroPriceRate("volume", "", ["0", "15"]), false);
});

test("the price editor does not prefill unit prices with zero", () => {
  assert.doesNotMatch(editor, /name="unit_price"[^>]*defaultValue=\{?\s*"0"/);
  assert.doesNotMatch(editor, /unitPrice:\s*"0"/);
});

test("unit prices the screen can show exactly are accepted and finer ones are sent to the API instead", () => {
  for (const text of ["120", "0.0000000001", "12.5", "9007199254740991"]) assert.equal(isRepresentableUnitPrice(text), true, text);
  for (const text of ["0.00000000001", "9007199254740993", "1e-11"]) assert.equal(isRepresentableUnitPrice(text), false, text);
});

test("unit prices typed in other number input forms are judged by their value", () => {
  for (const text of [".5", "1e3", "1.5e-5", "1.50"]) assert.equal(isRepresentableUnitPrice(text), true, text);
});

test("a unit price whose decimals the screen would round away is sent to the API", () => {
  for (const text of ["12345678.0000000001", "1234567.1234567891"]) assert.equal(isRepresentableUnitPrice(text), false, text);
});

test("the unit price precision message covers both too many decimals and too many digits", () => {
  assert.equal(UNIT_PRICE_PRECISION_MESSAGE, "화면에서 정확히 보일 수 없는 단가입니다. 소수 10자리를 넘거나 자릿수가 많은 단가는 API로 등록하세요.");
});
