import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const PAGES = ["app/(console)/customers/page.tsx", "app/(console)/settings/page.tsx", "app/(console)/payments/[id]/page.tsx"];
const read = path => readFileSync(new URL(`../src/${path}`, import.meta.url), "utf8");

test("a failed lookup passes its HTTP status so 403 and 404 explain themselves without a retry", () => {
  for (const path of PAGES) {
    const missing = [...read(path).matchAll(/<WorkspaceError message=\{(\w+)\.error\.message\}([^/]*)\/>/g)].filter(([, name, rest]) => !rest.includes(`status={${name}.error.status}`));
    assert.deepEqual(missing.map(([tag]) => tag), [], path);
  }
});

test("every workspace table on these pages names its scroll area", () => {
  for (const path of PAGES) {
    const unnamed = read(path).split("<WorkspaceTable").slice(1).filter(tag => !/^[^>]*?\blabel=/s.test(tag.split("/>")[0]));
    assert.equal(unnamed.length, 0, path);
  }
});

test("a row restore action on the meter list is a small link button", () => {
  const source = read("components/billable-metrics/BillableMetricsTable.tsx");
  assert.match(source, /<ActionPanel title="미터 복원" triggerType="link"/);
});
