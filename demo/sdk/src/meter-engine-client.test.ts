import assert from "node:assert/strict";
import { test } from "node:test";
import {
  MeterEngineApiError,
  MeterEngineBatchError,
  MeterEngineClient,
  MeterEngineConnectionError,
  MeterEngineTimeoutError,
} from "./index.ts";
import type { IngestEventInput, IngestEventRequest, InvoiceLineResponse, MeterEngineClientOptions } from "./index.ts";

const BASE_URL = "http://localhost:8080";
const API_KEY = "me_live_0123456789abcdefghijABCDEFGHIJKL";
const CUSTOMER_ID = "0f8fad5b-d9cb-469f-a165-70867728950e";
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

type RecordedCall = { url: string; method: string; headers: Headers; body: unknown };
type Reply = Response | Error | ((init: RequestInit) => Promise<Response>);

function fakeFetch(replies: Reply[]) {
  const calls: RecordedCall[] = [];
  const fetchImpl = async (input: string | URL | Request, init: RequestInit = {}): Promise<Response> => {
    calls.push({
      url: String(input),
      method: init.method ?? "GET",
      headers: new Headers(init.headers),
      body: typeof init.body === "string" ? JSON.parse(init.body) : undefined,
    });
    const reply = replies.shift();
    if (reply === undefined) {
      throw new Error("준비한 응답보다 호출이 많다");
    }
    if (reply instanceof Error) {
      throw reply;
    }
    if (typeof reply === "function") {
      return reply(init);
    }
    return reply;
  };
  return { calls, fetch: fetchImpl as typeof fetch };
}

function json(status: number, body: unknown, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...headers },
  });
}

function apiError(status: number, code: string): Response {
  return json(status, { code, message: "오류" });
}

function waitForAbort(init: RequestInit): Promise<Response> {
  return new Promise((_resolve, reject) => {
    const keepAlive = setTimeout(() => reject(new Error("abort가 오지 않았다")), 5000);
    init.signal?.addEventListener(
      "abort",
      () => {
        clearTimeout(keepAlive);
        reject(init.signal?.reason);
      },
      { once: true },
    );
  });
}

async function storedBatchReply(init: RequestInit): Promise<Response> {
  const { events } = JSON.parse(String(init.body)) as { events: IngestEventRequest[] };
  return json(200, {
    results: events.map((event, index) => ({ index, transaction_id: event.transaction_id, result: "stored" })),
  });
}

function event(n: number): IngestEventInput {
  return {
    transaction_id: `tx-${n}`,
    customer_id: CUSTOMER_ID,
    type: "api_call",
    timestamp: "2026-10-26T09:00:00+09:00",
    properties: { token: 1 },
  };
}

function eventWithoutTransactionId(n: number): IngestEventInput {
  const input = event(n);
  delete input.transaction_id;
  return input;
}

function events(count: number): IngestEventInput[] {
  return Array.from({ length: count }, (_value, n) => event(n));
}

function createClient(replies: Reply[], options: Partial<MeterEngineClientOptions> = {}) {
  const fake = fakeFetch(replies);
  const sleeps: number[] = [];
  const client = new MeterEngineClient({
    baseUrl: BASE_URL,
    apiKey: API_KEY,
    fetch: fake.fetch,
    sleep: async (ms) => {
      sleeps.push(ms);
    },
    random: () => 0,
    ...options,
  });
  return { client, calls: fake.calls, sleeps };
}

test("ingestEvent는 Bearer 헤더와 JSON 본문으로 POST /v1/events를 부른다", async () => {
  const { client, calls } = createClient([json(200, { transaction_id: "tx-1", duplicate: false })]);

  const response = await client.ingestEvent(event(1));

  assert.deepEqual(response, { transaction_id: "tx-1", duplicate: false });
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, `${BASE_URL}/v1/events`);
  assert.equal(calls[0].method, "POST");
  assert.equal(calls[0].headers.get("authorization"), `Bearer ${API_KEY}`);
  assert.equal(calls[0].headers.get("accept"), "application/json");
  assert.equal(calls[0].headers.get("content-type"), "application/json");
  assert.deepEqual(calls[0].body, event(1));
});

test("transaction_id가 없으면 하나 만들어 재시도에도 같은 값을 쓴다", async () => {
  const { client, calls } = createClient([apiError(503, "internal_server_error"), json(200, { transaction_id: "x", duplicate: false })]);
  await client.ingestEvent(eventWithoutTransactionId(1));

  assert.equal(calls.length, 2);
  const first = calls[0].body as IngestEventRequest;
  const second = calls[1].body as IngestEventRequest;
  assert.match(first.transaction_id, UUID_PATTERN);
  assert.equal(second.transaction_id, first.transaction_id);
});

test("빈 transaction_id는 그대로 보낸다", async () => {
  const { client, calls } = createClient([apiError(400, "validation_error")]);

  await assert.rejects(client.ingestEvent({ ...event(1), transaction_id: "" }), MeterEngineApiError);

  assert.equal((calls[0].body as IngestEventRequest).transaction_id, "");
});

test("음수 수량과 미래 시각은 바꾸지 않고 보낸다", async () => {
  const { client, calls } = createClient([json(200, { transaction_id: "tx-1", duplicate: false })]);
  const request = { ...event(1), timestamp: "2099-01-01T00:00:00+09:00", properties: { token: -5 } };

  await client.ingestEvent(request);

  assert.deepEqual(calls[0].body, request);
});

for (const status of [400, 401, 403, 404]) {
  test(`${status}는 재시도하지 않는다`, async () => {
    const { client, calls, sleeps } = createClient([apiError(status, "customer_not_found")]);

    await assert.rejects(client.ingestEvent(event(1)), (error) => {
      assert.ok(error instanceof MeterEngineApiError);
      assert.equal(error.status, status);
      assert.equal(error.code, "customer_not_found");
      return true;
    });
    assert.equal(calls.length, 1);
    assert.deepEqual(sleeps, []);
  });
}

test("400 응답의 errors를 오류에 담는다", async () => {
  const body = { code: "validation_error", message: "요청 값이 올바르지 않습니다", errors: [{ field: "type", message: "공백일 수 없습니다" }] };
  const { client } = createClient([json(400, body)]);

  await assert.rejects(client.ingestEvent(event(1)), (error) => {
    assert.ok(error instanceof MeterEngineApiError);
    assert.equal(error.message, body.message);
    assert.deepEqual(error.errors, body.errors);
    return true;
  });
});

test("JSON이 아닌 오류 응답은 code가 http_error다", async () => {
  const { client } = createClient([new Response("<html>bad gateway</html>", { status: 502 })], { maxRetries: 0 });

  await assert.rejects(client.ingestEvent(event(1)), (error) => {
    assert.ok(error instanceof MeterEngineApiError);
    assert.equal(error.status, 502);
    assert.equal(error.code, "http_error");
    return true;
  });
});

test("429는 Retry-After 초만큼 기다린 뒤 다시 보낸다", async () => {
  const { client, calls, sleeps } = createClient([
    json(429, { code: "too_many_requests", message: "잠시 뒤 다시 보내세요" }, { "Retry-After": "2" }),
    json(200, { transaction_id: "tx-1", duplicate: false }),
  ]);

  const response = await client.ingestEvent(event(1));

  assert.deepEqual(response, { transaction_id: "tx-1", duplicate: false });
  assert.deepEqual(sleeps, [2000]);
  assert.equal(calls.length, 2);
});

test("5xx가 계속되면 maxRetries 뒤 마지막 오류를 던진다", async () => {
  const { client, calls, sleeps } = createClient([
    apiError(503, "internal_server_error"),
    apiError(503, "internal_server_error"),
    apiError(503, "internal_server_error"),
    json(503, { code: "request_timed_out", message: "마지막" }),
  ]);

  await assert.rejects(client.ingestEvent(event(1)), (error) => {
    assert.ok(error instanceof MeterEngineApiError);
    assert.equal(error.code, "request_timed_out");
    return true;
  });
  assert.equal(calls.length, 4);
  assert.equal(sleeps.length, 3);
});

test("재시도 대기는 시도 횟수에 따라 full jitter로 늘어난다", async () => {
  const { client, sleeps } = createClient(
    [apiError(500, "internal_server_error"), apiError(500, "internal_server_error"), apiError(500, "internal_server_error"), apiError(500, "internal_server_error")],
    { random: () => 1 },
  );

  await assert.rejects(client.ingestEvent(event(1)), MeterEngineApiError);

  assert.deepEqual(sleeps, [200, 400, 800]);
});

test("연결 오류는 재시도 뒤 MeterEngineConnectionError", async () => {
  const cause = new TypeError("fetch failed");
  const { client, calls } = createClient([cause, cause, cause, cause]);

  await assert.rejects(client.ingestEvent(event(1)), (error) => {
    assert.ok(error instanceof MeterEngineConnectionError);
    assert.equal(error.cause, cause);
    return true;
  });
  assert.equal(calls.length, 4);
});

test("응답이 timeoutMs 안에 없으면 MeterEngineTimeoutError", async () => {
  const { client, calls } = createClient([waitForAbort], { timeoutMs: 20, maxRetries: 0 });

  await assert.rejects(client.ingestEvent(event(1)), (error) => {
    assert.ok(error instanceof MeterEngineTimeoutError);
    assert.equal(error.timeoutMs, 20);
    return true;
  });
  assert.equal(calls.length, 1);
});

test("타임아웃은 재시도한다", async () => {
  const { client, calls } = createClient([waitForAbort, json(200, { transaction_id: "tx-1", duplicate: false })], { timeoutMs: 20 });

  const response = await client.ingestEvent(event(1));

  assert.equal(response.transaction_id, "tx-1");
  assert.equal(calls.length, 2);
});

test("호출자가 취소하면 재시도하지 않는다", async () => {
  const { client, calls, sleeps } = createClient([apiError(503, "internal_server_error"), apiError(503, "internal_server_error")]);
  const controller = new AbortController();
  controller.abort();

  await assert.rejects(client.ingestEvent(event(1), { signal: controller.signal }), { name: "AbortError" });

  assert.ok(calls.length <= 1);
  assert.deepEqual(sleeps, []);
});

test("요청 중에 호출자가 취소하면 타임아웃이 아닌 abort 오류를 바로 던진다", async () => {
  const { client, calls, sleeps } = createClient([waitForAbort, json(200, { transaction_id: "tx-1", duplicate: false })]);
  const controller = new AbortController();

  const pending = client.ingestEvent(event(1), { signal: controller.signal });
  setTimeout(() => controller.abort(), 5);

  await assert.rejects(pending, (error) => {
    assert.ok(!(error instanceof MeterEngineTimeoutError));
    assert.equal((error as Error).name, "AbortError");
    return true;
  });
  assert.equal(calls.length, 1);
  assert.deepEqual(sleeps, []);
});

test("재시도를 기다리는 중에 호출자가 취소하면 기다림이 끝나기 전에 abort 오류를 던진다", { timeout: 1000 }, async () => {
  const { client, calls } = createClient(
    [json(429, { code: "too_many_requests", message: "잠시 뒤 다시 보내세요" }, { "Retry-After": "2" })],
    { sleep: () => new Promise<void>(() => {}) },
  );
  const controller = new AbortController();

  const pending = client.ingestEvent(event(1), { signal: controller.signal });
  setTimeout(() => controller.abort(), 5);

  await assert.rejects(pending, (error) => {
    assert.equal(error, controller.signal.reason);
    assert.equal((error as Error).name, "AbortError");
    return true;
  });
  assert.equal(calls.length, 1);
});

test("5xx 응답을 받은 뒤 호출자가 취소했으면 API 오류가 아닌 abort 오류를 던진다", async () => {
  const controller = new AbortController();
  const { client, calls, sleeps } = createClient([
    async () => {
      controller.abort();
      return apiError(503, "internal_server_error");
    },
  ]);

  await assert.rejects(client.ingestEvent(event(1), { signal: controller.signal }), (error) => {
    assert.ok(!(error instanceof MeterEngineApiError));
    assert.equal((error as Error).name, "AbortError");
    return true;
  });
  assert.equal(calls.length, 1);
  assert.deepEqual(sleeps, []);
});

test("101건은 100건과 1건 두 묶음으로 나눠 POST /v1/event-batches로 보낸다", async () => {
  const { client, calls } = createClient([storedBatchReply, storedBatchReply]);

  const response = await client.ingestEventBatch(events(101));

  assert.equal(calls.length, 2);
  for (const call of calls) {
    assert.equal(call.url, `${BASE_URL}/v1/event-batches`);
    assert.equal(call.method, "POST");
    assert.equal(call.headers.get("authorization"), `Bearer ${API_KEY}`);
  }
  assert.equal((calls[0].body as { events: unknown[] }).events.length, 100);
  assert.deepEqual((calls[1].body as { events: unknown[] }).events, [event(100)]);
  assert.deepEqual(
    response.results.map((result) => result.index),
    Array.from({ length: 101 }, (_value, n) => n),
  );
  assert.equal(response.results[100].index, 100);
  assert.equal(response.results[100].transaction_id, "tx-100");
});

test("100건은 한 묶음이다", async () => {
  const { client, calls } = createClient([storedBatchReply]);

  const response = await client.ingestEventBatch(events(100));

  assert.equal(calls.length, 1);
  assert.equal(response.results.length, 100);
});

test("200건은 두 묶음이다", async () => {
  const { client, calls } = createClient([storedBatchReply, storedBatchReply]);

  const response = await client.ingestEventBatch(events(200));

  assert.equal(calls.length, 2);
  assert.equal((calls[1].body as { events: unknown[] }).events.length, 100);
  assert.equal(response.results.length, 200);
  assert.equal(response.results[199].index, 199);
  assert.equal(response.results[199].transaction_id, "tx-199");
});

test("둘째 묶음이 실패하면 첫 묶음 결과를 담은 MeterEngineBatchError", async () => {
  const { client, calls } = createClient([storedBatchReply, apiError(400, "validation_error")]);

  await assert.rejects(client.ingestEventBatch(events(150)), (error) => {
    assert.ok(error instanceof MeterEngineBatchError);
    assert.equal(error.results.length, 100);
    assert.equal(error.results[99].transaction_id, "tx-99");
    assert.ok(error.cause instanceof MeterEngineApiError);
    assert.equal(error.cause.status, 400);
    return true;
  });
  assert.equal(calls.length, 2);
});

test("둘째 묶음이 재시도 끝에 5xx로 실패해도 첫 묶음 결과를 담은 MeterEngineBatchError", async () => {
  const { client, calls } = createClient([
    storedBatchReply,
    apiError(503, "internal_server_error"),
    apiError(503, "internal_server_error"),
    apiError(503, "internal_server_error"),
    apiError(503, "internal_server_error"),
  ]);

  await assert.rejects(client.ingestEventBatch(events(150)), (error) => {
    assert.ok(error instanceof MeterEngineBatchError);
    assert.equal(error.results.length, 100);
    assert.deepEqual(
      error.results.map((result) => result.index),
      Array.from({ length: 100 }, (_value, n) => n),
    );
    assert.ok(error.cause instanceof MeterEngineApiError);
    assert.equal(error.cause.status, 503);
    return true;
  });
  assert.equal(calls.length, 5);
});

test("duplicate와 rejected 결과를 그대로 돌려준다", async () => {
  const results = [
    { index: 0, transaction_id: "tx-0", result: "duplicate" },
    {
      index: 1,
      transaction_id: "tx-1",
      result: "rejected",
      error: { code: "validation_error", message: "요청 값이 올바르지 않습니다", errors: [{ field: "type", message: "공백일 수 없습니다" }] },
    },
  ];
  const { client } = createClient([json(200, { results })]);

  const response = await client.ingestEventBatch(events(2));

  assert.deepEqual(response, { results });
});

test("배치도 transaction_id가 없을 때만 만들고 빈 값은 그대로 보낸다", async () => {
  const { client, calls } = createClient([storedBatchReply]);
  await client.ingestEventBatch([eventWithoutTransactionId(0), { ...event(1), transaction_id: "" }]);

  const sent = (calls[0].body as { events: IngestEventRequest[] }).events;
  assert.match(sent[0].transaction_id, UUID_PATTERN);
  assert.equal(sent[1].transaction_id, "");
});

test("빈 배열은 호출하지 않고 빈 결과를 돌려준다", async () => {
  const { client, calls } = createClient([]);

  const response = await client.ingestEventBatch([]);

  assert.deepEqual(response, { results: [] });
  assert.equal(calls.length, 0);
});

test("listEvents는 준 params만 쿼리로 붙인다", async () => {
  const body = { events: [], month: "2026-10", page: 0, size: 1, total: 0 };
  const { client, calls } = createClient([json(200, body)]);

  const response = await client.listEvents({ customer_id: CUSTOMER_ID, month: "2026-10", size: 1, type: undefined });

  assert.deepEqual(response, body);
  const url = new URL(calls[0].url);
  assert.equal(`${url.origin}${url.pathname}`, `${BASE_URL}/v1/events`);
  assert.deepEqual(Object.fromEntries(url.searchParams), { customer_id: CUSTOMER_ID, month: "2026-10", size: "1" });
  assert.equal(calls[0].method, "GET");
  assert.equal(calls[0].headers.get("authorization"), `Bearer ${API_KEY}`);
  assert.equal(calls[0].headers.get("content-type"), null);
  assert.equal(calls[0].body, undefined);
});

test("listEvents에 params가 없으면 쿼리를 붙이지 않는다", async () => {
  const { client, calls } = createClient([json(200, { events: [], month: "2026-10", page: 0, size: 20, total: 0 })]);

  await client.listEvents();

  assert.equal(calls[0].url, `${BASE_URL}/v1/events`);
});

test("listCustomers는 GET /v1/customers", async () => {
  const body = { customers: [{ id: CUSTOMER_ID, name: "가나다", billing_email: null, created_at: "2026-10-01T00:00:00Z" }] };
  const { client, calls } = createClient([json(200, body)]);

  const response = await client.listCustomers();

  assert.deepEqual(response, body);
  assert.equal(calls[0].url, `${BASE_URL}/v1/customers`);
  assert.equal(calls[0].method, "GET");
});

test("조회도 5xx면 재시도한다", async () => {
  const { client, calls } = createClient([apiError(503, "internal_server_error"), json(200, { customers: [] })]);

  const response = await client.listCustomers();

  assert.deepEqual(response, { customers: [] });
  assert.equal(calls.length, 2);
});

test("baseUrl 끝의 슬래시를 두 번 붙이지 않는다", async () => {
  const { client, calls } = createClient([json(200, { customers: [] })], { baseUrl: `${BASE_URL}/` });

  await client.listCustomers();

  assert.equal(calls[0].url, `${BASE_URL}/v1/customers`);
});

test("apiKey에 줄바꿈이 있으면 키를 메시지에 넣지 않고 만들 때 던진다", () => {
  assert.throws(
    () => new MeterEngineClient({ baseUrl: BASE_URL, apiKey: "me_live_SECRET\n" }),
    (error: unknown) => error instanceof Error && !error.message.includes("SECRET"),
  );
});

for (const [count, failedStart, size] of [[201, 100, 100], [205, 200, 5], [101, 0, 100]]) {
  test(`${count}건 실패 묶음의 시작 ${failedStart}과 크기 ${size}를 보존한다`, async () => {
    const replies: Reply[] = Array.from({ length: failedStart / 100 }, () => storedBatchReply);
    replies.push(new TypeError("connection lost"));
    const { client, calls } = createClient(replies, { maxRetries: 0 });
    await assert.rejects(client.ingestEventBatch(events(count)), (error) => {
      assert.ok(error instanceof MeterEngineBatchError);
      assert.equal(error.results.length, failedStart);
      assert.deepEqual(error.failedBatch, { startIndex: failedStart, size });
      return true;
    });
    assert.equal(calls.length, failedStart / 100 + 1);
    assert.equal((calls.at(-1)?.body as { events: unknown[] }).events.length, size);
  });
}

test("앞 묶음 응답에 결과가 빠져도 실패 범위를 결과 수로 계산하지 않는다", async () => {
  const { client } = createClient([json(200, { results: [] }), new TypeError("connection lost")], { maxRetries: 0 });
  await assert.rejects(client.ingestEventBatch(events(201)), (error) => {
    assert.ok(error instanceof MeterEngineBatchError);
    assert.deepEqual(error.results, []);
    assert.deepEqual(error.failedBatch, { startIndex: 100, size: 100 });
    return true;
  });
});

test("배치 재시도에도 생성한 ID와 본문을 유지하고 다음 묶음은 1건만 보낸다", async () => {
  const { client, calls } = createClient([apiError(503, "internal_server_error"), storedBatchReply, storedBatchReply]);
  const response = await client.ingestEventBatch(Array.from({ length: 101 }, (_value, index) => eventWithoutTransactionId(index)));
  const first = (calls[0].body as { events: IngestEventRequest[] }).events;
  assert.equal(first.length, 100);
  assert.ok(first.every((event) => UUID_PATTERN.test(event.transaction_id)));
  assert.deepEqual(calls[1].body, calls[0].body);
  assert.equal((calls[2].body as { events: unknown[] }).events.length, 1);
  assert.equal(new Set(response.results.map((result) => result.transaction_id)).size, 101);
});

test("invoice line types allow a base fee line without a meter and only the server's line types", () => {
  const baseFee: InvoiceLineResponse = { billable_metric_code: null, target_property: null, dimension_values: {}, quantity: 1, unit_price: 9990, amount: 9990, line_type: "base_fee", tier_index: 0 };
  // @ts-expect-error 서버가 내지 않는 라인 종류는 타입에서 막는다
  const unknownLineType: InvoiceLineResponse["line_type"] = "discount";
  assert.equal(baseFee.billable_metric_code, null);
  assert.equal(unknownLineType, "discount");
});
