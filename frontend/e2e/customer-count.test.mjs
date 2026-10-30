import assert from "node:assert/strict";
import test from "node:test";
import { customerCountText } from "../src/lib/customer-count.ts";

test("the customer count names its scope and only mentions a search when there is one", () => {
  assert.equal(customerCountText({ status: "active", total: 8, totalUnfiltered: 9 }), "사용 중 고객 8곳, 보관 포함 전체 9곳");
  assert.equal(customerCountText({ status: "archived", q: "acme", total: 1, totalUnfiltered: 9 }), "검색어에 맞는 보관된 고객 1곳, 보관 포함 전체 9곳");
  assert.equal(customerCountText({ status: "all", total: 9, totalUnfiltered: 9 }), "전체 고객 9곳");
  assert.equal(customerCountText({ status: "all", q: "acme", total: 2, totalUnfiltered: 9 }), "검색어에 맞는 전체 고객 2곳, 보관 포함 전체 9곳");
});
