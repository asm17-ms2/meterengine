import assert from "node:assert/strict";
import { test } from "node:test";

import { signWebhookPayload } from "@meterengine/sdk";

import { isWebhookSecretUsable, parseWebhookReceiptLine, receiveWebhook, type WebhookReceipt } from "./webhook-receipt.ts";

const SECRET = "whsec_MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw";
const ID = "msg_p5jXN8AQM9LWM0D4loKWxJek";
const NOW = new Date("2026-10-26T03:00:00.000Z");
const NOW_SECONDS = Math.floor(NOW.getTime() / 1000);
const TEST_PAYLOAD = '{"type":"webhook_test","timestamp":"2026-10-26T03:00:00Z","data":{}}';
const SPACED_PAYLOAD = '{"type": "webhook_test",  "timestamp": "2026-10-26T03:00:00Z", "data": {"z": 1, "a": [2, 3]}}';

function signedHeaders(payload: string, timestamp: number = NOW_SECONDS): Headers {
  return new Headers({
    "webhook-id": ID,
    "webhook-timestamp": String(timestamp),
    "webhook-signature": signWebhookPayload({ id: ID, timestamp, payload, secret: SECRET }),
  });
}

test("서명이 맞으면 200이고 type을 기록한다", () => {
  const result = receiveWebhook({ payload: TEST_PAYLOAD, headers: signedHeaders(TEST_PAYLOAD), secret: SECRET, now: NOW });
  assert.deepEqual(result, {
    status: 200,
    receipt: {
      received_at: "2026-10-26T03:00:00.000Z",
      webhook_id: ID,
      type: "webhook_test",
      verified: true,
      reason: null,
      payload: TEST_PAYLOAD,
    },
  });
});

test("모르는 type도 그대로 기록한다", () => {
  const payload = '{"type":"payment_succeeded","timestamp":"2026-10-26T03:00:00Z","data":{"amount":100}}';
  const result = receiveWebhook({ payload, headers: signedHeaders(payload), secret: SECRET, now: NOW });
  assert.equal(result.status, 200);
  assert.equal(result.receipt.type, "payment_succeeded");
  assert.equal(result.receipt.verified, true);
});

test("바디가 바뀌면 400 signature_mismatch로 기록한다", () => {
  const tampered = TEST_PAYLOAD.replace("webhook_test", "webhook_tampered");
  const result = receiveWebhook({ payload: tampered, headers: signedHeaders(TEST_PAYLOAD), secret: SECRET, now: NOW });
  assert.equal(result.status, 400);
  assert.equal(result.receipt.verified, false);
  assert.equal(result.receipt.reason, "signature_mismatch");
  assert.equal(result.receipt.type, "webhook_tampered");
  assert.equal(result.receipt.payload, tampered);
});

test("오래된 timestamp는 400 timestamp_out_of_range", () => {
  const headers = signedHeaders(TEST_PAYLOAD, NOW_SECONDS - 301);
  const result = receiveWebhook({ payload: TEST_PAYLOAD, headers, secret: SECRET, now: NOW });
  assert.equal(result.status, 400);
  assert.equal(result.receipt.verified, false);
  assert.equal(result.receipt.reason, "timestamp_out_of_range");
  assert.equal(result.receipt.type, "webhook_test");
});

test("비밀값이 없으면 400 secret_not_configured", () => {
  for (const secret of [undefined, "", "whsec_"]) {
    const result = receiveWebhook({ payload: TEST_PAYLOAD, headers: signedHeaders(TEST_PAYLOAD), secret, now: NOW });
    assert.equal(result.status, 400);
    assert.equal(result.receipt.verified, false);
    assert.equal(result.receipt.reason, "secret_not_configured");
    assert.equal(result.receipt.type, "webhook_test");
    assert.equal(result.receipt.webhook_id, ID);
  }
});

test("받은 문자열을 바꾸지 않고 payload로 남긴다", () => {
  const result = receiveWebhook({ payload: SPACED_PAYLOAD, headers: signedHeaders(SPACED_PAYLOAD), secret: SECRET, now: NOW });
  assert.equal(result.status, 200);
  assert.equal(result.receipt.payload, SPACED_PAYLOAD);
});

test("공백을 없애 다시 직렬화한 본문은 signature_mismatch", () => {
  const reserialized = JSON.stringify(JSON.parse(SPACED_PAYLOAD));
  assert.notEqual(reserialized, SPACED_PAYLOAD);
  const result = receiveWebhook({ payload: reserialized, headers: signedHeaders(SPACED_PAYLOAD), secret: SECRET, now: NOW });
  assert.equal(result.status, 400);
  assert.equal(result.receipt.reason, "signature_mismatch");
});

test("서명이 맞은 본문이 객체가 아니면 type은 null이고 유효로 기록한다", () => {
  for (const payload of ["null", "2", "[]", '{"type":7}']) {
    const result = receiveWebhook({ payload, headers: signedHeaders(payload), secret: SECRET, now: NOW });
    assert.equal(result.status, 200, payload);
    assert.equal(result.receipt.type, null, payload);
    assert.equal(result.receipt.verified, true, payload);
    assert.equal(result.receipt.reason, null, payload);
  }
});

test("서명이 맞아도 JSON이 아니면 400 invalid_payload", () => {
  const payload = "not json";
  const result = receiveWebhook({ payload, headers: signedHeaders(payload), secret: SECRET, now: NOW });
  assert.equal(result.status, 400);
  assert.equal(result.receipt.reason, "invalid_payload");
  assert.equal(result.receipt.type, null);
});

test("헤더가 없으면 400 missing_header이고 webhook_id는 null", () => {
  const result = receiveWebhook({ payload: TEST_PAYLOAD, headers: new Headers(), secret: SECRET, now: NOW });
  assert.equal(result.status, 400);
  assert.equal(result.receipt.reason, "missing_header");
  assert.equal(result.receipt.webhook_id, null);
});

test("SDK가 키로 쓸 수 있는 비밀값만 설정된 것으로 본다", () => {
  assert.equal(isWebhookSecretUsable(SECRET), true);
  assert.equal(isWebhookSecretUsable(SECRET.slice("whsec_".length)), true);
  assert.equal(isWebhookSecretUsable(undefined), false);
  assert.equal(isWebhookSecretUsable(""), false);
  assert.equal(isWebhookSecretUsable("whsec_"), false);
});

test("기록 한 줄을 읽고 깨진 줄은 null", () => {
  const receipt: WebhookReceipt = {
    received_at: "2026-10-26T03:00:00.000Z",
    webhook_id: null,
    type: null,
    verified: false,
    reason: "missing_header",
    payload: "{}",
  };
  assert.deepEqual(parseWebhookReceiptLine(JSON.stringify(receipt)), receipt);
  assert.equal(parseWebhookReceiptLine('{"received_at":"x"'), null);
  assert.equal(parseWebhookReceiptLine("null"), null);
  assert.equal(parseWebhookReceiptLine('{"received_at":"x","payload":1}'), null);
  assert.equal(parseWebhookReceiptLine(JSON.stringify({ ...receipt, verified: "yes" })), null);
  assert.equal(parseWebhookReceiptLine(JSON.stringify({ ...receipt, received_at: "어제" })), null);
});
