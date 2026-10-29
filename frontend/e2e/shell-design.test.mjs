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

test("globals.css uses its own color tokens instead of repeating the same literal values", () => {
  const rules = source("app/globals.css").split("\n").filter(line => !line.startsWith(":root { color-scheme: light;")).join("\n");
  for (const literal of ["#202b40", "#5a6578", "#dce1eb", "#5746ad", "#f3f5f9", "#7c8799"]) assert.doesNotMatch(rules, new RegExp(`${literal}(?![0-9a-f])`, "i"));
  assert.doesNotMatch(rules, /background(?:-color)?:\s*#fff\b/i);
});
