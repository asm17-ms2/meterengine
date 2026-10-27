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

test("이벤트 진단과 수집 거절은 영부터 시작하는 페이지와 사유 필터를 구분한다", async () => {
  const urls: URL[] = [];
  const client = new MeterEngineClient({ baseUrl: "http://localhost:8080", apiKey: API_KEY, fetch: async input => {
    const url = new URL(String(input)); urls.push(url);
    return Response.json(url.pathname.includes("event-issues") ? { issues: [], page: 0, total: 0 } : { rejections: [], page: 0, total: 0 });
  } });
  const issues = await client.searchEventIssues({ month: "2026-10", reason: "unpriced_usage", page: 0 });
  const rejections = await client.searchEventRejections({ month: "2026-10", error_code: "customer_not_found", page: 0 });
  assert.equal(issues.page, 0);
  assert.equal(rejections.page, 0);
  assert.equal(urls[0]?.pathname, "/v1/event-issues/search");
  assert.equal(urls[0]?.searchParams.get("reason"), "unpriced_usage");
  assert.equal(urls[0]?.searchParams.get("page"), "0");
  assert.equal(urls[1]?.pathname, "/v1/event-rejections/search");
  assert.equal(urls[1]?.searchParams.get("error_code"), "customer_not_found");
  assert.equal(urls[1]?.searchParams.get("reason"), null);
});
