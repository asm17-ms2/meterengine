import assert from "node:assert/strict";
import test from "node:test";
import { formatKstShortStamp } from "../src/lib/format.ts";

test("the short stamp reads as a Korean date and a 24-hour time in KST", () => {
  assert.equal(formatKstShortStamp(new Date("2026-11-17T06:30:45Z")), "11월 17일 15:30");
  assert.equal(formatKstShortStamp(new Date("2026-11-30T15:00:00Z")), "12월 1일 00:00");
});
