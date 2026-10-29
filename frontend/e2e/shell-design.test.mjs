import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import test from "node:test";

const SRC = new URL("../src/", import.meta.url).pathname;
const source = path => readFileSync(join(SRC, path), "utf8");
const sourceFiles = (dir = SRC) => readdirSync(dir, { withFileTypes: true }).flatMap(entry => entry.isDirectory() ? sourceFiles(join(dir, entry.name)) : /\.(tsx?|css)$/.test(entry.name) ? [join(dir, entry.name)] : []);

test("workspace headers no longer carry a kicker that is always hidden", () => {
  assert.ok(!source("components/workspace/WorkspaceHeader.tsx").includes("kicker"));
  assert.ok(!source("app/globals.css").includes(".workspace-kicker"));
  const callers = sourceFiles().filter(file => readFileSync(file, "utf8").includes("kicker="));
  assert.deepEqual(callers, []);
});
