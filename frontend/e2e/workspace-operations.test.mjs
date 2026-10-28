import assert from "node:assert/strict";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import test from "node:test";
import { WORKSPACE_OPERATIONS, isWorkspaceOperation } from "../src/lib/auth/permissions.ts";

const SRC = new URL("../src/", import.meta.url).pathname;
const files = dir => readdirSync(dir).flatMap(name => {
  const path = join(dir, name);
  return statSync(path).isDirectory() ? files(path) : /\.tsx?$/.test(name) ? [path] : [];
});
const cases = new Set([...readFileSync(join(SRC, "app/(console)/workspace-actions.ts"), "utf8").matchAll(/case "([A-Za-z]+)"/g)].map(match => match[1]));

test("every workspace panel operation is a known permission and has a workspace action case", () => {
  const used = new Set(files(SRC).flatMap(path => [...readFileSync(path, "utf8").matchAll(/operation(?:=|: )"([A-Za-z]+)"/g)].map(match => match[1])));
  assert.ok(used.size > 0);
  for (const operation of used) {
    assert.ok(isWorkspaceOperation(operation), `${operation} is not in WORKSPACE_OPERATIONS`);
    assert.ok(cases.has(operation), `${operation} has no case in workspace-actions.ts`);
  }
});

test("every workspace action case is a known permission", () => {
  for (const operation of cases) assert.ok(WORKSPACE_OPERATIONS.includes(operation), `${operation} is not in WORKSPACE_OPERATIONS`);
});
