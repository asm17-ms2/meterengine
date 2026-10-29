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
  const optionsApi = readFileSync(new URL("../src/lib/api/search.ts", import.meta.url), "utf8");
  assert.match(optionsApi, /status:\s*"all"/);
  const pages = files(APP).filter(path => readFileSync(path, "utf8").includes("<ListControlsLoader"));
  assert.ok(pages.length > 0);
  for (const path of pages) {
    const source = readFileSync(path, "utf8");
    const calls = [...source.matchAll(/(listCustomers|listBillableMetrics)\(readListQuery\(([^,]*),/g)];
    if (calls.length === 0) assert.match(source, /<ListControlsLoader[\s\S]*?resource="/, `${path} loads no filter options`);
    for (const [, name, params] of calls) assert.equal(params.replace(/\s+/g, " "), '{ status: "all" }', `${name} in ${path}`);
  }
});
