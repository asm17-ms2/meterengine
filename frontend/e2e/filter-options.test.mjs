import assert from "node:assert/strict";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import test from "node:test";

const APP = new URL("../src/app/", import.meta.url).pathname;
const files = dir => readdirSync(dir).flatMap(name => {
  const path = join(dir, name);
  return statSync(path).isDirectory() ? files(path) : name.endsWith(".tsx") ? [path] : [];
});

test("list filter options ask for archived customers and metrics too", () => {
  const pages = files(APP).filter(path => readFileSync(path, "utf8").includes("<ListControlsLoader"));
  assert.ok(pages.length > 0);
  for (const path of pages) {
    const calls = [...readFileSync(path, "utf8").matchAll(/(listCustomers|listBillableMetrics)\(readListQuery\(([^,]*),/g)];
    assert.ok(calls.length > 0, `${path} loads no filter options`);
    for (const [, name, params] of calls) assert.equal(params.replace(/\s+/g, " "), '{ status: "all" }', `${name} in ${path}`);
  }
});
