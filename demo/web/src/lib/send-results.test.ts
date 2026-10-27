import assert from "node:assert/strict";
import { test } from "node:test";

import { MeterEngineBatchError, MeterEngineClient, type IngestEventInput, type IngestEventRequest } from "@meterengine/sdk";

import { parseEventCsv, type CsvEventRow } from "./csv.ts";
import { sendCsvRows, toRowResults } from "./send-results.ts";

function eventRows(count: number): CsvEventRow[] {
  const csv = [
    "transaction_id,customer_id,event_type,timestamp,properties",
    ...Array.from({ length: count }, (_value, index) => `tx-${index},c-1,request,2026-10-01T00:00:00Z,{}`),
  ].join("\n");
  const parsed = parseEventCsv(csv);
  assert.ok("rows" in parsed);
  return parsed.rows;
}

function batchSender(failAt?: number) {
  const calls: IngestEventRequest[][] = [];
  const client = new MeterEngineClient({
    baseUrl: "http://unused.invalid",
    apiKey: "test-key",
    maxRetries: 0,
    fetch: async (_input, init) => {
      const { events } = JSON.parse(String(init?.body)) as { events: IngestEventRequest[] };
      calls.push(events);
      if (calls.length === failAt) {
        throw new TypeError("connection lost after sending");
      }
      return new Response(JSON.stringify({
        results: events.map((event, index) => ({ index, transaction_id: event.transaction_id, result: "stored" })),
      }));
    },
  });
  return { calls, send: (events: IngestEventInput[]) => client.ingestEventBatch(events) };
}

for (const [count, failAt, accepted, unknown, notSent] of [[201, 2, 100, 100, 1], [205, 3, 200, 5, 0], [101, 1, 0, 100, 1]]) {
  test(`${count}행 중 ${failAt}번째 묶음 연결 실패: 접수 ${accepted}, 미확인 ${unknown}, 미전송 ${notSent}`, async () => {
    const rows = eventRows(count);
    const { calls, send } = batchSender(failAt);
    const sent = await sendCsvRows(rows, (events) => send(events));
    assert.equal(sent.ok, false);
    assert.equal(calls.length, failAt);
    assert.deepEqual(sent.results.map((row) => row.tag), [
      ...Array(accepted).fill("NEW"), ...Array(unknown).fill("UNKNOWN"), ...Array(notSent).fill("NOT_SENT"),
    ]);
    assert.deepEqual(sent.results.map((row) => [row.line, row.transaction_id]), rows.map((row) => [row.line, row.transaction_id]));
    assert.deepEqual(calls.flat().map((event) => event.transaction_id), rows.slice(0, accepted + unknown).map((row) => row.transaction_id));
    assert.match(sent.results[accepted].message ?? "", /서버가 접수했을 수/);
  });
}

test("205행 성공은 마지막 5행까지 원래 ID와 순서를 보존한다", async () => {
  const rows = eventRows(205);
  const { calls, send } = batchSender();
  const sent = await sendCsvRows(rows, (events) => send(events));
  assert.equal(sent.ok, true);
  assert.deepEqual(calls.map((batch) => batch.length), [100, 100, 5]);
  assert.equal(sent.results.length, 205);
  assert.ok(sent.results.every((row) => row.tag === "NEW"));
  assert.deepEqual(sent.results.map((row) => row.transaction_id), rows.map((row) => row.transaction_id));
});

test("검증 제외 행과 중복 ID가 있어도 전송 인덱스로 결과를 연결한다", async () => {
  const rows = eventRows(203);
  rows[0].error = "잘못된 properties";
  rows[102].error = "잘못된 properties";
  rows[101].transaction_id = rows[1].transaction_id;
  const { send } = batchSender(2);
  const sent = await sendCsvRows(rows, (events) => send(events));
  assert.equal(sent.results.length, 203);
  assert.deepEqual(sent.results.map((row) => row.tag), [
    "SKIP", ...Array(100).fill("NEW"), "UNKNOWN", "SKIP", ...Array(99).fill("UNKNOWN"), "NOT_SENT",
  ]);
  assert.equal(sent.results[101].transaction_id, rows[1].transaction_id);
});

test("응답에서 빠진 행은 미확인으로 남고 접수와 거절, 중복을 구분한다", () => {
  const rows = eventRows(5);
  const results = toRowResults(rows, [
    { index: 2, transaction_id: "tx-2", result: "rejected", error: { code: "invalid", message: "거절됨" } },
    { index: 0, transaction_id: "tx-0", result: "stored" },
    { index: 3, transaction_id: "tx-3", result: "duplicate" },
  ], 4);
  assert.deepEqual(results.map((row) => row.tag), ["NEW", "UNKNOWN", "REJ", "DUP", "NOT_SENT"]);
  assert.equal(results[2].code, "invalid");
  assert.equal(results[2].message, "거절됨");
});

test("대상 설정 오류처럼 배치를 시작하지 못한 경우 모든 유효 행은 미전송이다", async () => {
  const rows = eventRows(201);
  const sent = await sendCsvRows(rows, () => { throw new Error("대상 없음"); });
  assert.equal(sent.ok, false);
  assert.equal(sent.results.length, 201);
  assert.ok(sent.results.every((row) => row.tag === "NOT_SENT"));
});

test("검증 제외 행만 있으면 전송 함수를 부르지 않는다", async () => {
  const rows = eventRows(2).map((row) => ({ ...row, error: "잘못된 properties" }));
  const sent = await sendCsvRows(rows, () => { assert.fail("전송하지 않아야 한다"); });
  assert.equal(sent.ok, true);
  assert.deepEqual(sent.results.map((row) => row.tag), ["SKIP", "SKIP"]);
});

test("실패 범위가 없는 기존 SDK 오류도 미확인 행을 버리거나 미전송으로 단정하지 않는다", async () => {
  const rows = eventRows(201);
  const sent = await sendCsvRows(rows, async () => {
    throw new MeterEngineBatchError([
      { index: 0, transaction_id: rows[0].transaction_id, result: "stored" },
    ], new Error("연결 실패"));
  });
  assert.equal(sent.ok, false);
  assert.equal(sent.results.length, 201);
  assert.equal(sent.results[0].tag, "NEW");
  assert.ok(sent.results.slice(1).every((row) => row.tag === "UNKNOWN"));
});
