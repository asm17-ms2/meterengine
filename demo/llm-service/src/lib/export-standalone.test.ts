import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { copyFileSync, existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

const SCRIPTS_DIRECTORY = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..", "scripts");

test("실행 묶음은 앱 폴더의 .env와 로컬 DB를 복사하지 않는다", t => {
  const root = mkdtempSync(path.join(os.tmpdir(), "lumen-export-"));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const app = path.join(root, "app");
  const standalone = path.join(app, ".next", "standalone", "llm-service");
  mkdirSync(path.join(app, "scripts"), { recursive: true });
  for (const name of ["export-standalone.mjs", "start.mjs"]) copyFileSync(path.join(SCRIPTS_DIRECTORY, name), path.join(app, "scripts", name));
  mkdirSync(path.join(standalone, "data"), { recursive: true });
  mkdirSync(path.join(app, ".next", "static"), { recursive: true });
  writeFileSync(path.join(standalone, "server.js"), "");
  writeFileSync(path.join(standalone, ".env"), "METERENGINE_API_KEY=local-unit-test-key\n");
  writeFileSync(path.join(standalone, ".env.local"), "METERENGINE_API_KEY=local-unit-test-key\n");
  writeFileSync(path.join(standalone, "data", "lumen.sqlite"), "");
  const output = path.join(root, "bundle");
  const result = spawnSync(process.execPath, [path.join(app, "scripts", "export-standalone.mjs"), "--output", output], { encoding: "utf8" });
  assert.equal(result.status, 0, result.stderr);
  assert.ok(existsSync(path.join(output, "llm-service", "server.js")));
  assert.ok(!existsSync(path.join(output, "llm-service", ".env")));
  assert.ok(!existsSync(path.join(output, "llm-service", ".env.local")));
  assert.ok(!existsSync(path.join(output, "llm-service", "data")));
});
