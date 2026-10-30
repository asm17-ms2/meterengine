import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = path => readFileSync(new URL(`../src/${path}`, import.meta.url), "utf8");

test("the payment list passes the failed lookup status and names its table", () => {
  const source = read("app/(console)/payments/page.tsx");
  assert.match(source, /<WorkspaceError message=\{[^}]*\} status=\{/);
  assert.match(source, /<WorkspaceTable label="수납 목록"/);
});

test("price plan and rate history row actions are small link buttons", () => {
  const panels = [...read("components/pricing/PricingWorkspace.tsx").matchAll(/<ActionPanel title=\{\w+\.archived_at \? "(?:요금제|단가) 버전 복원"[^>]*/g)].map(([tag]) => tag);
  assert.equal(panels.length, 2);
  for (const tag of panels) assert.match(tag, /triggerType="link"/);
});
