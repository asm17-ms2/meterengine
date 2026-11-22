import assert from "node:assert/strict";
import test from "node:test";
import { barRatio, edgeSide, metricAmounts, topCustomerAmounts } from "../src/lib/insights.ts";

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

test("a column hangs its label and tooltip toward the middle of the chart so they stay inside it", () => {
  assert.equal(edgeSide(0, 10), "start");
  assert.equal(edgeSide(4, 10), "start");
  assert.equal(edgeSide(5, 10), "end");
  assert.equal(edgeSide(9, 10), "end");
  assert.equal(edgeSide(1, 3), "start");
  assert.equal(edgeSide(2, 3), "end");
  assert.equal(edgeSide(0, 1), "start");
});
