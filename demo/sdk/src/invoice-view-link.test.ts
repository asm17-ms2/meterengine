import assert from "node:assert/strict";
import { test } from "node:test";
import { MeterEngineClient } from "./index.ts";

const API_KEY = "me_test_0123456789abcdefghijABCDEFGHIJKL";

test("고객 조회 링크는 서버 키를 헤더로 보내고 목록과 폐기에서 원문을 요구하지 않는다", async () => {
  const calls: Array<{ url: URL; method: string | undefined; headers: Headers; body: unknown }> = [];
  const metadata = { id: "link-id", mode: "test", allow_payment_retry: false, created_at: "2026-10-27T00:00:00Z", expires_at: "2026-11-26T00:00:00Z", revoked_at: null };
  const client = new MeterEngineClient({ baseUrl: "http://localhost:8080", apiKey: API_KEY, fetch: async (input, init) => {
    const url = new URL(String(input));
    calls.push({ url, method: init?.method, headers: new Headers(init?.headers), body: init?.body === undefined ? undefined : JSON.parse(String(init.body)) });
    if (init?.method === "GET") return Response.json({ invoice_view_links: [metadata] });
    if (url.pathname.endsWith("/revoke")) return Response.json({ ...metadata, revoked_at: "2026-10-27T01:00:00Z" });
    return Response.json({ ...metadata, token: "one-time-secret" }, { status: 201 });
  } });
  const created = await client.createInvoiceViewLink("invoice/id", { expires_in_days: 7 });
  const listed = await client.listInvoiceViewLinks("invoice/id");
  const revoked = await client.revokeInvoiceViewLink("invoice/id", created.id);
  assert.equal(created.token, "one-time-secret");
  assert.equal(created.allow_payment_retry, false);
  assert.equal(listed.invoice_view_links[0]?.id, created.id);
  assert.equal(Object.hasOwn(listed.invoice_view_links[0]!, "token"), false);
  assert.equal(revoked.revoked_at, "2026-10-27T01:00:00Z");
  assert.equal(calls[0]?.url.pathname, "/v1/invoices/invoice%2Fid/view-links");
  assert.deepEqual(calls[0]?.body, { expires_in_days: 7 });
  assert.equal(calls[2]?.url.pathname, "/v1/invoices/invoice%2Fid/view-links/link-id/revoke");
  for (const call of calls) {
    assert.equal(call.headers.get("Authorization"), `Bearer ${API_KEY}`);
    assert.equal(call.url.href.includes(API_KEY), false);
    assert.equal(call.url.href.includes(created.token), false);
  }
});

test("고객 재시도 권한은 발급 요청의 명시적 선택을 그대로 보내며 기본 요청에는 추가하지 않는다", async () => {
  const requests: unknown[] = [];
  const client = new MeterEngineClient({ baseUrl: "http://localhost:8080", apiKey: API_KEY, fetch: async (_input, init) => {
    const body = JSON.parse(String(init?.body));
    requests.push(body);
    return Response.json({ token: "issued-once", allow_payment_retry: body.allow_payment_retry === true });
  } });
  assert.equal((await client.createInvoiceViewLink("invoice-id")).allow_payment_retry, false);
  assert.equal((await client.createInvoiceViewLink("invoice-id", { allow_payment_retry: true })).allow_payment_retry, true);
  assert.deepEqual(requests, [{}, { allow_payment_retry: true }]);
});

test("응답이 유실된 조회 링크를 자동 재발급하지 않는다", async () => {
  let calls = 0;
  const client = new MeterEngineClient({ baseUrl: "http://localhost:8080", apiKey: API_KEY, maxRetries: 3, sleep: async () => {}, fetch: async () => {
    calls += 1;
    throw new TypeError("response lost");
  } });
  await assert.rejects(client.createInvoiceViewLink("invoice-id"));
  assert.equal(calls, 1);
});

test("폐기는 같은 링크 ID로 재시도하고 원문 토큰을 보내지 않는다", async () => {
  const requests: string[] = [];
  const client = new MeterEngineClient({ baseUrl: "http://localhost:8080", apiKey: API_KEY, maxRetries: 1, sleep: async () => {}, fetch: async input => {
    requests.push(String(input));
    if (requests.length === 1) throw new TypeError("response lost");
    return Response.json({ id: "link-id", revoked_at: "2026-10-27T00:00:00Z" });
  } });
  const result = await client.revokeInvoiceViewLink("invoice-id", "link-id");
  assert.equal(result.id, "link-id");
  assert.equal(requests.length, 2);
  assert.equal(requests[0], requests[1]);
});
