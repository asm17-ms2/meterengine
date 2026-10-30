import assert from "node:assert/strict";
import test from "node:test";
import { publicInvoiceLineName, publicInvoiceLineTier } from "../src/lib/invoice-view-links.ts";

test("public invoice lines use the console and mail names for fees and tiers", () => {
  assert.equal(publicInvoiceLineName({ line_type: "minimum_adjustment", billable_metric_code: null }), "최소 사용료 조정");
  assert.equal(publicInvoiceLineName({ line_type: "base_fee", billable_metric_code: null }), "기본료");
  assert.equal(publicInvoiceLineName({ line_type: "usage", billable_metric_code: "api-calls" }), "api-calls");
  assert.equal(publicInvoiceLineName({ billable_metric_code: null }), "사용량");
  assert.equal(publicInvoiceLineName({ line_type: "graduated_tier", billable_metric_code: "api-calls", billable_metric_name: "API 호출" }), "API 호출");
  assert.equal(publicInvoiceLineTier({ line_type: "graduated_tier", tier_index: 1 }), "구간 2");
  assert.equal(publicInvoiceLineTier({ line_type: "volume", tier_index: 0 }), "총량 단가");
  assert.equal(publicInvoiceLineTier({ line_type: "usage" }), null);
});
