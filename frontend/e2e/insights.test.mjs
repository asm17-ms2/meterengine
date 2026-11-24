import assert from "node:assert/strict";
import test from "node:test";
import { barRatio, edgeSide, monthlyRevenueSummary, revenueTotal } from "../src/lib/insights.ts";

test("a bar's length is its share of the largest value and an empty chart has no length", () => {
  assert.equal(barRatio(50, 200), 0.25);
  assert.equal(barRatio(0, 200), 0);
  assert.equal(barRatio(10, 0), 0);
  assert.equal(barRatio(-5, 200), 0);
});

test("a column hangs its label and tooltip toward the middle of the chart so they stay inside it", () => {
  assert.equal(edgeSide(0, 10), "start");
  assert.equal(edgeSide(4, 10), "start");
  assert.equal(edgeSide(5, 10), "end");
  assert.equal(edgeSide(9, 10), "end");
  assert.equal(edgeSide(1, 3), "start");
  assert.equal(edgeSide(2, 3), "end");
  assert.equal(edgeSide(0, 1), "start");
});

const won = value => `${value.toLocaleString("ko-KR")}원`;

test("a month's revenue is what was collected, what is still owed and what is not invoiced yet", () => {
  assert.equal(revenueTotal({ month: "2026-09", collected: 1_580_000, outstanding: 240_000, draft: 0 }), 1_820_000);
});

test("the revenue summary compares the month in progress with the month before and names the months still owed", () => {
  const months = [
    { month: "2026-09", collected: 1_580_000, outstanding: 240_000, draft: 0 },
    { month: "2026-10", collected: 2_140_000, outstanding: 0, draft: 0 },
    { month: "2026-11", collected: 0, outstanding: 0, draft: 1_390_000 },
  ];
  assert.equal(monthlyRevenueSummary(months, "2026-11", won), "11월은 지금까지 1,390,000원이고 10월의 65%예요. 받을 금액은 9월 240,000원이에요.");
});

test("the revenue summary of a finished month does not say so far", () => {
  const months = [{ month: "2026-08", collected: 1_000_000, outstanding: 0, draft: 0 }, { month: "2026-09", collected: 1_500_000, outstanding: 0, draft: 0 }];
  assert.equal(monthlyRevenueSummary(months, "", won), "9월은 1,500,000원이고 8월의 150%예요. 받을 금액은 없어요.");
});

test("the revenue summary leaves out the comparison when the month before had nothing", () => {
  const months = [{ month: "2026-10", collected: 0, outstanding: 0, draft: 0 }, { month: "2026-11", collected: 0, outstanding: 0, draft: 500 }];
  assert.equal(monthlyRevenueSummary(months, "2026-11", won), "11월은 지금까지 500원이에요. 받을 금액은 없어요.");
});

test("the revenue summary lists every month that is still owed", () => {
  const months = [{ month: "2026-09", collected: 0, outstanding: 100, draft: 0 }, { month: "2026-10", collected: 0, outstanding: 200, draft: 0 }, { month: "2026-11", collected: 0, outstanding: 0, draft: 0 }];
  assert.equal(monthlyRevenueSummary(months, "2026-11", won), "11월은 지금까지 0원이고 10월의 0%예요. 받을 금액은 9월 100원, 10월 200원이에요.");
});
