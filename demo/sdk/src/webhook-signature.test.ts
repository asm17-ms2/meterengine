import assert from "node:assert/strict";
import { test } from "node:test";
import { WebhookVerificationError } from "./errors.ts";
import { signWebhookPayload, verifyWebhookSignature } from "./webhook-signature.ts";

const SECRET = "whsec_MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw";
const ID = "msg_p5jXN8AQM9LWM0D4loKWxJek";
const TIMESTAMP = 1614265330;
const PAYLOAD = '{"test": 2432232314}';
const SIGNATURE = "v1,g0hM9SsE+OTPJTGt/tmIKtSyZlE3uFJELVlNIOLJ1OE=";
const OTHER_SIGNATURE = "v1,Ceo5qEr07ixe2NLpvHk3FH9bwy/WavXrAFQ/9tdO6mc=";
const AT_TIMESTAMP = new Date(TIMESTAMP * 1000);

function vectorHeaders(): Record<string, string | undefined> {
  return { "webhook-id": ID, "webhook-timestamp": String(TIMESTAMP), "webhook-signature": SIGNATURE };
}

function failsWith(reason: WebhookVerificationError["reason"]) {
  return (error: unknown) => error instanceof WebhookVerificationError && error.reason === reason;
}

test("공식 벡터로 서명을 만든다", () => {
  assert.equal(signWebhookPayload({ id: ID, timestamp: TIMESTAMP, payload: PAYLOAD, secret: SECRET }), SIGNATURE);
});

test("공식 벡터를 검증하고 본문을 돌려준다", () => {
  const event = verifyWebhookSignature({ payload: PAYLOAD, headers: vectorHeaders(), secret: SECRET, now: AT_TIMESTAMP });
  assert.deepEqual(event, { test: 2432232314 });
});

test("whsec_ 접두가 있어도 없어도 같은 키다", () => {
  const unprefixed = SECRET.slice("whsec_".length);
  assert.equal(signWebhookPayload({ id: ID, timestamp: TIMESTAMP, payload: PAYLOAD, secret: unprefixed }), SIGNATURE);
  const event = verifyWebhookSignature({ payload: PAYLOAD, headers: vectorHeaders(), secret: unprefixed, now: AT_TIMESTAMP });
  assert.deepEqual(event, { test: 2432232314 });
});

test("바디를 한 글자 바꾸면 signature_mismatch", () => {
  assert.throws(
    () => verifyWebhookSignature({ payload: '{"test": 2432232315}', headers: vectorHeaders(), secret: SECRET, now: AT_TIMESTAMP }),
    failsWith("signature_mismatch"),
  );
});

test("시각 차가 300초를 넘으면 timestamp_out_of_range", () => {
  for (const offset of [301, -301]) {
    assert.throws(
      () =>
        verifyWebhookSignature({
          payload: PAYLOAD,
          headers: vectorHeaders(),
          secret: SECRET,
          now: new Date((TIMESTAMP + offset) * 1000),
        }),
      failsWith("timestamp_out_of_range"),
      String(offset),
    );
  }
  for (const offset of [300, -300]) {
    const event = verifyWebhookSignature({
      payload: PAYLOAD,
      headers: vectorHeaders(),
      secret: SECRET,
      now: new Date((TIMESTAMP + offset) * 1000),
    });
    assert.deepEqual(event, { test: 2432232314 }, String(offset));
  }
});

test("서명이 둘이면 하나만 맞아도 통과한다", () => {
  const headers = { ...vectorHeaders(), "webhook-signature": `${OTHER_SIGNATURE} ${SIGNATURE}` };
  const event = verifyWebhookSignature({ payload: PAYLOAD, headers, secret: SECRET, now: AT_TIMESTAMP });
  assert.deepEqual(event, { test: 2432232314 });
});

test("v1이 아닌 서명은 건너뛴다", () => {
  const signatureValue = SIGNATURE.slice("v1,".length);
  const headers = { ...vectorHeaders(), "webhook-signature": `v2,${signatureValue}` };
  assert.throws(
    () => verifyWebhookSignature({ payload: PAYLOAD, headers, secret: SECRET, now: AT_TIMESTAMP }),
    failsWith("signature_mismatch"),
  );
});

test("길이가 다른 서명은 불일치로 본다", () => {
  for (const signature of [SIGNATURE.slice(0, 8), "v1,", "v1,dawfeoifkpqwoekfpqoekf"]) {
    const headers = { ...vectorHeaders(), "webhook-signature": signature };
    assert.throws(
      () => verifyWebhookSignature({ payload: PAYLOAD, headers, secret: SECRET, now: AT_TIMESTAMP }),
      failsWith("signature_mismatch"),
      signature,
    );
  }
});

test("헤더가 하나라도 없으면 missing_header", () => {
  for (const name of ["webhook-id", "webhook-timestamp", "webhook-signature"]) {
    const headers = { ...vectorHeaders(), [name]: undefined };
    assert.throws(
      () => verifyWebhookSignature({ payload: PAYLOAD, headers, secret: SECRET, now: AT_TIMESTAMP }),
      failsWith("missing_header"),
      name,
    );
  }
});

test("timestamp가 숫자가 아니면 invalid_timestamp", () => {
  for (const timestamp of ["hello", "", "1614265330abc", "1614265330.5"]) {
    const headers = { ...vectorHeaders(), "webhook-timestamp": timestamp };
    assert.throws(
      () => verifyWebhookSignature({ payload: PAYLOAD, headers, secret: SECRET, now: AT_TIMESTAMP }),
      timestamp === "" ? failsWith("missing_header") : failsWith("invalid_timestamp"),
      timestamp,
    );
  }
});

test("Headers 객체와 대문자 헤더 이름을 받는다", () => {
  const headers = new Headers({ "Webhook-Id": ID, "Webhook-Timestamp": String(TIMESTAMP), "Webhook-Signature": SIGNATURE });
  assert.deepEqual(verifyWebhookSignature({ payload: PAYLOAD, headers, secret: SECRET, now: AT_TIMESTAMP }), { test: 2432232314 });
  const record = { "WEBHOOK-ID": ID, "WEBHOOK-TIMESTAMP": String(TIMESTAMP), "WEBHOOK-SIGNATURE": SIGNATURE };
  assert.deepEqual(verifyWebhookSignature({ payload: PAYLOAD, headers: record, secret: SECRET, now: AT_TIMESTAMP }), { test: 2432232314 });
});

test("서명이 맞아도 본문이 JSON이 아니면 invalid_payload", () => {
  const payload = "not json";
  const headers = { ...vectorHeaders(), "webhook-signature": signWebhookPayload({ id: ID, timestamp: TIMESTAMP, payload, secret: SECRET }) };
  assert.throws(
    () => verifyWebhookSignature({ payload, headers, secret: SECRET, now: AT_TIMESTAMP }),
    failsWith("invalid_payload"),
  );
});

test("비밀값이 비었거나 디코드 결과가 빈 바이트면 일반 Error", () => {
  for (const secret of ["", "whsec_"]) {
    for (const call of [
      () => signWebhookPayload({ id: ID, timestamp: TIMESTAMP, payload: PAYLOAD, secret }),
      () => verifyWebhookSignature({ payload: PAYLOAD, headers: vectorHeaders(), secret, now: AT_TIMESTAMP }),
    ]) {
      assert.throws(call, (error: unknown) => error instanceof Error && !(error instanceof WebhookVerificationError), secret);
    }
  }
});

test("now를 주지 않으면 현재 시각으로 검사한다", () => {
  const timestamp = Math.floor(Date.now() / 1000);
  const signature = signWebhookPayload({ id: ID, timestamp, payload: PAYLOAD, secret: SECRET });
  const headers = { "webhook-id": ID, "webhook-timestamp": String(timestamp), "webhook-signature": signature };
  assert.deepEqual(verifyWebhookSignature({ payload: PAYLOAD, headers, secret: SECRET }), { test: 2432232314 });
  assert.throws(() => verifyWebhookSignature({ payload: PAYLOAD, headers: vectorHeaders(), secret: SECRET }), failsWith("timestamp_out_of_range"));
});
