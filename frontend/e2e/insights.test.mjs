import assert from "node:assert/strict";
import test from "node:test";
import { barRatio, edgeSide, formatShare, metricAmounts, monthlyRevenueSummary, revenueTotal, shareOfTotal, topCustomerAmounts, topCustomerSummary } from "../src/lib/insights.ts";

test("top customers are the largest draft amounts first and leave out customers with nothing to bill", () => {
  const customers = [
    { customer_name: "가", amount: 100 }, { customer_name: "나", amount: 0 }, { customer_name: "다", amount: 300 },
    { customer_name: "라", amount: 200 },
  ];
  assert.deepEqual(topCustomerAmounts(customers, 2), [{ label: "다", amount: 300 }, { label: "라", amount: 200 }]);
});

test("customers with the same amount keep their name order", () => {
  const customers = [{ customer_name: "나", amount: 100 }, { customer_name: "가", amount: 100 }];
  assert.deepEqual(topCustomerAmounts(customers, 5).map(row => row.label), ["가", "나"]);
});

test("metric amounts add up every customer's lines by meter code", () => {
  const customers = [
    { lines: [{ billable_metric_code: "tokens", amount: 100 }, { billable_metric_code: "calls", amount: 50 }] },
    { lines: [{ billable_metric_code: "tokens", amount: 30 }] },
  ];
  assert.deepEqual(metricAmounts(customers, 5), [{ label: "tokens", amount: 130 }, { label: "calls", amount: 50 }]);
});

test("lines without a meter are shown as base and minimum fees", () => {
  const customers = [{ lines: [{ billable_metric_code: null, amount: 1000 }, { billable_metric_code: "tokens", amount: 10 }] }];
  assert.deepEqual(metricAmounts(customers, 5), [{ label: "기본료와 최소 사용료", amount: 1000 }, { label: "tokens", amount: 10 }]);
});

test("meters past the limit fold into one last row instead of disappearing", () => {
  const customers = [{ lines: ["a", "b", "c", "d"].map((code, index) => ({ billable_metric_code: code, amount: 40 - index * 10 })) }];
  assert.deepEqual(metricAmounts(customers, 2), [{ label: "a", amount: 40 }, { label: "b", amount: 30 }, { label: "그 밖의 과금 미터 2개", amount: 30 }]);
});

test("a bar's length is its share of the largest value and an empty chart has no length", () => {
  assert.equal(barRatio(50, 200), 0.25);
  assert.equal(barRatio(0, 200), 0);
  assert.equal(barRatio(10, 0), 0);
  assert.equal(barRatio(-5, 200), 0);
});

test("a share is the amount divided by the total and nothing when there is no total", () => {
  assert.equal(shareOfTotal(1_240_000, 3_000_000), 1_240_000 / 3_000_000);
  assert.equal(shareOfTotal(100, 0), 0);
  assert.equal(shareOfTotal(0, 100), 0);
  assert.equal(shareOfTotal(-5, 100), 0);
});

test("a share reads as a whole percent", () => {
  assert.equal(formatShare(0.413), "41%");
  assert.equal(formatShare(0.207), "21%");
  assert.equal(formatShare(1), "100%");
});

test("a share too small to round up still shows that the customer has some", () => {
  assert.equal(formatShare(0.004), "1% 미만");
});

test("a share that could not be worked out shows nothing", () => {
  assert.equal(formatShare(0), "");
});

test("the customer summary says how much of the total the listed customers hold", () => {
  const customers = [600, 200, 100, 50, 30, 20].map((amount, index) => ({ customer_name: `고객${index}`, amount }));
  assert.equal(topCustomerSummary(customers, 5, 1000), "청구 예정액이 큰 5곳이 전체의 98%예요. 공급가액 기준이에요.");
});

test("the customer summary only counts customers when all of them are listed", () => {
  const customers = [{ customer_name: "가", amount: 100 }, { customer_name: "나", amount: 0 }, { customer_name: "다", amount: 300 }];
  assert.equal(topCustomerSummary(customers, 5, 400), "청구 예정액이 있는 고객은 2곳이에요. 공급가액 기준이에요.");
});

test("the customer summary reads naturally when the listed customers hold under one percent", () => {
  const customers = Array.from({ length: 6 }, (_, index) => ({ customer_name: `고객${index}`, amount: 1 }));
  assert.equal(topCustomerSummary(customers, 5, 10_000), "청구 예정액이 큰 5곳이 전체의 1% 미만이에요. 공급가액 기준이에요.");
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
