import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { createServer } from "node:http";
import { test } from "node:test";
import { MeterEngineApiError } from "@meterengine/sdk";
import type { IngestEventRequest } from "@meterengine/sdk";
import { EVENT_TYPE, METRICS, MODELS, transactionId } from "./catalog.ts";
import { readConfig } from "./config.ts";
import type { OpenAbConfig } from "./config.ts";
import { matchesMetricContract, monthRange, OpenAbService } from "./service.ts";
import type { MeteringClient } from "./service.ts";
import { OpenAbStore } from "./store.ts";
import { ServiceError } from "./service-error.ts";
import { CHAT_SYSTEM_TOKENS, estimateTokens } from "./provider.ts";
import type { GenerationContext, GenerationInput, GenerationOutput, GenerationProvider } from "./provider.ts";

const ACCOUNT_A = { id: "alpha", name: "가람데이터", email: "billing@garam.example", customerId: "00000000-0000-4000-8000-000000000001" };
const ACCOUNT_B = { id: "beta", name: "누리랩스", email: "billing@nuri.example", customerId: "00000000-0000-4000-8000-000000000002" };
const NOW = Date.parse("2026-10-26T12:00:00Z");
const EVENT_PROPERTIES = ["cache_read_tokens", "cache_write_tokens", "input_tokens", "latency_ms", "model", "output_tokens", "surface"];
const CODES = METRICS.map(metric => metric.code);

function config(databasePath = ":memory:"): OpenAbConfig {
  return { appOrigin: "http://localhost:3200", databasePath, accounts: [ACCOUNT_A, ACCOUNT_B], meterEngineBaseUrl: "http://127.0.0.1:38147", meterEngineApiKey: "local-unit-test-key", billingApiKey: "", meterEngineConsoleUrl: "http://localhost:33147", flushIntervalMs: 0, isConfigured: true };
}

function fixedProvider(output = "응답"): GenerationProvider {
  return { async generate() { return { output, usage: { input: 4, output: 2, cacheRead: 0, cacheWrite: 0 }, latencyMs: 300 }; } };
}

function apiBody(prompt: string, requestId: string = randomUUID(), modelId = "ab-5-mini") {
  return { requestId, surface: "api", modelId, prompt };
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

type UsageChange = "none" | "unit" | "aggregation" | "rounding" | "dimensions" | "eventType";

function usageMetrics(change: UsageChange = "none") {
  return CODES.map((code, index) => ({
    code, name: code, event_type: change === "eventType" && index === 0 ? "llm_generation" : EVENT_TYPE,
    aggregation: change === "aggregation" && index === 0 ? "max" : "sum", target_property: METRICS[index].property,
    unit_size: change === "unit" && index === 0 ? 1000 : 1_000_000, rounding_mode: change === "rounding" && index === 0 ? "ceil" as const : "none" as const, customers: [],
  }));
}

const BASE_PRICES: Record<string, number[]> = { "ab-5": [4000, 20000, 400, 5000], "ab-5-mini": [800, 4000, 80, 1000], "ab-5-nano": [150, 600, 15, 190], "ab-embed-3": [30] };

function commonRates(code: string, index: number) {
  return MODELS.flatMap(model => model.surfaces.flatMap(surface => {
    const base = BASE_PRICES[model.id][index];
    if (base === undefined) return [];
    return [{ id: `${code}-${model.id}-${surface}`, dimension_values: { model: model.id, surface }, unit_price: surface === "batch" ? base / 2 : base, customer_id: null, effective_from: "2026-10", effective_until: null, model: "per_unit" as const, tiers: null }];
  }));
}

function pricedClientFixture(options: { customerOverride?: boolean; unavailable?: "common" | "customer"; change?: UsageChange } = {}) {
  const queries: Array<{ month?: string; customer_id?: string }> = [];
  const fake = clientFixture({
    async aggregateBillableMetricUsages() { return { month: "2026-10", billable_metric_usages: usageMetrics(options.change) }; },
    async listBillableMetricPrices(params = {}) {
      queries.push(params);
      const scope = params.customer_id ? "customer" : "common";
      if (scope === options.unavailable) throw new Error("unavailable scoped price query");
      return { billable_metric_prices: CODES.map((code, index) => ({
        billable_metric_code: code, dimension_properties: options.change === "dimensions" && index === 0 ? ["model"] : ["surface", "model"],
        price_rates: scope === "common" ? commonRates(code, index) : options.customerOverride && index === 0 ? [{
          id: "alpha-input-override", dimension_values: { model: "ab-5", surface: "api" }, unit_price: 3500, customer_id: params.customer_id!,
          effective_from: "2026-01", effective_until: null, model: "per_unit" as const, tiers: null,
        }] : [],
      })) };
    },
  });
  return { ...fake, queries };
}

test("같은 요청은 응답과 거래 ID를 재사용하고 다른 내용은 409다", async t => {
  const store = new OpenAbStore(":memory:", [ACCOUNT_A, ACCOUNT_B]);
  t.after(() => store.close());
  const fake = clientFixture();
  let calls = 0;
  const provider: GenerationProvider = { async generate(input, context) { calls++; return fixedProvider().generate(input, context); } };
  const service = new OpenAbService({ config: config(), store, client: fake.client, provider, now: () => NOW });
  const requestId = randomUUID();
  const body = apiBody("과금 모형", requestId);
  const first = await service.generate(ACCOUNT_A, body);
  const duplicate = await service.generate(ACCOUNT_A, { ...body, requestId: requestId.toUpperCase() });
  assert.deepEqual(duplicate, first);
  assert.equal(calls, 1);
  assert.equal(fake.events.length, 1);
  assert.equal(fake.events[0].transaction_id, transactionId(requestId));
  assert.match(fake.events[0].transaction_id, /^req_[0-9a-f]{32}$/);
  assert.equal(fake.events[0].type, EVENT_TYPE);
  await assert.rejects(() => service.generate(ACCOUNT_A, { ...body, prompt: "다른 내용" }), error => error instanceof ServiceError && error.status === 409);
  await assert.rejects(() => service.generate(ACCOUNT_A, { ...body, surface: "chat", conversationId: randomUUID() }), error => error instanceof ServiceError && error.status === 409);
});

test("프롬프트와 응답을 보내지 않고 일곱 속성과 서버가 정한 고객만 수집한다", async t => {
  const store = new OpenAbStore(":memory:", [ACCOUNT_A, ACCOUNT_B]);
  t.after(() => store.close());
  const fake = clientFixture();
  const service = new OpenAbService({ config: config(), store, client: fake.client, now: () => NOW });
  const generated = await service.generate(ACCOUNT_A, { ...apiBody("비밀인 고객 문서 😀", randomUUID(), "ab-5"), system: "짧은 지시", customer_id: ACCOUNT_B.customerId, accountId: ACCOUNT_B.id });
  assert.equal(generated.usage.input, estimateTokens("비밀인 고객 문서 😀") + estimateTokens("짧은 지시"));
  assert.equal(fake.events[0].customer_id, ACCOUNT_A.customerId);
  assert.deepEqual(Object.keys(fake.events[0].properties).sort(), EVENT_PROPERTIES);
  assert.deepEqual(fake.events[0].properties, { model: "ab-5", surface: "api", input_tokens: generated.usage.input, output_tokens: generated.usage.output, cache_read_tokens: 0, cache_write_tokens: 0, latency_ms: generated.latencyMs });
  assert.ok(!JSON.stringify(fake.events).includes("비밀인 고객 문서"));
  assert.ok(!JSON.stringify(fake.events).includes("짧은 지시"));
  assert.ok(!JSON.stringify(fake.events).includes(generated.output));
  assert.equal(service.listRequests(ACCOUNT_B).length, 0);
  assert.throws(() => service.getRequest(ACCOUNT_B, generated.id), error => error instanceof ServiceError && error.status === 404);
});

test("대화는 앞선 차례를 캐시 읽기로, 바로 앞 차례를 캐시 쓰기로, 새 메시지를 입력으로 센다", async t => {
  const store = new OpenAbStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  const fake = clientFixture();
  const service = new OpenAbService({ config: config(), store, client: fake.client, now: () => NOW });
  const conversationId = randomUUID();
  const prompts = ["신규 고객 안내 메일 초안을 써 줘.", "조금 더 짧게 줄여 줘.", "제목도 세 개 제안해 줘."];
  const turns = [];
  for (const prompt of prompts) turns.push(await service.generate(ACCOUNT_A, { requestId: randomUUID(), surface: "chat", conversationId, modelId: "ab-5", prompt }));
  const sizes = turns.map(turn => estimateTokens(turn.prompt) + estimateTokens(turn.output));
  assert.deepEqual(turns.map(turn => turn.usage.input), prompts.map(estimateTokens));
  assert.deepEqual(turns.map(turn => turn.usage.cacheRead), [CHAT_SYSTEM_TOKENS, CHAT_SYSTEM_TOKENS, CHAT_SYSTEM_TOKENS + sizes[0]]);
  assert.deepEqual(turns.map(turn => turn.usage.cacheWrite), [0, sizes[0], sizes[1]]);
  const last = turns[2].usage;
  assert.equal(last.cacheRead + last.cacheWrite + last.input, CHAT_SYSTEM_TOKENS + sizes[0] + sizes[1] + estimateTokens(prompts[2]));
  assert.ok(turns.every(turn => turn.usage.cacheRead > turn.usage.input * 10));
  assert.ok(turns[1].output.startsWith("앞서 나눈 이야기에 이어서"));
  assert.deepEqual(fake.events.map(event => event.properties.surface), ["chat", "chat", "chat"]);
  assert.deepEqual(fake.events.map(event => event.properties.cache_read_tokens), turns.map(turn => turn.usage.cacheRead));
  const conversation = service.getConversation(ACCOUNT_A, conversationId);
  assert.equal(conversation.turns, 3);
  assert.equal(conversation.title, prompts[0]);
  assert.deepEqual(conversation.messages.map(message => message.prompt), prompts);
  assert.deepEqual(service.listConversations(ACCOUNT_A).map(item => item.id), [conversationId]);
  assert.throws(() => service.getConversation({ ...ACCOUNT_A, id: "beta" }, conversationId), error => error instanceof ServiceError && error.status === 404);
});

test("대화 중 모델을 바꾸면 캐시를 읽지 않고 앞선 맥락을 새로 쓴다", async t => {
  const store = new OpenAbStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  const service = new OpenAbService({ config: config(), store, client: clientFixture().client, now: () => NOW });
  const conversationId = randomUUID();
  const first = await service.generate(ACCOUNT_A, { requestId: randomUUID(), surface: "chat", conversationId, modelId: "ab-5", prompt: "첫 질문" });
  const second = await service.generate(ACCOUNT_A, { requestId: randomUUID(), surface: "chat", conversationId, modelId: "ab-5-mini", prompt: "두 번째 질문" });
  assert.equal(second.usage.cacheRead, 0);
  assert.equal(second.usage.cacheWrite, CHAT_SYSTEM_TOKENS + estimateTokens(first.prompt) + estimateTokens(first.output));
});

test("API의 긴 시스템 지시는 처음에 캐시에 쓰고 5분 안에 같은 지시로 부르면 읽는다", async t => {
  const store = new OpenAbStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  let now = NOW;
  const service = new OpenAbService({ config: config(), store, client: clientFixture().client, now: () => now });
  const system = "가".repeat(1200);
  const first = await service.generate(ACCOUNT_A, { ...apiBody("요약해 줘", randomUUID(), "ab-5"), system });
  now += 60_000;
  const second = await service.generate(ACCOUNT_A, { ...apiBody("다시 요약해 줘", randomUUID(), "ab-5"), system });
  now += 10 * 60_000;
  const expired = await service.generate(ACCOUNT_A, { ...apiBody("한 번 더", randomUUID(), "ab-5"), system });
  assert.deepEqual([first.usage.cacheWrite, first.usage.cacheRead], [1200, 0]);
  assert.deepEqual([second.usage.cacheWrite, second.usage.cacheRead], [0, 1200]);
  assert.deepEqual([expired.usage.cacheWrite, expired.usage.cacheRead], [1200, 0]);
  assert.equal(second.usage.input, estimateTokens("다시 요약해 줘"));
});

test("임베딩은 입력 토큰만 세고 나머지 속성도 0으로 빠짐없이 보낸다", async t => {
  const store = new OpenAbStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  const fake = clientFixture();
  const service = new OpenAbService({ config: config(), store, client: fake.client, now: () => NOW });
  const generated = await service.generate(ACCOUNT_A, apiBody("검색할 문장", randomUUID(), "ab-embed-3"));
  const vector = JSON.parse(generated.output) as number[];
  assert.equal(vector.length, 256);
  assert.ok(Math.abs(Math.hypot(...vector) - 1) < 0.001);
  assert.deepEqual(fake.events[0].properties, { model: "ab-embed-3", surface: "api", input_tokens: estimateTokens("검색할 문장"), output_tokens: 0, cache_read_tokens: 0, cache_write_tokens: 0, latency_ms: generated.latencyMs });
  await assert.rejects(() => service.generate(ACCOUNT_A, { ...apiBody("검색", randomUUID(), "ab-embed-3"), system: "지시" }), error => error instanceof ServiceError && error.code === "invalid_system");
});

test("용도마다 쓸 수 있는 모델만 받는다", async t => {
  const store = new OpenAbStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  const service = new OpenAbService({ config: config(), store, client: clientFixture().client, provider: fixedProvider(), now: () => NOW });
  for (const modelId of ["ab-5-nano", "ab-embed-3"]) {
    await assert.rejects(() => service.generate(ACCOUNT_A, { requestId: randomUUID(), surface: "chat", conversationId: randomUUID(), modelId, prompt: "안녕" }), error => error instanceof ServiceError && error.code === "invalid_model");
  }
  await assert.rejects(() => service.generate(ACCOUNT_A, { ...apiBody("안녕"), surface: "batch" }), error => error instanceof ServiceError && error.code === "invalid_surface");
  await assert.rejects(() => service.generate(ACCOUNT_A, { requestId: randomUUID(), surface: "chat", modelId: "ab-5", prompt: "대화 ID 없음" }), error => error instanceof ServiceError && error.code === "invalid_conversation");
});

test("일괄 처리는 건마다 이벤트 하나를 batch 용도로 보내고 다시 보내도 중복되지 않는다", async t => {
  const store = new OpenAbStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  const fake = clientFixture();
  const service = new OpenAbService({ config: config(), store, client: fake.client, now: () => NOW });
  const body = { batchId: randomUUID(), modelId: "ab-5-nano", items: ["배송이 늦어요", "환불해 주세요", "좋은 서비스예요"].map(prompt => ({ requestId: randomUUID(), prompt })) };
  const result = await service.generateBatch(ACCOUNT_A, body);
  assert.equal(result.items.length, 3);
  assert.deepEqual(result.items.map(item => item.prompt), body.items.map(item => item.prompt));
  assert.ok(result.items.every(item => item.surface === "batch" && item.batchId === body.batchId && item.state === "completed" && item.meteringStatus === "delivered"));
  assert.deepEqual(fake.events.map(event => event.transaction_id), body.items.map(item => transactionId(item.requestId)));
  assert.ok(fake.events.every(event => event.properties.surface === "batch" && event.properties.model === "ab-5-nano" && event.properties.cache_read_tokens === 0));
  await service.generateBatch(ACCOUNT_A, body);
  assert.equal(fake.events.length, 3);
  const tooMany = { ...body, batchId: randomUUID(), items: Array.from({ length: 21 }, () => ({ requestId: randomUUID(), prompt: "x" })) };
  await assert.rejects(() => service.generateBatch(ACCOUNT_A, tooMany), error => error instanceof ServiceError && error.code === "invalid_batch");
  const repeated = randomUUID();
  await assert.rejects(() => service.generateBatch(ACCOUNT_A, { ...body, batchId: randomUUID(), items: [{ requestId: repeated, prompt: "a" }, { requestId: repeated, prompt: "b" }] }), error => error instanceof ServiceError && error.code === "invalid_batch");
  await assert.rejects(() => service.generateBatch(ACCOUNT_A, { ...body, modelId: "ab-5-mini-x" }), error => error instanceof ServiceError && error.code === "invalid_model");
  assert.equal(fake.events.length, 3);
});

test("일괄 처리의 한 건이 실패해도 나머지는 처리하고 실패한 건은 수집하지 않는다", async t => {
  const store = new OpenAbStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  const fake = clientFixture();
  const provider: GenerationProvider = { async generate(input: GenerationInput, context?: GenerationContext): Promise<GenerationOutput> {
    if (input.prompt === "실패") throw new Error("provider down");
    return fixedProvider().generate(input, context);
  } };
  const service = new OpenAbService({ config: config(), store, client: fake.client, provider, now: () => NOW });
  const result = await service.generateBatch(ACCOUNT_A, { batchId: randomUUID(), modelId: "ab-5", items: ["성공", "실패", "성공 둘"].map(prompt => ({ requestId: randomUUID(), prompt })) });
  assert.deepEqual(result.items.map(item => item.state), ["completed", "failed", "completed"]);
  assert.equal(fake.events.length, 2);
});

test("처리 중인 동일 요청은 모델을 다시 부르지 않고 202용 상태를 반환한다", async t => {
  const store = new OpenAbStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  const fake = clientFixture();
  let finish: (value: GenerationOutput) => void = () => undefined;
  const pending = new Promise<GenerationOutput>(resolve => { finish = resolve; });
  let calls = 0;
  const service = new OpenAbService({ config: config(), store, client: fake.client, provider: { generate: () => { calls++; return pending; } }, now: () => NOW });
  const body = apiBody("동시 요청");
  const first = service.generate(ACCOUNT_A, body);
  const second = await service.generate(ACCOUNT_A, body);
  assert.equal(second.state, "processing");
  assert.equal(calls, 1);
  finish({ output: "완료", usage: { input: 5, output: 2, cacheRead: 0, cacheWrite: 0 }, latencyMs: 100 });
  assert.equal((await first).state, "completed");
  assert.equal(fake.events.length, 1);
});

test("교체 가능한 모델 어댑터에 계정과 고정 요청 번호를 넘긴다", async t => {
  const store = new OpenAbStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  const contexts: (GenerationContext | undefined)[] = [];
  const service = new OpenAbService({ config: config(), store, client: clientFixture().client, now: () => NOW,
    provider: { async generate(input, context) { contexts.push(context); return fixedProvider().generate(input, context); } },
  });
  const requestId = randomUUID();
  await service.generate(ACCOUNT_A, apiBody("요청", requestId.toUpperCase()));
  assert.deepEqual(contexts, [{ requestId, accountId: ACCOUNT_A.id }]);
});

test("모형이 잘못된 토큰 수를 돌려주면 수집하지 않고 실패로 남긴다", async t => {
  const store = new OpenAbStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  const fake = clientFixture();
  const service = new OpenAbService({ config: config(), store, client: fake.client, now: () => NOW,
    provider: { async generate() { return { output: "응답", usage: { input: 1, output: -1, cacheRead: 0, cacheWrite: 0 }, latencyMs: 10 }; } },
  });
  const body = apiBody("요청");
  await assert.rejects(() => service.generate(ACCOUNT_A, body), error => error instanceof ServiceError && error.code === "generation_failed");
  assert.equal(service.getRequest(ACCOUNT_A, body.requestId).state, "failed");
  assert.equal(fake.events.length, 0);
});

test("수집 실패 뒤 재기동해도 원래 월과 같은 거래 ID로 한 번 더 전달한다", async t => {
  const directory = mkdtempSync(path.join(os.tmpdir(), "openab-outbox-"));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const databasePath = path.join(directory, "tenant.sqlite");
  let now = Date.parse("2026-10-31T14:59:59Z");
  const firstClient = clientFixture({ async ingestEvent() { throw new Error("upstream unavailable"); } });
  const firstStore = new OpenAbStore(databasePath, [ACCOUNT_A]);
  const first = new OpenAbService({ config: config(databasePath), store: firstStore, client: firstClient.client, now: () => now });
  const id = randomUUID();
  const result = await first.generate(ACCOUNT_A, apiBody("월말 요청", id));
  assert.equal(result.state, "completed");
  assert.equal(result.meteringStatus, "failed");
  firstStore.close();
  now += 10000;
  const recoveredClient = clientFixture();
  const recoveredStore = new OpenAbStore(databasePath, [ACCOUNT_A]);
  t.after(() => recoveredStore.close());
  const recovered = new OpenAbService({ config: config(databasePath), store: recoveredStore, client: recoveredClient.client, now: () => now });
  await recovered.flushMetering();
  assert.equal(recovered.getRequest(ACCOUNT_A, id).meteringStatus, "delivered");
  assert.equal(recoveredClient.events.length, 1);
  assert.equal(recoveredClient.events[0].timestamp, "2026-10-31T14:59:59.000Z");
  assert.equal(recoveredClient.events[0].transaction_id, transactionId(id));
  await recovered.retryMetering(ACCOUNT_A, { requestId: id });
  assert.equal(recoveredClient.events.length, 1);
});

test("연결 키와 환경을 바꿔 이전 원장과 전송 대기를 재사용하지 못한다", async t => {
  const directory = mkdtempSync(path.join(os.tmpdir(), "openab-source-"));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const databasePath = path.join(directory, "test.sqlite");
  const firstStore = new OpenAbStore(databasePath, [ACCOUNT_A]);
  const original = { ...config(databasePath), meterEngineEnvironment: "test" as const };
  const first = new OpenAbService({ config: original, store: firstStore, client: clientFixture({ async ingestEvent() { throw new Error("offline"); } }).client, now: () => NOW });
  await first.generate(ACCOUNT_A, apiBody("보존할 요청"));
  firstStore.close();
  for (const changed of [{ meterEngineEnvironment: "live" as const }, { meterEngineApiKey: "another-key" }, { billingApiKey: "another-billing-key" }, { meterEngineBaseUrl: "http://localhost:9090" }]) {
    const reopened = new OpenAbStore(databasePath, [ACCOUNT_A]);
    try { assert.throws(() => new OpenAbService({ config: { ...original, ...changed }, store: reopened, client: clientFixture().client }), /별도 데이터 파일/); }
    finally { reopened.close(); }
  }
  const same = new OpenAbStore(databasePath, [ACCOUNT_A]);
  t.after(() => same.close());
  const resumedClient = clientFixture();
  const resumed = new OpenAbService({ config: original, store: same, client: resumedClient.client, now: () => NOW + 10000 });
  await resumed.flushMetering();
  assert.equal(resumedClient.events.length, 1);
  assert.ok(!readFileSync(databasePath).includes(Buffer.from(original.meterEngineApiKey)));
});

test("연결 범위가 없는 기존 원장을 새 환경에 자동 배정하지 않는다", t => {
  const store = new OpenAbStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  store.reserveGeneration(ACCOUNT_A.id, { id: randomUUID(), surface: "api", modelId: "ab-5", modelName: "AB-5", prompt: "기존 요청" }, NOW);
  assert.throws(() => new OpenAbService({ config: { ...config(), meterEngineEnvironment: "test" }, store, client: clientFixture().client }), /기존 요청의 MeterEngine 연결 설정을 확인할 수 없습니다/);
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
    const store = new OpenAbStore(":memory:", [ACCOUNT_A]);
    try {
      const service: OpenAbService = new OpenAbService({ config: { ...config(), meterEngineBaseUrl: `http://127.0.0.1:${address.port}`, meterEngineApiKey: `local-key-${environment}`, meterEngineEnvironment: environment }, store, now: () => NOW });
      const generated = await service.generate(ACCOUNT_A, apiBody(`${environment} 요청`, requestId));
      assert.equal(generated.meteringStatus, "delivered");
    } finally { store.close(); }
  }
  assert.deepEqual(events, ["test", "live"].map(environment => ({ environment, authorization: `Bearer local-key-${environment}`, transactionId: transactionId(requestId) })));
});

test("응답이 유실된 수집도 같은 거래 ID로 다시 보내 중복 판정으로 마친다", async t => {
  const store = new OpenAbStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  const accepted = new Set<string>();
  const observed: string[] = [];
  const fake = clientFixture({ async ingestEvent(event) {
    const id = event.transaction_id!;
    observed.push(id);
    if (!accepted.has(id)) { accepted.add(id); throw new Error("lost response"); }
    return { transaction_id: id, duplicate: true };
  } });
  const service = new OpenAbService({ config: config(), store, client: fake.client, now: () => NOW });
  const id = randomUUID();
  await service.generate(ACCOUNT_A, apiBody("한 번만 청구", id));
  assert.equal(service.getRequest(ACCOUNT_A, id).meteringStatus, "failed");
  await service.retryMetering(ACCOUNT_A, { requestId: id.toUpperCase() });
  assert.equal(service.getRequest(ACCOUNT_A, id).meteringStatus, "delivered");
  assert.equal(accepted.size, 1);
  assert.equal(observed[0], observed[1]);
});

test("만료 세션과 로그아웃, 위조 토큰을 거절하고 세션 원문은 DB에 저장하지 않는다", t => {
  const directory = mkdtempSync(path.join(os.tmpdir(), "openab-session-"));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const databasePath = path.join(directory, "tenant.sqlite");
  const store = new OpenAbStore(databasePath, [ACCOUNT_A]);
  const token = store.createSession(ACCOUNT_A.id, NOW);
  assert.equal(store.sessionAccount(token, NOW), ACCOUNT_A.id);
  assert.equal(store.sessionAccount("x".repeat(43), NOW), null);
  assert.equal(store.sessionAccount(token, NOW + 8 * 60 * 60 * 1000), null);
  store.close();
  assert.ok(!readFileSync(databasePath).includes(Buffer.from(token)));
  const reopened = new OpenAbStore(databasePath, [ACCOUNT_A]);
  assert.equal(reopened.sessionAccount(token, NOW + 1000), ACCOUNT_A.id);
  reopened.revokeSession(token);
  assert.equal(reopened.sessionAccount(token, NOW + 1000), null);
  reopened.close();
});

test("계정 목록이 바뀌어도 이전 세션으로 다른 고객을 선택하지 못한다", t => {
  const store = new OpenAbStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  const token = store.createSession(ACCOUNT_A.id, NOW);
  const service = new OpenAbService({ config: { ...config(), accounts: [ACCOUNT_B] }, store, client: clientFixture().client, now: () => NOW });
  assert.equal(service.accountForSession(token), null);
  assert.throws(() => service.requireAccount(token), error => error instanceof ServiceError && error.status === 401);
});

test("수집 임대가 만료되면 재선점하고 예전 작업자는 결과를 덮지 못한다", t => {
  const store = new OpenAbStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  const id = randomUUID();
  const generation = store.reserveGeneration(ACCOUNT_A.id, { id, surface: "api", modelId: "ab-5", modelName: "AB-5", prompt: "시작" }, NOW);
  store.completeGeneration(ACCOUNT_A.id, id, generation.leaseToken!, { output: "완료", usage: { input: 2, output: 2, cacheRead: 0, cacheWrite: 0 }, latencyMs: 10 }, { transaction_id: transactionId(id), customer_id: ACCOUNT_A.customerId, type: EVENT_TYPE, timestamp: new Date(NOW).toISOString(), properties: { input_tokens: 2 } });
  const first = store.claimOutbox(NOW)!;
  assert.equal(store.claimOutbox(NOW + 1000), null);
  const second = store.claimOutbox(NOW + 30001)!;
  store.finishOutbox(first, NOW + 30002, true);
  assert.equal(store.getGeneration(ACCOUNT_A.id, id)?.meteringStatus, "pending");
  store.finishOutbox(second, NOW + 30003, true);
  assert.equal(store.getGeneration(ACCOUNT_A.id, id)?.meteringStatus, "delivered");
});

test("모델 작업 임대가 만료돼도 새 작업 결과만 outbox와 함께 저장한다", t => {
  const store = new OpenAbStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  const id = randomUUID();
  const spec = { id, surface: "api" as const, modelId: "ab-5", modelName: "AB-5", prompt: "시작" };
  const old = store.reserveGeneration(ACCOUNT_A.id, spec, NOW);
  const next = store.reserveGeneration(ACCOUNT_A.id, spec, NOW + 60001);
  const event = { transaction_id: transactionId(id), customer_id: ACCOUNT_A.customerId, type: EVENT_TYPE, timestamp: old.generation.createdAt, properties: { input_tokens: 2 } };
  store.completeGeneration(ACCOUNT_A.id, id, old.leaseToken!, { output: "옛 결과", usage: { input: 2, output: 3, cacheRead: 0, cacheWrite: 0 }, latencyMs: 1 }, event);
  assert.equal(store.getGeneration(ACCOUNT_A.id, id)?.state, "processing");
  store.completeGeneration(ACCOUNT_A.id, id, next.leaseToken!, { output: "새 결과", usage: { input: 2, output: 3, cacheRead: 0, cacheWrite: 0 }, latencyMs: 1 }, event);
  assert.equal(store.getGeneration(ACCOUNT_A.id, id)?.output, "새 결과");
  assert.equal(store.claimOutbox(NOW + 60002)?.event.timestamp, old.generation.createdAt);
});

test("실패한 응답을 나중에 다시 만들어도 처음 요청 시각으로 사용량을 보낸다", async t => {
  const store = new OpenAbStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  let now = Date.parse("2026-10-31T14:59:30Z");
  let calls = 0;
  const provider: GenerationProvider = { async generate(input, context) { calls++; if (calls === 1) throw new Error("provider down"); return fixedProvider().generate(input, context); } };
  const fake = clientFixture();
  const service = new OpenAbService({ config: config(), store, client: fake.client, provider, now: () => now });
  const body = apiBody("월말 요청");
  await assert.rejects(() => service.generate(ACCOUNT_A, body), error => error instanceof ServiceError && error.status === 503);
  now = Date.parse("2026-10-31T15:00:10Z");
  await service.generate(ACCOUNT_A, body);
  assert.deepEqual(fake.events.map(event => event.timestamp), ["2026-10-31T14:59:30.000Z"]);
});

test("MeterEngine이 영구 거절한 사용량은 자동으로 다시 보내지 않고 직접 재전송으로만 보낸다", async t => {
  const store = new OpenAbStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  let now = NOW;
  let calls = 0;
  const fake = clientFixture({ async ingestEvent() { calls++; throw new MeterEngineApiError(409, { code: "customer_archived", message: "보관한 고객입니다" }); } });
  const service = new OpenAbService({ config: config(), store, client: fake.client, now: () => now });
  const id = randomUUID();
  await service.generate(ACCOUNT_A, apiBody("보관된 고객", id));
  assert.equal(calls, 1);
  now = NOW + 60000;
  await service.flushMetering();
  assert.equal(calls, 1);
  assert.equal(service.getRequest(ACCOUNT_A, id).meteringStatus, "failed");
  await service.retryMetering(ACCOUNT_A, { requestId: id });
  assert.equal(calls, 2);
});

test("한 계정의 실패 재전송이 다른 계정의 큐를 처리하지 않는다", async t => {
  const store = new OpenAbStore(":memory:", [ACCOUNT_A, ACCOUNT_B]);
  t.after(() => store.close());
  const fake = clientFixture({ async ingestEvent() { throw new Error("offline"); } });
  let now = NOW;
  const service = new OpenAbService({ config: config(), store, client: fake.client, now: () => now });
  const a = randomUUID();
  const b = randomUUID();
  await service.generate(ACCOUNT_A, apiBody("가람", a));
  await service.generate(ACCOUNT_B, apiBody("누리", b));
  await assert.rejects(() => service.retryMetering(ACCOUNT_A, { requestId: b }), error => error instanceof ServiceError && error.status === 404);
  fake.client.ingestEvent = async event => { fake.events.push({ ...event, transaction_id: event.transaction_id! }); return { transaction_id: event.transaction_id!, duplicate: false }; };
  now = NOW + 6000;
  await service.retryMetering(ACCOUNT_A, {});
  assert.equal(service.getRequest(ACCOUNT_A, a).meteringStatus, "delivered");
  assert.equal(service.getRequest(ACCOUNT_B, b).meteringStatus, "failed");
  assert.deepEqual(fake.events.map(event => event.customer_id), [ACCOUNT_A.customerId]);
});

test("조회 응답에 다른 고객의 사용량, 인보이스, 납부 상태를 섞지 않는다", async t => {
  const store = new OpenAbStore(":memory:", [ACCOUNT_A, ACCOUNT_B]);
  t.after(() => store.close());
  const fake = clientFixture({
    async aggregateBillableMetricUsages() { return { month: "2026-10", billable_metric_usages: [{ ...usageMetrics()[0], customers: [
      { customer_id: ACCOUNT_A.customerId, customer_name: "가람데이터", quantity: 0.00001, raw_quantity: 10, dimension_usages: [{ dimension_values: { model: "ab-5", surface: "chat" }, quantity: 0.00001, raw_quantity: 10 }, { dimension_values: { model: "ab-5" }, quantity: 1, raw_quantity: 1 }] },
      { customer_id: ACCOUNT_B.customerId, customer_name: "다른 고객 비밀", quantity: 999, dimension_usages: [] },
    ] }] }; },
    async previewDraftInvoice() { return { month: "2026-10", calculated_at: "2026-10-26T12:00:00Z", total_amount: 999999, customers: [
      { customer_id: ACCOUNT_A.customerId, customer_name: "가람데이터", amount: 100, lines: [{ billable_metric_code: "llm-cache-read-tokens", target_property: "cache_read_tokens", dimension_values: { model: "ab-5", surface: "batch" }, quantity: 0.25, unit_price: 200, amount: 50, line_type: "per_unit" as const, tier_index: 0 }], unpriced_usages: [], negative_usages: [] },
      { customer_id: ACCOUNT_B.customerId, customer_name: "다른 고객 비밀", amount: 999999, lines: [], unpriced_usages: [], negative_usages: [] },
    ] }; },
    async listInvoices() { return { invoices: [
      { id: "a-invoice", invoice_number: "ME-202610-00000001", customer_id: ACCOUNT_A.customerId, customer_name: "가람데이터", month: "2026-10", finalized_at: "2026-10-26T12:00:00Z", supply_amount: 100, tax_amount: 10, total_amount: 110, lines: [] },
      { id: "b-invoice", invoice_number: "ME-202610-00000002", customer_id: ACCOUNT_B.customerId, customer_name: "다른 고객 비밀", month: "2026-10", finalized_at: "2026-10-26T12:00:00Z", supply_amount: 999999, tax_amount: 0, total_amount: 999999, lines: [] },
    ] }; },
    async listInvoicePayments() { return { invoice_payments: [{ invoice_id: "b-invoice", status: "paid", mode: "test", amount: 999999, canceled_amount: 0, attempt_count: 1, last_failure_code: null, next_retry_at: null, completed_at: null }] }; },
  });
  const service = new OpenAbService({ config: config(), store, client: fake.client, now: () => NOW });
  const result = await service.workspace(ACCOUNT_A, "2026-10");
  assert.equal(result.billing.draft?.totalAmount, 110);
  assert.deepEqual(result.billing.draft?.lines.map(line => [line.label, line.modelId, line.surface]), [["캐시 읽기 토큰", "ab-5", "batch"]]);
  assert.equal(result.billing.invoices.length, 1);
  assert.equal(result.billing.invoices[0].id, "a-invoice");
  assert.equal(result.billing.invoices[0].status, "unavailable");
  assert.equal(result.billing.invoices[0].paidAmount, null);
  assert.equal(result.billing.invoices[0].canceledAmount, null);
  assert.equal(result.billing.invoices[0].amountDue, null);
  assert.deepEqual(result.usage.metrics.map(metric => [metric.code, metric.key, metric.quantity, metric.rawQuantity, metric.breakdown]), [["llm-input-tokens", "input", 0.00001, 10, [{ modelId: "ab-5", surface: "chat", quantity: 0.00001, rawQuantity: 10 }]]]);
  assert.ok(!JSON.stringify(result).includes("다른 고객 비밀"));
  assert.ok(!JSON.stringify(result).includes("999999"));
});

test("부분 조회 실패를 가짜 0원 성공으로 바꾸지 않는다", async t => {
  const store = new OpenAbStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  const fake = clientFixture({
    async aggregateBillableMetricUsages() { throw new Error("secret upstream payload"); },
    async previewDraftInvoice() { throw new Error("secret upstream payload"); },
    async listBillableMetricPrices() { throw new Error("secret upstream payload"); },
  });
  const result = await new OpenAbService({ config: config(), store, client: fake.client, now: () => NOW }).workspace(ACCOUNT_A, "2026-10");
  assert.equal(result.usage.state, "unavailable");
  assert.equal(result.billing.state, "unavailable");
  assert.equal(result.billing.draft, null);
  assert.equal(result.models[0].prices.chat?.input, null);
  assert.equal(result.billing.error, "청구 정보 일부를 불러오지 못했어요: 청구 예정액, 적용 요금. 잠시 뒤 새로 불러와 주세요.");
  assert.ok(!JSON.stringify(result).includes("secret upstream payload"));
});

test("일부 납부 상태가 빠지거나 다른 고객 카드가 오면 청구를 ready로 보이지 않는다", async t => {
  const store = new OpenAbStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  for (const broken of ["payment", "method"] as const) {
    const fake = pricedClientFixture();
    fake.client.listInvoices = async () => ({ invoices: [{ id: "invoice", invoice_number: "ME-202610-00000001", customer_id: ACCOUNT_A.customerId, customer_name: "가람데이터", month: "2026-10", finalized_at: "2026-10-26T12:00:00Z", supply_amount: 100, tax_amount: 10, total_amount: 110, lines: [] }] });
    fake.client.listInvoicePayments = async () => ({ invoice_payments: broken === "payment" ? [] : [{ invoice_id: "invoice", status: "paid", mode: "test", amount: 110, canceled_amount: 0, attempt_count: 1, last_failure_code: null, next_retry_at: null, completed_at: null }] });
    if (broken === "method") fake.client.getPaymentMethod = async () => ({ customer_id: ACCOUNT_B.customerId, card_issuer_code: "61", card_number: "1234****5678", mode: "test", authenticated_at: "2026-10-01T00:00:00Z" });
    const result = await new OpenAbService({ config: config(), store, client: fake.client, now: () => NOW }).workspace(ACCOUNT_A, "2026-10");
    assert.equal(result.billing.state, "unavailable", broken);
    assert.equal(result.billing.error, `청구 정보 일부를 불러오지 못했어요: ${broken === "payment" ? "일부 납부 상태" : "결제 카드"}. 잠시 뒤 새로 불러와 주세요.`, broken);
  }
});

test("모델과 용도마다 100만 토큰 단가를 읽고 일괄 처리 단가를 따로 보여 준다", async t => {
  const store = new OpenAbStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  const fake = pricedClientFixture();
  const result = await new OpenAbService({ config: config(), store, client: fake.client, now: () => NOW }).workspace(ACCOUNT_A, "2026-10");
  assert.deepEqual(fake.queries, [{ month: "2026-10" }, { month: "2026-10", customer_id: ACCOUNT_A.customerId }]);
  const ab5 = result.models.find(model => model.id === "ab-5")!;
  assert.deepEqual(ab5.prices.chat, { input: 4000, output: 20000, cacheRead: 400, cacheWrite: 5000 });
  assert.deepEqual(ab5.prices.batch, { input: 2000, output: 10000, cacheRead: 200, cacheWrite: 2500 });
  const embed = result.models.find(model => model.id === "ab-embed-3")!;
  assert.deepEqual(embed.prices.api, { input: 30, output: null, cacheRead: null, cacheWrite: null });
  assert.equal(embed.prices.chat, undefined);
  assert.equal(result.usage.state, "ready");
  assert.equal(result.billing.state, "ready");
});

test("고객별 단가는 더 최근 공통 단가보다 우선하고 다른 조합은 공통 단가를 쓴다", async t => {
  const store = new OpenAbStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  const fake = pricedClientFixture({ customerOverride: true });
  const result = await new OpenAbService({ config: config(), store, client: fake.client, now: () => NOW }).workspace(ACCOUNT_A, "2026-10");
  const ab5 = result.models.find(model => model.id === "ab-5")!;
  assert.equal(ab5.prices.api?.input, 3500);
  assert.equal(ab5.prices.chat?.input, 4000);
  assert.equal(result.billing.state, "ready");
});

test("공통 단가와 고객별 단가 중 하나라도 조회 실패하면 부분 단가를 확정 가격으로 표시하지 않는다", async t => {
  const store = new OpenAbStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  for (const unavailable of ["common", "customer"] as const) {
    const fake = pricedClientFixture({ customerOverride: true, unavailable });
    const result = await new OpenAbService({ config: config(), store, client: fake.client, now: () => NOW }).workspace(ACCOUNT_A, "2026-10");
    assert.ok(result.models.every(model => Object.values(model.prices).every(prices => Object.values(prices!).every(value => value === null))), unavailable);
    assert.equal(result.billing.state, "unavailable", unavailable);
    assert.equal(result.usage.state, "unavailable", unavailable);
    assert.match(result.usage.error!, /요금표를 불러오지 못해/);
    assert.ok(!JSON.stringify(result).includes("unavailable scoped price query"));
  }
});

test("미터 단위, 집계 방식, 이벤트 유형, 차원이 달라지면 단가를 안내하지 않는다", async t => {
  const store = new OpenAbStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  for (const change of ["none", "unit", "aggregation", "rounding", "dimensions", "eventType"] as const) {
    const fake = pricedClientFixture({ change });
    const result = await new OpenAbService({ config: config(), store, client: fake.client, now: () => NOW }).workspace(ACCOUNT_A, "2026-10");
    const input = result.models.find(model => model.id === "ab-5")!.prices.chat?.input;
    if (change === "none") {
      assert.equal(input, 4000);
      assert.equal(result.usage.state, "ready");
    } else {
      assert.equal(input, null, change);
      assert.equal(result.billing.state, "unavailable", change);
      assert.equal(result.billing.error, "이번 달 요금 설정이 서비스 기준과 맞지 않아 요금을 표시하지 않아요. 서비스 운영자에게 문의해 주세요.", change);
      assert.match(result.usage.error!, /사용량 집계 기준/);
    }
  }
});

test("차원 키 순서가 달라도 같은 계약으로 본다", () => {
  for (const dimensions of [["model", "surface"], ["surface", "model"]]) {
    const usage = { month: "2026-10", billable_metric_usages: usageMetrics() };
    const prices = { billable_metric_prices: CODES.map(code => ({ billable_metric_code: code, dimension_properties: dimensions, price_rates: [] })) };
    assert.ok(CODES.every(code => matchesMetricContract(code, usage, prices)));
  }
});

test("텍스트 모델의 단가가 하나라도 빠지면 요금 설정 확인을 안내한다", async t => {
  const store = new OpenAbStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  const fake = pricedClientFixture();
  const original = fake.client.listBillableMetricPrices;
  fake.client.listBillableMetricPrices = async params => {
    const response = await original(params);
    return { billable_metric_prices: response.billable_metric_prices.map(metric => ({ ...metric, price_rates: metric.price_rates.filter(rate => !(rate.dimension_values.model === "ab-5-nano" && rate.dimension_values.surface === "batch" && metric.billable_metric_code === "llm-cache-write-tokens")) })) };
  };
  const result = await new OpenAbService({ config: config(), store, client: fake.client, now: () => NOW }).workspace(ACCOUNT_A, "2026-10");
  assert.equal(result.billing.state, "unavailable");
  assert.match(result.billing.error!, /요금 설정이 서비스 기준과 맞지 않아/);
});

test("결제 취소는 별도 금액으로 표시하고 다시 결제할 미납액으로 만들지 않는다", async t => {
  const store = new OpenAbStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  for (const canceled of [40, 110]) {
    const fake = clientFixture({
      async listInvoices() { return { invoices: [{ id: "invoice", invoice_number: "ME-202610-00000001", customer_id: ACCOUNT_A.customerId, customer_name: "가람데이터", month: "2026-10", finalized_at: "2026-10-26T12:00:00Z", supply_amount: 100, tax_amount: 10, total_amount: 110, lines: [] }] }; },
      async listInvoicePayments() { return { invoice_payments: [{ invoice_id: "invoice", status: canceled === 110 ? "canceled" : "partially_canceled", mode: "test", amount: 110, canceled_amount: canceled, attempt_count: 1, last_failure_code: null, next_retry_at: null, completed_at: null }] }; },
    });
    const result = await new OpenAbService({ config: config(), store, client: fake.client, now: () => NOW }).workspace(ACCOUNT_A, "2026-10");
    assert.equal(result.billing.invoices[0].amountDue, 0);
    assert.equal(result.billing.invoices[0].canceledAmount, canceled);
    assert.equal(result.billing.invoices[0].paidAmount, 110 - canceled);
  }
});

test("모델 실패와 잘못된 입력은 수집 없이 복구할 수 있다", async t => {
  const store = new OpenAbStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  const fake = clientFixture();
  const service = new OpenAbService({ config: config(), store, client: fake.client, provider: { async generate() { throw new Error("private provider key"); } }, now: () => NOW });
  const body = apiBody("요청");
  await assert.rejects(() => service.generate(ACCOUNT_A, body), error => error instanceof ServiceError && error.code === "generation_failed" && !error.message.includes("private provider key"));
  assert.equal(service.getRequest(ACCOUNT_A, body.requestId).state, "failed");
  assert.equal(fake.events.length, 0);
  await assert.rejects(() => service.generate(ACCOUNT_A, { ...body, modelId: "not-a-model" }), error => error instanceof ServiceError && error.status === 400);
  await assert.rejects(() => service.generate(ACCOUNT_A, { ...body, prompt: "x".repeat(4001) }), error => error instanceof ServiceError && error.status === 400);
  await assert.rejects(() => service.generate(ACCOUNT_A, { ...body, requestId: randomUUID(), system: "x".repeat(4001) }), error => error instanceof ServiceError && error.code === "invalid_system");
});

test("카드 등록은 로그인 고객으로만 만들고 실결제 모드를 반환하지 않는다", async t => {
  const store = new OpenAbStore(":memory:", [ACCOUNT_A]);
  t.after(() => store.close());
  const observed: string[] = [];
  const registrationClient = { async createPaymentMethodRegistration(id: string) { observed.push(id); return { token: "only-local-test-token", customer_id: id, customer_name: "가람데이터", organization_name: "OpenAB", client_key: "not-exposed", customer_key: "not-exposed", mode: "test" as const, expires_at: "2026-10-27T12:00:00Z" }; } };
  const service = new OpenAbService({ config: config(), store, client: clientFixture().client, registrationClient, now: () => NOW });
  const result = await service.registerPaymentMethod(ACCOUNT_A);
  assert.deepEqual(observed, [ACCOUNT_A.customerId]);
  assert.equal(result.url, "http://localhost:33147/payment-method-registrations/only-local-test-token");
  assert.ok(!JSON.stringify(result).includes("not-exposed"));
  const live = new OpenAbService({ config: config(), store, client: clientFixture().client, registrationClient: { async createPaymentMethodRegistration(id) { return { ...(await registrationClient.createPaymentMethodRegistration(id)), mode: "live" }; } }, now: () => NOW });
  await assert.rejects(() => live.registerPaymentMethod(ACCOUNT_A), error => error instanceof ServiceError && error.status === 503);
});

test("월 경계는 한국 시간 기준이며 잘못된 월은 거절한다", () => {
  assert.deepEqual(monthRange("2026-10"), { start: "2026-09-30T15:00:00.000Z", end: "2026-10-31T15:00:00.000Z" });
  assert.deepEqual(monthRange("2026-12"), { start: "2026-11-30T15:00:00.000Z", end: "2026-12-31T15:00:00.000Z" });
  assert.throws(() => monthRange("0000-01"));
  assert.throws(() => monthRange("2026-13"));
});

test("설정의 계정과 고객 중복, 잘못된 주소는 거절하고 기본 DB 파일은 회사 ID를 쓴다", () => {
  assert.equal(readConfig(Object.fromEntries([])).isConfigured, false);
  assert.equal(path.basename(readConfig({}).databasePath), "openab.sqlite");
  assert.throws(() => readConfig(Object.fromEntries([["OPENAB_ACCOUNTS", JSON.stringify([ACCOUNT_A, ACCOUNT_A])]])));
  assert.throws(() => readConfig(Object.fromEntries([["OPENAB_APP_ORIGIN", "https://secret:password@example.test"]])));
  assert.equal(readConfig(Object.fromEntries([["OPENAB_ACCOUNTS", JSON.stringify([ACCOUNT_A])], ["METERENGINE_API_KEY", "k"]])).isConfigured, true);
});
