import assert from "node:assert/strict";
import { test } from "node:test";
import { defaultModelId, METRICS, MODELS, SURFACES } from "./catalog.ts";
import { usageEvent, usageProperties } from "./metering-event.ts";
import { apiUsage, CHAT_SYSTEM_TOKENS, chatUsage, estimateTokens, LocalDemoProvider, PROMPT_CACHE_MIN_TOKENS } from "./provider.ts";

test("한글은 글자마다, 나머지는 네 글자마다 1토큰으로 어림하고 공백은 세지 않는다", () => {
  assert.equal(estimateTokens("안녕하세요"), 5);
  assert.equal(estimateTokens("abcd efgh"), 2);
  assert.equal(estimateTokens("AB-5 요약"), 3);
  assert.equal(estimateTokens(""), 0);
});

test("대화의 세 입력 값을 더하면 서비스 지시, 앞선 차례, 새 메시지를 합한 요청 전체가 된다", () => {
  const history = [{ modelId: "ab-5", prompt: "첫 질문", output: "첫 답변입니다" }, { modelId: "ab-5", prompt: "둘째 질문", output: "둘째 답변" }];
  const usage = chatUsage(history, "ab-5", "셋째 질문", "셋째 답변");
  const turns = history.map(turn => estimateTokens(turn.prompt) + estimateTokens(turn.output));
  assert.deepEqual(usage, { input: 4, output: 4, cacheRead: CHAT_SYSTEM_TOKENS + turns[0], cacheWrite: turns[1] });
  assert.equal(usage.cacheRead + usage.cacheWrite + usage.input, CHAT_SYSTEM_TOKENS + turns[0] + turns[1] + 4);
  assert.deepEqual(chatUsage([], "ab-5", "처음", "답"), { input: 2, output: 1, cacheRead: CHAT_SYSTEM_TOKENS, cacheWrite: 0 });
});

test("짧은 시스템 지시는 입력으로 세고 긴 지시만 캐시로 센다", () => {
  assert.deepEqual(apiUsage("짧은 지시", "질문", "답", false), { input: 6, output: 1, cacheRead: 0, cacheWrite: 0 });
  const long = "가".repeat(PROMPT_CACHE_MIN_TOKENS);
  assert.deepEqual(apiUsage(long, "질문", "답", false), { input: 2, output: 1, cacheRead: 0, cacheWrite: PROMPT_CACHE_MIN_TOKENS });
  assert.deepEqual(apiUsage(long, "질문", "답", true), { input: 2, output: 1, cacheRead: PROMPT_CACHE_MIN_TOKENS, cacheWrite: 0 });
});

test("로컬 모형은 같은 입력에 같은 응답과 응답 시간을 돌려준다", async () => {
  const provider = new LocalDemoProvider();
  const input = { surface: "batch" as const, modelId: "ab-5-nano", prompt: "배송 문의를 분류해 줘" };
  const first = await provider.generate(input);
  assert.deepEqual(await provider.generate(input), first);
  assert.match(first.output, /^분류: /);
  assert.deepEqual([first.usage.cacheRead, first.usage.cacheWrite], [0, 0]);
  assert.ok(first.latencyMs > 0);
  await assert.rejects(() => provider.generate({ ...input, modelId: "unknown" }));
});

test("화면마다 처음 고르는 모델은 그 화면에서 쓸 수 있고, 글을 쓰는 모델에는 응답 방식이 있다", () => {
  for (const surface of SURFACES) {
    assert.ok(MODELS.find(model => model.id === defaultModelId(surface.id))?.surfaces.includes(surface.id), surface.id);
  }
  for (const model of MODELS) assert.equal(model.reply === "none", model.kind === "embedding", model.id);
});

test("이벤트 속성은 모델, 용도, 토큰 넷, 응답 시간 일곱 개다", () => {
  const usage = { input: 1, output: 2, cacheRead: 3, cacheWrite: 4 };
  assert.deepEqual(usageProperties({ modelId: "ab-5", surface: "chat", usage, latencyMs: 5 }), { model: "ab-5", surface: "chat", input_tokens: 1, output_tokens: 2, cache_read_tokens: 3, cache_write_tokens: 4, latency_ms: 5 });
  const event = usageEvent({ requestId: "0F8FAD5B-D9CB-469F-A165-70867728950E", customerId: "c", occurredAt: "2026-11-01T00:00:00.000Z", modelId: "ab-5", surface: "chat", usage, latencyMs: 5 });
  assert.equal(event.transaction_id, "req_0f8fad5bd9cb469fa16570867728950e");
  assert.equal(event.type, "llm_request");
  assert.deepEqual(METRICS.map(metric => metric.property).every(property => property in event.properties), true);
});
