import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdir, mkdtemp, readdir, realpath, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { test } from "node:test";

const SCRIPT = path.join(import.meta.dirname, "seed-demo.ts");
// 검사가 뚫려도 실제 서버에 닿지 않게 아무도 듣지 않는 로컬 포트를 대상으로 둔다.
const UNREACHABLE = "http://127.0.0.1:9";
const SEED_ENV = ["METERENGINE_API_BASE_URL", "METERENGINE_ENVIRONMENT", "METERENGINE_OPERATOR_EMAIL", "METERENGINE_OPERATOR_PASSWORD", "OPENAB_OWNER_EMAIL", "OPENAB_OWNER_PASSWORD", "OPENAB_ORGANIZATION_NAME", "OPENAB_APP_ORIGIN", "METERENGINE_CONSOLE_URL"];

function run(args: string[], env: Record<string, string> = {}) {
  const clean = Object.fromEntries(Object.entries(process.env).filter(([name]) => !SEED_ENV.includes(name)));
  const result = spawnSync(process.execPath, [SCRIPT, ...args], { env: { ...clean, ...env } as NodeJS.ProcessEnv, encoding: "utf8", timeout: 60_000 });
  return { status: result.status, output: result.stdout + result.stderr };
}

async function temporary(run: (directory: string) => Promise<void>) {
  const directory = await realpath(await mkdtemp(path.join(os.tmpdir(), "openab-seed-")));
  try { await run(directory); } finally { await rm(directory, { recursive: true, force: true }); }
}

test("실제 환경(live)은 환경 변수로도 인자로도 고를 수 없고, 로컬 주소에서도 파일을 쓰기 전에 멈춘다", () => temporary(async directory => {
  const output = path.join(directory, "private", "openab-config.json");
  const byEnv = run(["--dry-run"], { METERENGINE_ENVIRONMENT: "live" });
  assert.notEqual(byEnv.status, 0);
  assert.match(byEnv.output, /테스트 환경에만/);
  const byArgument = run(["--dry-run", "--environment", "live"]);
  assert.notEqual(byArgument.status, 0);
  assert.match(byArgument.output, /--environment/);
  const local = run(["--base-url", UNREACHABLE, "--output", output], { METERENGINE_ENVIRONMENT: "live" });
  assert.notEqual(local.status, 0);
  assert.match(local.output, /테스트 환경에만/);
  assert.deepEqual(await readdir(directory), []);
}));

test("--dry-run은 늘 테스트 환경을 대상으로 적는다", () => {
  const result = run(["--dry-run", "--until", "2026-11-03T00:00:00Z"]);
  assert.equal(result.status, 0, result.output);
  assert.deepEqual(JSON.parse(result.output).target, { host: "127.0.0.1", environment: "test", local: true });
});

test("--until이 지난달이면 파일을 쓰기 전에 멈춘다", () => temporary(async directory => {
  const lastMonth = new Date(Date.now() - 40 * 24 * 3_600_000).toISOString();
  const result = run(["--base-url", UNREACHABLE, "--output", path.join(directory, "private", "openab-config.json"), "--until", lastMonth]);
  assert.notEqual(result.status, 0);
  assert.match(result.output, /이번 달/);
  assert.deepEqual(await readdir(directory), []);
}));

test("출력 경로가 git 작업 트리 안이면 파일을 쓰기 전에 멈춘다", () => temporary(async directory => {
  await mkdir(path.join(directory, ".git"));
  const result = run(["--base-url", UNREACHABLE, "--output", path.join(directory, "demo", "openab-config.json")]);
  assert.notEqual(result.status, 0);
  assert.match(result.output, /git 작업 트리/);
  assert.deepEqual(await readdir(directory), [".git"]);
}));

test("대표 계정 환경 변수와 --organization-id는 --existing-owner 없이는 받지 않고, 로컬이 아닌 대상은 앱과 콘솔 주소를 받아야 한다", () => temporary(async directory => {
  const output = path.join(directory, "private", "openab-config.json");
  const ownerWithoutFlag = run(["--base-url", UNREACHABLE, "--output", output], { OPENAB_OWNER_EMAIL: "someone@openab.example", OPENAB_OWNER_PASSWORD: "not-a-real-password" });
  assert.notEqual(ownerWithoutFlag.status, 0);
  assert.match(ownerWithoutFlag.output, /--existing-owner와 같이/);
  const flagWithoutOwner = run(["--base-url", UNREACHABLE, "--output", output, "--existing-owner"]);
  assert.notEqual(flagWithoutOwner.status, 0);
  assert.match(flagWithoutOwner.output, /OPENAB_OWNER_EMAIL/);
  const idWithoutFlag = run(["--base-url", UNREACHABLE, "--output", output, "--organization-id", "0b6f3c2e-6a51-4c1f-9d0e-5b7a2f8c4d13"]);
  assert.notEqual(idWithoutFlag.status, 0);
  assert.match(idWithoutFlag.output, /--existing-owner와 같이/);
  const malformedId = run(["--base-url", UNREACHABLE, "--output", output, "--existing-owner", "--organization-id", "openab"], { OPENAB_OWNER_EMAIL: "someone@openab.example", OPENAB_OWNER_PASSWORD: "not-a-real-password" });
  assert.notEqual(malformedId.status, 0);
  assert.match(malformedId.output, /UUID 형식/);
  const remote = run(["--base-url", "https://seed-guard.invalid", "--confirm-host", "seed-guard.invalid", "--output", output]);
  assert.notEqual(remote.status, 0);
  assert.match(remote.output, /OPENAB_APP_ORIGIN/);
  assert.deepEqual(await readdir(directory), []);
}));
