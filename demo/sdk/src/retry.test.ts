import assert from "node:assert/strict";
import { test } from "node:test";
import {
  MeterEngineApiError,
  MeterEngineConnectionError,
  MeterEngineTimeoutError,
} from "./errors.ts";
import { isRetryable, parseRetryAfter, retryDelayMs } from "./retry.ts";

const NOW_MS = Date.parse("2026-10-26T00:00:00Z");

test("Retry-After 정수 초를 그대로 읽는다", () => {
  assert.equal(parseRetryAfter("2", NOW_MS), 2);
  assert.equal(parseRetryAfter("0", NOW_MS), 0);
});

test("Retry-After HTTP 날짜는 지금부터 남은 초로 바꾼다", () => {
  assert.equal(parseRetryAfter("Mon, 26 Oct 2026 00:00:03 GMT", NOW_MS), 3);
});

test("Retry-After HTTP 날짜가 지났으면 0이다", () => {
  assert.equal(parseRetryAfter("Sun, 25 Oct 2026 23:59:00 GMT", NOW_MS), 0);
});

test("Retry-After가 없거나 잘못된 값이면 undefined", () => {
  for (const header of [null, "", "abc", "-1", "1.5", "2 seconds"]) {
    assert.equal(parseRetryAfter(header, NOW_MS), undefined, String(header));
  }
});

test("Retry-After가 있으면 그 초만큼 기다린다", () => {
  assert.equal(retryDelayMs(0, 2, () => 0.5), 2000);
});

test("Retry-After가 없으면 full jitter로 기다린다", () => {
  assert.equal(retryDelayMs(3, undefined, () => 1), 1600);
  assert.equal(retryDelayMs(0, undefined, () => 0.5), 100);
  assert.equal(retryDelayMs(3, undefined, () => 0), 0);
});

test("full jitter 대기는 5000ms를 넘지 않는다", () => {
  assert.equal(retryDelayMs(10, undefined, () => 1), 5000);
});

test("429, 5xx, 연결 오류, 타임아웃은 재시도한다", () => {
  for (const status of [429, 500, 503]) {
    assert.equal(isRetryable(new MeterEngineApiError(status, { code: "x", message: "x" })), true, String(status));
  }
  assert.equal(isRetryable(new MeterEngineConnectionError(new TypeError("fetch failed"))), true);
  assert.equal(isRetryable(new MeterEngineTimeoutError(10)), true);
});

test("400, 401, 403, 404, 409와 그 밖의 오류는 재시도하지 않는다", () => {
  for (const status of [400, 401, 403, 404, 409]) {
    assert.equal(isRetryable(new MeterEngineApiError(status, { code: "x", message: "x" })), false, String(status));
  }
  assert.equal(isRetryable(new Error("x")), false);
  assert.equal(isRetryable(new DOMException("aborted", "AbortError")), false);
});
