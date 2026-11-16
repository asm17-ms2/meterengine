import assert from "node:assert/strict";
import { chmod, mkdir, mkdtemp, readdir, readFile, stat, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { API_KEY_ENV, credentialsPath, resolveApiKey, saveApiKey } from "./credentials.ts";
import { UsageError } from "./exit-code.ts";

const TEST_KEY = "me_test_wiCp766QYNqhyYm4Z7Xq9wYosNvQCeVC";
const LIVE_KEY = "me_live_QouOFuCiJyAbC0VGK1ltUCkG4IZM2LtA";

async function emptyConfigDir(): Promise<string> {
  return join(await mkdtemp(join(tmpdir(), "meterengine-cli-")), "meterengine");
}

test("저장한 키 파일은 소유자만 읽고 쓸 수 있다", async () => {
  const configDir = await emptyConfigDir();
  const path = await saveApiKey(configDir, TEST_KEY);

  assert.equal((await stat(path)).mode & 0o777, 0o600);
  assert.equal((await stat(configDir)).mode & 0o777, 0o700);
  assert.deepEqual(JSON.parse(await readFile(path, "utf8")), { profiles: { default: { api_key: TEST_KEY } } });
});

test("프로필마다 키를 따로 저장하고 다른 프로필은 지우지 않는다", async () => {
  const configDir = await emptyConfigDir();
  await saveApiKey(configDir, TEST_KEY, "test");
  await saveApiKey(configDir, LIVE_KEY, "live");

  assert.equal(await resolveApiKey({}, configDir, "test"), TEST_KEY);
  assert.equal(await resolveApiKey({}, configDir, "live"), LIVE_KEY);
});

test("프로필 없는 {api_key} 형식의 키 파일은 default 프로필로 읽는다", async () => {
  const configDir = await emptyConfigDir();
  await saveApiKey(configDir, TEST_KEY);
  await writeFile(credentialsPath(configDir), JSON.stringify({ api_key: TEST_KEY }));

  assert.equal(await resolveApiKey({}, configDir), TEST_KEY);
  await saveApiKey(configDir, LIVE_KEY, "live");
  assert.equal(await resolveApiKey({}, configDir), TEST_KEY);
});

test("--profile을 주면 환경변수보다 프로필을 먼저 쓰고, 없는 프로필은 사용법 오류다", async () => {
  const configDir = await emptyConfigDir();
  await saveApiKey(configDir, TEST_KEY, "test");

  assert.equal(await resolveApiKey({ [API_KEY_ENV]: LIVE_KEY }, configDir, "test"), TEST_KEY);
  await assert.rejects(resolveApiKey({ [API_KEY_ENV]: LIVE_KEY }, configDir, "live"), (error: unknown) => error instanceof UsageError && error.code === "missing_profile");
  await assert.rejects(saveApiKey(configDir, TEST_KEY, "../x"), (error: unknown) => error instanceof UsageError && error.code === "invalid_profile_name");
});

test("이미 있던 키 파일의 권한이 넓어도 다시 저장하면 600이 된다", async () => {
  const configDir = await emptyConfigDir();
  await saveApiKey(configDir, TEST_KEY);
  await writeFile(credentialsPath(configDir), "{}", { mode: 0o644 });

  await saveApiKey(configDir, LIVE_KEY);

  assert.equal((await stat(credentialsPath(configDir))).mode & 0o777, 0o600);
});

test("이미 있던 설정 디렉터리의 권한이 넓어도 저장하면 700이 되고 임시 파일은 남지 않는다", async () => {
  const configDir = await emptyConfigDir();
  await mkdir(configDir, { mode: 0o755 });
  await chmod(configDir, 0o755);
  await writeFile(credentialsPath(configDir), JSON.stringify({ api_key: TEST_KEY }), { mode: 0o644 });

  await saveApiKey(configDir, LIVE_KEY, "live");

  assert.equal((await stat(configDir)).mode & 0o777, 0o700);
  assert.equal((await stat(credentialsPath(configDir))).mode & 0o777, 0o600);
  assert.deepEqual(await readdir(configDir), ["credentials.json"]);
  assert.equal(await resolveApiKey({}, configDir), TEST_KEY);
});

test("형식이 틀린 키는 저장하지 않는다", async () => {
  const configDir = await emptyConfigDir();

  await assert.rejects(saveApiKey(configDir, "me_test_xxxxxxxx"), (error: unknown) => error instanceof UsageError && error.code === "invalid_api_key_format");
  await assert.rejects(stat(credentialsPath(configDir)));
});

test("환경변수의 키를 저장한 키보다 먼저 쓴다", async () => {
  const configDir = await emptyConfigDir();
  await saveApiKey(configDir, TEST_KEY);

  assert.equal(await resolveApiKey({ [API_KEY_ENV]: LIVE_KEY }, configDir), LIVE_KEY);
  assert.equal(await resolveApiKey({}, configDir), TEST_KEY);
});

test("환경변수도 저장한 키도 없으면 사용법 오류다", async () => {
  await assert.rejects(resolveApiKey({}, await emptyConfigDir()), (error: unknown) => error instanceof UsageError && error.code === "missing_api_key");
});
