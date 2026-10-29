import assert from "node:assert/strict";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { test } from "node:test";
import { signWebhookPayload } from "@meterengine/sdk";
import type { Target, TargetEnv } from "./config.ts";
import { appendScopedWebhookReceipt, listScopedWebhookReceipts } from "./webhook-file-store.ts";
import { handleWebhookRequest } from "./webhook-handler.ts";
import { selectWebhookTarget, webhookTarget } from "./webhook-target.ts";

const SECRET_TEST = "whsec_MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw";
const SECRET_LIVE = "whsec_" + Buffer.from("live-webhook-local-test-secret").toString("base64");
const NOW = new Date("2026-10-27T03:00:00Z");
const TIMESTAMP = Math.floor(NOW.getTime() / 1000);
const TARGETS: Target[] = [
  { name: "test", base_url: "http://localhost:8080", api_key: "key-test", environment: "test", webhook_secret: SECRET_TEST },
  { name: "live", base_url: "http://localhost:8080", api_key: "key-live", environment: "live", webhook_secret: SECRET_LIVE },
  { name: "e2e", base_url: "http://localhost:8080", api_key: "key-legacy" },
  { name: "other-legacy", base_url: "http://localhost:8080", api_key: "key-other" },
];

function configuration(targets = TARGETS): TargetEnv {
  return Object.fromEntries([["METERENGINE_TARGETS", JSON.stringify(targets)], ["METERENGINE_WEBHOOK_SECRET", SECRET_TEST], ["METERENGINE_LEGACY_WEBHOOK_TARGET", "e2e"]]);
}

function signedRequest(query: string, environment: string | undefined, secret: string, id = "same-event-id"): Request {
  const payload = JSON.stringify({ ...(environment ? { environment } : {}), type: "webhook_test", timestamp: NOW.toISOString(), data: { marker: environment ?? "old-legacy" } });
  return new Request(`http://localhost:3100/api/webhooks${query}`, { method: "POST", headers: {
    "webhook-id": id,
    "webhook-timestamp": String(TIMESTAMP),
    "webhook-signature": signWebhookPayload({ id, timestamp: TIMESTAMP, payload, secret }),
  }, body: payload });
}

test("단일 대상에서 환경을 지정하면 전역 비밀값으로 그 환경의 웹훅을 받는다", async () => {
  const received: string[] = [];
  const append = async (scope: { environment: string }) => { received.push(scope.environment); };
  const scoped = Object.fromEntries([["METERENGINE_API_KEY", "key-test"], ["METERENGINE_ENVIRONMENT", "test"], ["METERENGINE_WEBHOOK_SECRET", SECRET_TEST]]);
  assert.equal((await handleWebhookRequest(signedRequest("?target=local", "test", SECRET_TEST), scoped, append, NOW)).status, 200);
  assert.deepEqual(received, ["test"]);
  const unscoped = Object.fromEntries([["METERENGINE_API_KEY", "key-test"], ["METERENGINE_WEBHOOK_SECRET", SECRET_TEST]]);
  const legacy = await handleWebhookRequest(signedRequest("?target=local", undefined, SECRET_TEST), unscoped, append, NOW);
  assert.equal((await legacy.json()).reason, "secret_not_configured");
  assert.deepEqual(received, ["test", "legacy"]);
});

test("URL의 대상을 하나만 고르고 서명 전 본문 환경으로 대상을 바꾸지 않는다", async () => {
  const received: string[] = [];
  const append = async (scope: { id: string }) => { received.push(scope.id); };
  for (const query of ["", "?target=unknown", "?target=test&target=live", "?target=test&target=test", "?target="]) {
    const result = await handleWebhookRequest(signedRequest(query, "live", SECRET_LIVE), configuration(), append, NOW);
    assert.equal(result.status, 400, query);
  }
  assert.deepEqual(received, []);
  const wrongSecret = await handleWebhookRequest(signedRequest("?target=test", "live", SECRET_LIVE), configuration(), append, NOW);
  assert.deepEqual(await wrongSecret.json(), { reason: "signature_mismatch" });
  const signedWrongEnvironment = await handleWebhookRequest(signedRequest("?target=test", "live", SECRET_TEST), configuration(), append, NOW);
  assert.deepEqual(await signedWrongEnvironment.json(), { reason: "environment_mismatch" });
  assert.ok(received.every(id => id === webhookTarget(TARGETS[0], configuration()).scope.id));
});

test("같은 event ID의 test와 live 수신과 조회를 실제 파일에서 분리한다", async t => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "demo-webhook-environments-"));
  t.after(() => rm(directory, { recursive: true, force: true }));
  const env = configuration();
  const append = (scope: Parameters<typeof appendScopedWebhookReceipt>[1], receipt: Parameters<typeof appendScopedWebhookReceipt>[2]) => appendScopedWebhookReceipt(directory, scope, receipt);
  for (const target of TARGETS.slice(0, 2)) {
    const result = await handleWebhookRequest(signedRequest(`?target=${target.name}`, target.environment, target.webhook_secret!), env, append, NOW);
    assert.equal(result.status, 200);
  }
  for (const target of TARGETS.slice(0, 2)) {
    const scope = webhookTarget(target, env).scope;
    const receipts = await listScopedWebhookReceipts(directory, scope);
    assert.equal(receipts.length, 1);
    assert.equal(receipts[0].webhook_id, "same-event-id");
    assert.equal(JSON.parse(receipts[0].payload).environment, target.environment);
    assert.equal(receipts[0].verified, true);
  }
  for (const changed of [{ api_key: "rotated-key" }, { name: "other-name" }, { base_url: "http://localhost:8081" }]) {
    assert.deepEqual(await listScopedWebhookReceipts(directory, webhookTarget({ ...TARGETS[0], ...changed }, env).scope), []);
  }
  assert.deepEqual(await listScopedWebhookReceipts(directory, webhookTarget(TARGETS[2], env).scope), []);
});

test("전역 legacy 비밀값을 test/live 또는 다른 legacy 대상에 대신 쓰지 않는다", async () => {
  const env = configuration(TARGETS.map(target => ({ ...target, webhook_secret: undefined })));
  for (const name of ["test", "live", "other-legacy"]) {
    const response = await handleWebhookRequest(signedRequest(`?target=${name}`, name === "other-legacy" ? undefined : name, SECRET_TEST), env, async () => {}, NOW);
    assert.deepEqual(await response.json(), { reason: "secret_not_configured" });
  }
  for (const environment of [undefined, "legacy"]) {
    const response = await handleWebhookRequest(signedRequest("?target=e2e", environment, SECRET_TEST), env, async () => {}, NOW);
    assert.equal(response.status, 200);
  }
  const wrong = await handleWebhookRequest(signedRequest("?target=e2e", "test", SECRET_TEST), env, async () => {}, NOW);
  assert.deepEqual(await wrong.json(), { reason: "environment_mismatch" });
});

test("기존 JSONL은 명시한 legacy source에서만 읽고 원문을 보존한다", async t => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "demo-webhook-legacy-"));
  t.after(() => rm(directory, { recursive: true, force: true }));
  const original = JSON.stringify({ received_at: NOW.toISOString(), webhook_id: "old-event", type: "webhook_test", verified: true, reason: null, payload: '{"type":"webhook_test","data":{"legacy":true}}' }) + "\n";
  const file = path.join(directory, "webhooks.jsonl");
  await writeFile(file, original, { mode: 0o600 });
  const env = configuration();
  for (const target of TARGETS.slice(0, 2).concat(TARGETS[3])) assert.deepEqual(await listScopedWebhookReceipts(directory, webhookTarget(target, env).scope), []);
  const legacy = webhookTarget(TARGETS[2], env).scope;
  assert.equal((await listScopedWebhookReceipts(directory, legacy))[0].webhook_id, "old-event");
  assert.equal(await readFile(file, "utf8"), original);
  const changed = webhookTarget({ ...TARGETS[2], api_key: "another-legacy-key" }, env).scope;
  await assert.rejects(() => listScopedWebhookReceipts(directory, changed), /기존 수신 기록의 연결 대상이 다릅니다/);
  assert.equal(await readFile(file, "utf8"), original);
  const binding = await readFile(path.join(directory, "webhooks-legacy-source.json"), "utf8");
  assert.equal(binding.includes(TARGETS[2].api_key), false);
  assert.equal(binding.includes(SECRET_TEST), false);
});

test("legacy 지정이 없으면 기존 전역 비밀값과 파일을 새 대상에 자동 배정하지 않는다", async t => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "demo-webhook-unbound-"));
  t.after(() => rm(directory, { recursive: true, force: true }));
  await writeFile(path.join(directory, "webhooks.jsonl"), "original legacy bytes\n");
  const env = Object.fromEntries([["METERENGINE_TARGETS", JSON.stringify([TARGETS[2]])], ["METERENGINE_WEBHOOK_SECRET", SECRET_TEST]]);
  const selected = selectWebhookTarget(["e2e"], env);
  assert.equal(selected.ok, true);
  if (!selected.ok) throw new Error("legacy 대상이 없습니다");
  assert.equal(selected.value.secret, undefined);
  assert.deepEqual(await listScopedWebhookReceipts(directory, selected.value.scope), []);
  assert.equal(await readFile(path.join(directory, "webhooks.jsonl"), "utf8"), "original legacy bytes\n");
});
