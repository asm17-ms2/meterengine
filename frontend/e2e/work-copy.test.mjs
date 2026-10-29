import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = path => readFileSync(new URL(`../src/${path}`, import.meta.url), "utf8");

test("pricing history empty states use the readable Empty component instead of the faint default text", () => {
  const pricing = source("components/pricing/PricingWorkspace.tsx");
  assert.equal(pricing.match(/emptyText: ?<Empty image=\{Empty\.PRESENTED_IMAGE_SIMPLE\}/g)?.length, 2);
});

test("pricing history money columns are right aligned numbers", () => {
  const pricing = source("components/pricing/PricingWorkspace.tsx");
  for (const column of ['{title:"기본료",dataIndex:"base_fee",align:"right"', '{title:"최소 사용료",dataIndex:"minimum_amount",align:"right"', '{title:"단가",key:"price",align:"right"']) assert.ok(pricing.includes(column), column);
});
