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
