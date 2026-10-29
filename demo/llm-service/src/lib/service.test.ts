import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { createServer } from "node:http";
import { test } from "node:test";
import type { IngestEventRequest } from "@meterengine/sdk";
import { readConfig } from "./config.ts";
import type { LumenConfig } from "./config.ts";
import { LumenService, monthRange } from "./service.ts";
import type { MeteringClient } from "./service.ts";
import { LumenStore } from "./store.ts";
import { ServiceError } from "./service-error.ts";
import type { GenerationContext, GenerationOutput, GenerationProvider } from "./provider.ts";

const ACCOUNT_A = { id: "alice", name: "앨리스", email: "alice@example.test", customerId: "00000000-0000-4000-8000-000000000001" };
const ACCOUNT_B = { id: "bob", name: "밥", email: "bob@example.test", customerId: "00000000-0000-4000-8000-000000000002" };
const NOW = Date.parse("2026-10-26T12:00:00Z");

function config(databasePath = ":memory:"): LumenConfig {
  return { appOrigin: "http://localhost:3200", databasePath, accounts: [ACCOUNT_A, ACCOUNT_B], meterEngineBaseUrl: "http://127.0.0.1:38147", meterEngineApiKey: "local-unit-test-key", billingApiKey: "", meterEngineConsoleUrl: "http://localhost:33147", flushIntervalMs: 0, isConfigured: true };
}

function clientFixture(overrides: Partial<MeteringClient> = {}) {
  const events: IngestEventRequest[] = [];
  const client: MeteringClient = {
    async ingestEvent(event) { events.push({ ...event, transaction_id: event.transaction_id! }); return { transaction_id: event.transaction_id!, duplicate: false }; },
    async aggregateBillableMetricUsages() { return { month: "2026-10", billable_metric_usages: [] }; },
    async previewDraftInvoice() { return { month: "2026-10", calculated_at: "2026-10-26T12:00:00Z", total_amount: 0, customers: [] }; },
    async listInvoices() { return { invoices: [] }; },
    async listInvoicePayments() { return { invoice_payments: [] }; },
    async getPaymentMethod() { throw Object.assign(new Error("미등록"), { code: "payment_method_not_found" }); },
    async listBillableMetricPrices() { return { billable_metric_prices: [] }; },
    ...overrides,
  };
  return { client, events };
}

function pricedClientFixture(options: { customerOverride?: boolean; unavailable?: "common" | "customer" } = {}) {
  const codes = ["llm-input-tokens", "llm-output-tokens", "llm-requests"];
  const queries: Array<{ month?: string; customer_id?: string }> = [];
  const fake = clientFixture({
    async aggregateBillableMetricUsages() { return { month: "2026-10", billable_metric_usages: codes.map((code, index) => ({
      code, name: code, event_type: "llm_generation", aggregation: index === 2 ? "count" : "sum",
      target_property: index === 0 ? "input_tokens" : index === 1 ? "output_tokens" : null,
      unit_size: index === 2 ? 1 : 1000, rounding_mode: "none" as const, customers: [],
    })) }; },
    async listBillableMetricPrices(params = {}) {
      queries.push(params);
      const scope = params.customer_id ? "customer" : "common";
      if (scope === options.unavailable) throw new Error("unavailable scoped price query");
      return { billable_metric_prices: codes.map((code, index) => ({
        billable_metric_code: code, dimension_properties: ["model"],
        price_rates: scope === "common" ? ["lumen-fast", "lumen-standard"].map((model, modelIndex) => ({
          id: `${code}-${model}`, dimension_values: { model }, unit_price: [500, 1000, 10][index] * (modelIndex + 1), customer_id: null,
          effective_from: "2026-10", effective_until: null, model: "per_unit" as const, tiers: null,
        })) : options.customerOverride && index === 0 ? [{
          id: "alice-input-override", dimension_values: { model: "lumen-fast" }, unit_price: 300, customer_id: params.customer_id!,
          effective_from: "2026-01", effective_until: null, model: "per_unit" as const, tiers: null,
        }] : [],
      })) };
    },
  });
  return { ...fake, queries };
}

test("같은 요청은 응답과 거래 번호를 재사용하고 다른 내용은 409다", async t => {
  const store = new LumenStore(":memory:", [ACCOUNT_A, ACCOUNT_B]);
  t.after(() => store.close());
  const fake = clientFixture();
  let calls = 0;
  const provider: GenerationProvider = { async generate() { calls++; return { output: "응답", inputTokens: 4, outputTokens: 2 }; } };
  const service = new LumenService({ config: config(), store, client: fake.client, provider, now: () => NOW });
  const requestId = randomUUID();
  const body = { requestId, modelId: "lumen-fast", prompt: "과금 모형" };
  const first = await service.generate(ACCOUNT_A, body);
  const duplicate = await service.generate(ACCOUNT_A, { ...body, requestId: requestId.toUpperCase() });
  assert.deepEqual(duplicate, first);
  assert.equal(calls, 1);
  assert.equal(fake.events.length, 1);
  assert.equal(fake.events[0].transaction_id, `lumen:alice:${requestId}`);
  await assert.rejects(() => service.generate(ACCOUNT_A, { ...body, prompt: "다른 내용" }), error => error instanceof ServiceError && error.status === 409);
});

test("프롬프트와 응답을 전송하지 않고 계수와 서버가 정한 고객만 수집한다", async t => {
  const store = new LumenStore(":memory:", [ACCOUNT_A, ACCOUNT_B]);
  t.after(() => store.close());
  const fake = clientFixture();
  const service = new LumenService({ config: config(), store, client: fake.client, now: () => NOW });
  const generated = await service.generate(ACCOUNT_A, { requestId: randomUUID(), modelId: "lumen-standard", prompt: "비밀인 고객 문서 😀", customer_id: ACCOUNT_B.customerId, accountId: ACCOUNT_B.id });
  assert.equal(generated.inputTokens, Array.from("비밀인 고객 문서 😀").length);
  assert.equal(fake.events[0].customer_id, ACCOUNT_A.customerId);
  assert.deepEqual(Object.keys(fake.events[0].properties!).sort(), ["input_tokens", "model", "output_tokens", "source", "total_tokens"]);
  assert.ok(!JSON.stringify(fake.events).includes("비밀인 고객 문서"));
  assert.ok(!JSON.stringify(fake.events).includes(generated.output));
  assert.equal(service.listRequests(ACCOUNT_B).length, 0);
  assert.throws(() => service.getRequest(ACCOUNT_B, generated.id), error => error instanceof ServiceError && error.status === 404);
});

test("처리 중인 동일 요청은 모델을 다시 부르지 않고 202용 상태를 반환한다", async t => {
  const store = new LumenStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  const fake = clientFixture();
  let finish: (value: GenerationOutput) => void = () => undefined;
  const pending = new Promise<GenerationOutput>(resolve => { finish = resolve; });
  let calls = 0;
  const service = new LumenService({ config: config(), store, client: fake.client, provider: { generate: () => { calls++; return pending; } }, now: () => NOW });
  const body = { requestId: randomUUID(), modelId: "lumen-fast", prompt: "동시 요청" };
  const first = service.generate(ACCOUNT_A, body);
  const second = await service.generate(ACCOUNT_A, body);
  assert.equal(second.state, "processing");
  assert.equal(calls, 1);
  finish({ output: "완료", inputTokens: 5, outputTokens: 2 });
  assert.equal((await first).state, "completed");
  assert.equal(fake.events.length, 1);
});

test("교체 가능한 모델 어댑터에 계정과 고정 요청 번호를 넘긴다", async t => {
  const store = new LumenStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  const contexts: (GenerationContext | undefined)[] = [];
  const service = new LumenService({ config: config(), store, client: clientFixture().client, now: () => NOW,
    provider: { async generate(_prompt, _modelId, context) { contexts.push(context); return { output: "응답", inputTokens: 1, outputTokens: 2 }; } },
  });
  const requestId = randomUUID();
  await service.generate(ACCOUNT_A, { requestId: requestId.toUpperCase(), modelId: "lumen-fast", prompt: "요청" });
  assert.deepEqual(contexts, [{ requestId, accountId: ACCOUNT_A.id }]);
});

test("수집 실패 뒤 재기동해도 원래 월과 같은 거래 번호로 한 번 더 전달한다", async t => {
  const directory = mkdtempSync(path.join(os.tmpdir(), "lumen-outbox-"));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const databasePath = path.join(directory, "tenant.sqlite");
  let now = Date.parse("2026-10-31T14:59:59Z");
  const firstClient = clientFixture({ async ingestEvent() { throw new Error("upstream unavailable"); } });
  const firstStore = new LumenStore(databasePath, [ACCOUNT_A]);
  const first = new LumenService({ config: config(databasePath), store: firstStore, client: firstClient.client, now: () => now });
  const id = randomUUID();
  const result = await first.generate(ACCOUNT_A, { requestId: id, modelId: "lumen-fast", prompt: "월말 요청" });
  assert.equal(result.state, "completed");
  assert.equal(result.meteringStatus, "failed");
  firstStore.close();
  now += 10000;
  const recoveredClient = clientFixture();
  const recoveredStore = new LumenStore(databasePath, [ACCOUNT_A]);
  t.after(() => recoveredStore.close());
  const recovered = new LumenService({ config: config(databasePath), store: recoveredStore, client: recoveredClient.client, now: () => now });
  await recovered.flushMetering();
  assert.equal(recovered.getRequest(ACCOUNT_A, id).meteringStatus, "delivered");
  assert.equal(recoveredClient.events.length, 1);
  assert.equal(recoveredClient.events[0].timestamp, "2026-10-31T14:59:59.000Z");
  assert.equal(recoveredClient.events[0].transaction_id, `lumen:alice:${id}`);
  await recovered.retryMetering(ACCOUNT_A, { requestId: id });
  assert.equal(recoveredClient.events.length, 1);
});

test("연결 키와 환경을 바꿔 이전 원장과 전송 대기를 재사용하지 못한다", async t => {
  const directory = mkdtempSync(path.join(os.tmpdir(), "lumen-source-"));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const databasePath = path.join(directory, "test.sqlite");
  const firstStore = new LumenStore(databasePath, [ACCOUNT_A]);
  const original = { ...config(databasePath), meterEngineEnvironment: "test" as const };
  const first = new LumenService({ config: original, store: firstStore, client: clientFixture({ async ingestEvent() { throw new Error("offline"); } }).client, now: () => NOW });
  await first.generate(ACCOUNT_A, { requestId: randomUUID(), modelId: "lumen-fast", prompt: "보존할 요청" });
  firstStore.close();
  for (const changed of [{ meterEngineEnvironment: "live" as const }, { meterEngineApiKey: "another-key" }, { billingApiKey: "another-billing-key" }, { meterEngineBaseUrl: "http://localhost:9090" }]) {
    const reopened = new LumenStore(databasePath, [ACCOUNT_A]);
    try { assert.throws(() => new LumenService({ config: { ...original, ...changed }, store: reopened, client: clientFixture().client }), /별도 데이터 파일/); }
    finally { reopened.close(); }
  }
  const same = new LumenStore(databasePath, [ACCOUNT_A]);
  t.after(() => same.close());
  const resumedClient = clientFixture();
  const resumed = new LumenService({ config: original, store: same, client: resumedClient.client, now: () => NOW + 10000 });
  await resumed.flushMetering();
  assert.equal(resumedClient.events.length, 1);
  assert.ok(!readFileSync(databasePath).includes(Buffer.from(original.meterEngineApiKey)));
});

test("연결 범위가 없는 기존 원장을 새 환경에 자동 배정하지 않는다", t => {
  const store = new LumenStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  store.reserveGeneration(ACCOUNT_A.id, randomUUID(), "lumen-fast", "Fast", "기존 요청", NOW);
  assert.throws(() => new LumenService({ config: { ...config(), meterEngineEnvironment: "test" }, store, client: clientFixture().client }), /기존 요청의 MeterEngine 연결 설정을 확인할 수 없습니다/);
});

test("서비스 설정은 명시 환경만 허용하고 생략 시 키의 환경을 사용한다", () => {
  assert.equal(readConfig({}).meterEngineEnvironment, undefined);
  for (const environment of ["test", "live"]) assert.equal(readConfig(Object.fromEntries([["METERENGINE_ENVIRONMENT", environment]])).meterEngineEnvironment, environment);
  assert.throws(() => readConfig(Object.fromEntries([["METERENGINE_ENVIRONMENT", "legacy"]])));
});

test("같은 고객의 test와 live 서비스가 실제 SDK 전송을 각 환경에만 보낸다", async t => {
  const events: { environment: string; authorization: string; transactionId: string }[] = [];
  const backend = createServer(async (request, response) => {
    let body = "";
    for await (const chunk of request) body += chunk;
    const event = JSON.parse(body) as { transaction_id: string };
    events.push({ environment: String(request.headers["x-meterengine-environment"]), authorization: String(request.headers.authorization), transactionId: event.transaction_id });
    response.writeHead(200, { "Content-Type": "application/json" });
    response.end(JSON.stringify({ transaction_id: event.transaction_id, duplicate: false }));
  });
  await new Promise<void>(resolve => backend.listen(0, "127.0.0.1", resolve));
  t.after(() => new Promise<void>((resolve, reject) => backend.close(error => error ? reject(error) : resolve())));
  const address = backend.address();
  if (typeof address !== "object" || address === null) throw new Error("로컬 검증 서버가 없습니다");
  const requestId = randomUUID();
  for (const environment of ["test", "live"] as const) {
    const store = new LumenStore(":memory:", [ACCOUNT_A]);
    try {
      const service: LumenService = new LumenService({ config: { ...config(), meterEngineBaseUrl: `http://127.0.0.1:${address.port}`, meterEngineApiKey: `local-key-${environment}`, meterEngineEnvironment: environment }, store, now: () => NOW });
      const generated = await service.generate(ACCOUNT_A, { requestId, modelId: "lumen-fast", prompt: `${environment} 요청` });
      assert.equal(generated.meteringStatus, "delivered");
    } finally { store.close(); }
  }
  assert.deepEqual(events, ["test", "live"].map(environment => ({ environment, authorization: `Bearer local-key-${environment}`, transactionId: `lumen:alice:${requestId}` })));
});

test("응답이 유실된 수집도 같은 거래 번호로 다시 보내 중복 판정으로 마친다", async t => {
  const store = new LumenStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  const accepted = new Set<string>();
  const observed: string[] = [];
  const fake = clientFixture({ async ingestEvent(event) {
    const id = event.transaction_id!;
    observed.push(id);
    if (!accepted.has(id)) { accepted.add(id); throw new Error("lost response"); }
    return { transaction_id: id, duplicate: true };
  } });
  const service = new LumenService({ config: config(), store, client: fake.client, now: () => NOW });
  const id = randomUUID();
  await service.generate(ACCOUNT_A, { requestId: id, modelId: "lumen-fast", prompt: "한 번만 청구" });
  assert.equal(service.getRequest(ACCOUNT_A, id).meteringStatus, "failed");
  await service.retryMetering(ACCOUNT_A, { requestId: id.toUpperCase() });
  assert.equal(service.getRequest(ACCOUNT_A, id).meteringStatus, "delivered");
  assert.equal(accepted.size, 1);
  assert.equal(observed[0], observed[1]);
});

test("만료 세션과 로그아웃, 위조 토큰을 거절하고 세션 원문은 DB에 저장하지 않는다", t => {
  const directory = mkdtempSync(path.join(os.tmpdir(), "lumen-session-"));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const databasePath = path.join(directory, "tenant.sqlite");
  const store = new LumenStore(databasePath, [ACCOUNT_A]);
  const token = store.createSession(ACCOUNT_A.id, NOW);
  assert.equal(store.sessionAccount(token, NOW), ACCOUNT_A.id);
  assert.equal(store.sessionAccount("x".repeat(43), NOW), null);
  assert.equal(store.sessionAccount(token, NOW + 8 * 60 * 60 * 1000), null);
  store.close();
  assert.ok(!readFileSync(databasePath).includes(Buffer.from(token)));
  const reopened = new LumenStore(databasePath, [ACCOUNT_A]);
  assert.equal(reopened.sessionAccount(token, NOW + 1000), ACCOUNT_A.id);
  reopened.revokeSession(token);
  assert.equal(reopened.sessionAccount(token, NOW + 1000), null);
  reopened.close();
});

test("계정 목록이 바뀌어도 이전 세션으로 다른 고객을 선택하지 못한다", t => {
  const store = new LumenStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  const token = store.createSession(ACCOUNT_A.id, NOW);
  const service = new LumenService({ config: { ...config(), accounts: [ACCOUNT_B] }, store, client: clientFixture().client, now: () => NOW });
  assert.equal(service.accountForSession(token), null);
  assert.throws(() => service.requireAccount(token), error => error instanceof ServiceError && error.status === 401);
});

test("수집 임대가 만료되면 재선점하고 예전 작업자는 결과를 덮지 못한다", t => {
  const store = new LumenStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  const id = randomUUID();
  const generation = store.reserveGeneration(ACCOUNT_A.id, id, "lumen-fast", "Fast", "시작", NOW);
  store.completeGeneration(ACCOUNT_A.id, id, generation.leaseToken!, "완료", 2, 2, { transaction_id: `lumen:alice:${id}`, customer_id: ACCOUNT_A.customerId, type: "llm_generation", timestamp: new Date(NOW).toISOString(), properties: { input_tokens: 2, output_tokens: 2, model: "lumen-fast" } });
  const first = store.claimOutbox(NOW)!;
  assert.equal(store.claimOutbox(NOW + 1000), null);
  const second = store.claimOutbox(NOW + 30001)!;
  store.finishOutbox(first, NOW + 30002, true);
  assert.equal(store.getGeneration(ACCOUNT_A.id, id)?.meteringStatus, "pending");
  store.finishOutbox(second, NOW + 30003, true);
  assert.equal(store.getGeneration(ACCOUNT_A.id, id)?.meteringStatus, "delivered");
});

test("모델 작업 임대가 만료돼도 새 작업 결과만 outbox와 함께 저장한다", t => {
  const store = new LumenStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  const id = randomUUID();
  const old = store.reserveGeneration(ACCOUNT_A.id, id, "lumen-fast", "Fast", "시작", NOW);
  const next = store.reserveGeneration(ACCOUNT_A.id, id, "lumen-fast", "Fast", "시작", NOW + 60001);
  const event = { transaction_id: `lumen:alice:${id}`, customer_id: ACCOUNT_A.customerId, type: "llm_generation", timestamp: old.generation.createdAt, properties: { input_tokens: 2 } };
  store.completeGeneration(ACCOUNT_A.id, id, old.leaseToken!, "옛 결과", 2, 3, event);
  assert.equal(store.getGeneration(ACCOUNT_A.id, id)?.state, "processing");
  store.completeGeneration(ACCOUNT_A.id, id, next.leaseToken!, "새 결과", 2, 3, event);
  assert.equal(store.getGeneration(ACCOUNT_A.id, id)?.output, "새 결과");
  assert.equal(store.claimOutbox(NOW + 60002)?.event.timestamp, old.generation.createdAt);
});

test("한 계정의 실패 재전송이 다른 계정의 큐를 처리하지 않는다", async t => {
  const store = new LumenStore(":memory:", [ACCOUNT_A, ACCOUNT_B]);
  t.after(() => store.close());
  const fake = clientFixture({ async ingestEvent() { throw new Error("offline"); } });
  let now = NOW;
  const service = new LumenService({ config: config(), store, client: fake.client, now: () => now });
  const a = randomUUID();
  const b = randomUUID();
  await service.generate(ACCOUNT_A, { requestId: a, modelId: "lumen-fast", prompt: "앨리스" });
  await service.generate(ACCOUNT_B, { requestId: b, modelId: "lumen-fast", prompt: "밥" });
  await assert.rejects(() => service.retryMetering(ACCOUNT_A, { requestId: b }), error => error instanceof ServiceError && error.status === 404);
  fake.client.ingestEvent = async event => { fake.events.push({ ...event, transaction_id: event.transaction_id! }); return { transaction_id: event.transaction_id!, duplicate: false }; };
  now = NOW + 6000;
  await service.retryMetering(ACCOUNT_A, {});
  assert.equal(service.getRequest(ACCOUNT_A, a).meteringStatus, "delivered");
  assert.equal(service.getRequest(ACCOUNT_B, b).meteringStatus, "failed");
  assert.deepEqual(fake.events.map(event => event.customer_id), [ACCOUNT_A.customerId]);
});

test("조회 응답에 다른 고객의 사용량, 인보이스, 납부 상태를 섞지 않는다", async t => {
  const store = new LumenStore(":memory:", [ACCOUNT_A, ACCOUNT_B]);
  t.after(() => store.close());
  const fake = clientFixture({
    async aggregateBillableMetricUsages() { return { month: "2026-10", billable_metric_usages: [{ code: "llm-input-tokens", name: "입력", event_type: "llm_generation", aggregation: "sum", target_property: "input_tokens", unit_size: 1000, customers: [
      { customer_id: ACCOUNT_A.customerId, customer_name: "앨리스", quantity: 0.01, raw_quantity: 10, dimension_usages: [{ dimension_values: { model: "lumen-fast" }, quantity: 0.01, raw_quantity: 10 }] },
      { customer_id: ACCOUNT_B.customerId, customer_name: "다른 고객 비밀", quantity: 999, dimension_usages: [] },
    ] }] }; },
    async previewDraftInvoice() { return { month: "2026-10", calculated_at: "2026-10-26T12:00:00Z", total_amount: 999999, customers: [
      { customer_id: ACCOUNT_A.customerId, customer_name: "앨리스", amount: 100, lines: [], unpriced_usages: [], negative_usages: [] },
      { customer_id: ACCOUNT_B.customerId, customer_name: "다른 고객 비밀", amount: 999999, lines: [], unpriced_usages: [], negative_usages: [] },
    ] }; },
    async listInvoices() { return { invoices: [
      { id: "a-invoice", invoice_number: "ME-202610-00000001", customer_id: ACCOUNT_A.customerId, customer_name: "앨리스", month: "2026-10", finalized_at: "2026-10-26T12:00:00Z", supply_amount: 100, tax_amount: 10, total_amount: 110, lines: [] },
      { id: "b-invoice", invoice_number: "ME-202610-00000002", customer_id: ACCOUNT_B.customerId, customer_name: "다른 고객 비밀", month: "2026-10", finalized_at: "2026-10-26T12:00:00Z", supply_amount: 999999, tax_amount: 0, total_amount: 999999, lines: [] },
    ] }; },
    async listInvoicePayments() { return { invoice_payments: [{ invoice_id: "b-invoice", status: "paid", mode: "test", amount: 999999, canceled_amount: 0, attempt_count: 1, last_failure_code: null, next_retry_at: null, completed_at: null }] }; },
  });
  const service = new LumenService({ config: config(), store, client: fake.client, now: () => NOW });
  const result = await service.workspace(ACCOUNT_A, "2026-10");
  assert.equal(result.billing.draft?.totalAmount, 110);
  assert.equal(result.billing.invoices.length, 1);
  assert.equal(result.billing.invoices[0].id, "a-invoice");
  assert.equal(result.billing.invoices[0].status, "unavailable");
  assert.equal(result.billing.invoices[0].paidAmount, null);
  assert.equal(result.billing.invoices[0].canceledAmount, null);
  assert.equal(result.billing.invoices[0].amountDue, null);
  assert.deepEqual(result.usage.metrics.map(metric => [metric.code, metric.quantity, metric.rawQuantity, metric.modelQuantities.length]), [["llm-input-tokens", 0.01, 10, 1]]);
  assert.ok(!JSON.stringify(result).includes("다른 고객 비밀"));
  assert.ok(!JSON.stringify(result).includes("999999"));
});

test("부분 조회 실패를 가짜 0원 성공으로 바꾸지 않는다", async t => {
  const store = new LumenStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  const fake = clientFixture({
    async aggregateBillableMetricUsages() { throw new Error("secret upstream payload"); },
    async previewDraftInvoice() { throw new Error("secret upstream payload"); },
    async listBillableMetricPrices() { throw new Error("secret upstream payload"); },
  });
  const result = await new LumenService({ config: config(), store, client: fake.client, now: () => NOW }).workspace(ACCOUNT_A, "2026-10");
  assert.equal(result.usage.state, "unavailable");
  assert.equal(result.billing.state, "unavailable");
  assert.equal(result.billing.draft, null);
  assert.equal(result.models[0].requestPrice, null);
  assert.ok(!JSON.stringify(result).includes("secret upstream payload"));
});

test("고객별 단가 응답이 비어 있어도 공통 단가를 조회해 모델 요금을 표시한다", async t => {
  const store = new LumenStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  const fake = pricedClientFixture();
  const result = await new LumenService({ config: config(), store, client: fake.client, now: () => NOW }).workspace(ACCOUNT_A, "2026-10");
  assert.deepEqual(fake.queries, [{ month: "2026-10" }, { month: "2026-10", customer_id: ACCOUNT_A.customerId }]);
  assert.deepEqual(result.models.map(model => [model.inputPricePerThousand, model.outputPricePerThousand, model.requestPrice]), [[500, 1000, 10], [1000, 2000, 20]]);
  assert.equal(result.usage.state, "ready");
  assert.equal(result.billing.state, "ready");
});

test("고객별 단가는 더 최근 공통 단가보다 우선하고 다른 모델과 미터는 공통 단가를 쓴다", async t => {
  const store = new LumenStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  const fake = pricedClientFixture({ customerOverride: true });
  const result = await new LumenService({ config: config(), store, client: fake.client, now: () => NOW }).workspace(ACCOUNT_A, "2026-10");
  assert.deepEqual(result.models.map(model => [model.inputPricePerThousand, model.outputPricePerThousand, model.requestPrice]), [[300, 1000, 10], [1000, 2000, 20]]);
  assert.equal(result.billing.state, "ready");
});

test("공통 단가와 고객별 단가 중 하나라도 조회 실패하면 부분 단가를 확정 가격으로 표시하지 않는다", async t => {
  const store = new LumenStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  for (const unavailable of ["common", "customer"] as const) {
    const fake = pricedClientFixture({ customerOverride: true, unavailable });
    const result = await new LumenService({ config: config(), store, client: fake.client, now: () => NOW }).workspace(ACCOUNT_A, "2026-10");
    assert.deepEqual(result.models.map(model => [model.inputPricePerThousand, model.outputPricePerThousand, model.requestPrice]), [[null, null, null], [null, null, null]], unavailable);
    assert.equal(result.billing.state, "unavailable", unavailable);
    assert.equal(result.usage.state, "unavailable", unavailable);
    assert.match(result.usage.error!, /공통 요금 또는 고객별 요금/);
    assert.ok(!JSON.stringify(result).includes("unavailable scoped price query"));
  }
});

test("미터 단위와 집계 방식 또는 차원이 달라지면 고정 단위 요금을 안내하지 않는다", async t => {
  const store = new LumenStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  const codes = ["llm-input-tokens", "llm-output-tokens", "llm-requests"];
  for (const change of ["none", "unit", "aggregation", "rounding", "dimensions"]) {
    const fake = clientFixture({
      async aggregateBillableMetricUsages() { return { month: "2026-10", billable_metric_usages: codes.map((code, index) => ({
        code, name: code, event_type: "llm_generation", aggregation: index === 2 ? "count" : change === "aggregation" && index === 0 ? "max" : "sum",
        target_property: index === 0 ? "input_tokens" : index === 1 ? "output_tokens" : null,
        unit_size: index === 2 ? 1 : change === "unit" && index === 0 ? 100 : 1000,
        rounding_mode: change === "rounding" && index === 0 ? "ceil" as const : "none" as const, customers: [],
      })) }; },
      async listBillableMetricPrices() { return { billable_metric_prices: codes.map(code => ({
        billable_metric_code: code, dimension_properties: change === "dimensions" && code === codes[0] ? ["region"] : ["model"],
        price_rates: ["lumen-fast", "lumen-standard"].map(model => ({ id: `${code}-${model}`, dimension_values: { model }, unit_price: 100, customer_id: null, effective_from: "2026-01", effective_until: null, model: "per_unit" as const, tiers: null })),
      })) }; },
    });
    const result = await new LumenService({ config: config(), store, client: fake.client, now: () => NOW }).workspace(ACCOUNT_A, "2026-10");
    if (change === "none") {
      assert.equal(result.models[0].inputPricePerThousand, 100);
      assert.equal(result.usage.state, "ready");
    } else {
      assert.equal(result.models[0].inputPricePerThousand, null, change);
      assert.equal(result.billing.state, "unavailable", change);
      assert.match(result.usage.error!, /집계 또는 요금 설정/);
    }
  }
});

test("결제 취소는 별도 금액으로 표시하고 다시 결제할 미납액으로 만들지 않는다", async t => {
  const store = new LumenStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  for (const canceled of [40, 110]) {
    const fake = clientFixture({
      async listInvoices() { return { invoices: [{ id: "invoice", invoice_number: "ME-202610-00000001", customer_id: ACCOUNT_A.customerId, customer_name: "앨리스", month: "2026-10", finalized_at: "2026-10-26T12:00:00Z", supply_amount: 100, tax_amount: 10, total_amount: 110, lines: [] }] }; },
      async listInvoicePayments() { return { invoice_payments: [{ invoice_id: "invoice", status: canceled === 110 ? "canceled" : "partially_canceled", mode: "test", amount: 110, canceled_amount: canceled, attempt_count: 1, last_failure_code: null, next_retry_at: null, completed_at: null }] }; },
    });
    const result = await new LumenService({ config: config(), store, client: fake.client, now: () => NOW }).workspace(ACCOUNT_A, "2026-10");
    assert.equal(result.billing.invoices[0].amountDue, 0);
    assert.equal(result.billing.invoices[0].canceledAmount, canceled);
    assert.equal(result.billing.invoices[0].paidAmount, 110 - canceled);
  }
});

test("모델 실패와 잘못된 입력은 수집 없이 복구할 수 있다", async t => {
  const store = new LumenStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  const fake = clientFixture();
  const service = new LumenService({ config: config(), store, client: fake.client, provider: { async generate() { throw new Error("private provider key"); } }, now: () => NOW });
  const body = { requestId: randomUUID(), modelId: "lumen-fast", prompt: "요청" };
  await assert.rejects(() => service.generate(ACCOUNT_A, body), error => error instanceof ServiceError && error.code === "generation_failed" && !error.message.includes("private provider key"));
  assert.equal(service.getRequest(ACCOUNT_A, body.requestId).state, "failed");
  assert.equal(fake.events.length, 0);
  await assert.rejects(() => service.generate(ACCOUNT_A, { ...body, modelId: "not-a-model" }), error => error instanceof ServiceError && error.status === 400);
  await assert.rejects(() => service.generate(ACCOUNT_A, { ...body, prompt: "x".repeat(4001) }), error => error instanceof ServiceError && error.status === 400);
});

test("시연 카드 등록은 로그인 고객으로만 만들고 실결제 모드를 반환하지 않는다", async t => {
  const store = new LumenStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  const observed: string[] = [];
  const registrationClient = { async createPaymentMethodRegistration(id: string) { observed.push(id); return { token: "only-local-test-token", customer_id: id, customer_name: "앨리스", organization_name: "Lumen", client_key: "not-exposed", customer_key: "not-exposed", mode: "test" as const, expires_at: "2026-10-27T12:00:00Z" }; } };
  const service = new LumenService({ config: config(), store, client: clientFixture().client, registrationClient, now: () => NOW });
  const result = await service.registerPaymentMethod(ACCOUNT_A);
  assert.deepEqual(observed, [ACCOUNT_A.customerId]);
  assert.equal(result.url, "http://localhost:33147/payment-method-registrations/only-local-test-token");
  assert.ok(!JSON.stringify(result).includes("not-exposed"));
  const live = new LumenService({ config: config(), store, client: clientFixture().client, registrationClient: { async createPaymentMethodRegistration(id) { return { ...(await registrationClient.createPaymentMethodRegistration(id)), mode: "live" }; } }, now: () => NOW });
  await assert.rejects(() => live.registerPaymentMethod(ACCOUNT_A), error => error instanceof ServiceError && error.status === 503);
});

test("월 경계는 한국 시간 기준이며 잘못된 월은 거절한다", () => {
  assert.deepEqual(monthRange("2026-10"), { start: "2026-09-30T15:00:00.000Z", end: "2026-10-31T15:00:00.000Z" });
  assert.deepEqual(monthRange("2026-12"), { start: "2026-11-30T15:00:00.000Z", end: "2026-12-31T15:00:00.000Z" });
  assert.throws(() => monthRange("0000-01"));
  assert.throws(() => monthRange("2026-13"));
});

test("설정의 계정과 고객 중복, 잘못된 주소는 거절한다", () => {
  assert.equal(readConfig(Object.fromEntries([])).isConfigured, false);
  assert.throws(() => readConfig(Object.fromEntries([["LUMEN_ACCOUNTS", JSON.stringify([ACCOUNT_A, ACCOUNT_A])]])));
  assert.throws(() => readConfig(Object.fromEntries([["LUMEN_APP_ORIGIN", "https://secret:password@example.test"]])));
});
