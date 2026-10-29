import assert from "node:assert/strict";
import { test } from "node:test";
import { MeterEngineClient } from "./index.ts";

const API_KEY = "me_test_0123456789abcdefghijABCDEFGHIJKL";
const CUSTOMER_ID = "4a853fc7-e36b-44b1-abd5-7b6991df2f32";

test("서버 응답이 유실된 카드 등록 링크를 자동으로 다시 만들지 않는다", async () => {
  let calls = 0;
  const client = new MeterEngineClient({ baseUrl: "http://localhost:8080", apiKey: API_KEY, maxRetries: 3, sleep: async () => {}, fetch: async () => {
    calls += 1;
    throw new TypeError("connection lost");
  } });
  await assert.rejects(client.createPaymentMethodRegistration(CUSTOMER_ID));
  assert.equal(calls, 1);
});

test("웹훅 엔드포인트 목록은 보관 상태를 query로 보내고 archived_at을 돌려준다", async () => {
  const urls: string[] = [];
  const client = new MeterEngineClient({ baseUrl: "http://localhost:8080", apiKey: API_KEY, fetch: async (input) => {
    urls.push(String(input));
    return new Response(JSON.stringify({ endpoints: [{ id: "c0b1a2d3-0000-4000-8000-000000000001", url: "https://example.com/hook", event_types: ["invoice.finalized"], enabled: false, description: null, created_at: "2026-10-01T00:00:00Z", archived_at: "2026-10-02T00:00:00Z" }] }), { status: 200 });
  } });
  const response = await client.listWebhookEndpoints({ status: "archived" });
  assert.equal(new URL(urls[0]).pathname, "/v1/webhook-endpoints");
  assert.equal(new URL(urls[0]).searchParams.get("status"), "archived");
  assert.equal(response.endpoints[0].archived_at, "2026-10-02T00:00:00Z");
});
