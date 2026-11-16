import assert from "node:assert/strict";
import { test } from "node:test";
import { EXIT_FAILED, EXIT_OK, EXIT_USAGE } from "../exit-code.ts";
import { run } from "../program.ts";
import { TEST_KEY, json, setup } from "../testing.ts";

const CUSTOMER_ID = "0b6f1c1e-3d5a-4c2b-9f7e-2a1d4c6b8e90";
const EVENT = {
  transaction_id: "order-1/2 &x",
  customer_id: CUSTOMER_ID,
  customer_name: "OpenAB",
  type: "llm.tokens",
  properties: { tokens: 1200, model: "ab-1" },
  occurred_at: "2026-11-14T11:00:00+09:00",
  received_at: "2026-11-14T11:00:01.204+09:00",
  request_id: "5f0c8d7a-1b2c-4d3e-8f9a-0b1c2d3e4f5a",
};
const PAGE = { month: "2026-11", page: 0, size: 20, total: 1, events: [EVENT], from: "2026-11-01T00:00:00+09:00", to: "2026-12-01T00:00:00+09:00" };

function query(url: string): Record<string, string> {
  return Object.fromEntries(new URL(url).searchParams);
}

test("events get은 transaction_id로 lookup을 불러 서버 응답을 그대로 낸다", async () => {
  const { deps, calls, err, stdoutJson } = await setup([json(200, EVENT)]);

  const code = await run(["events", "get", "order-1/2 &x"], deps);

  assert.equal(code, EXIT_OK);
  assert.deepEqual(stdoutJson(), EVENT);
  assert.equal(err.join(""), "");
  assert.equal(calls.length, 1);
  assert.equal(calls[0].method, "GET");
  assert.equal(new URL(calls[0].url).pathname, "/v1/events/lookup");
  assert.deepEqual(query(calls[0].url), { transaction_id: "order-1/2 &x" });
  assert.equal(calls[0].headers.get("Authorization"), `Bearer ${TEST_KEY}`);
});

test("events get은 저장된 이벤트가 없으면 서버의 404 event_not_found를 내고 종료 코드 1이다", async () => {
  const { deps, out, stderrJson } = await setup([json(404, { code: "event_not_found", message: "이벤트를 찾을 수 없습니다" })]);

  const code = await run(["events", "get", "never-sent"], deps);

  assert.equal(code, EXIT_FAILED);
  assert.equal(out.join(""), "");
  assert.deepEqual(stderrJson(), { code: "event_not_found", message: "이벤트를 찾을 수 없습니다" });
});

test("events get은 transaction_id가 비었으면 서버에 보내지 않고 종료 코드 2다", async () => {
  const { deps, calls, stderrJson } = await setup([]);

  assert.equal(await run(["events", "get", ""], deps), EXIT_USAGE);
  assert.equal(calls.length, 0);
  assert.equal((stderrJson() as { code: string }).code, "invalid_transaction_id");
});

test("events get은 transaction_id를 빠뜨리면 서버에 보내지 않고 종료 코드 2다", async () => {
  const { deps, calls, stderrJson } = await setup([]);

  assert.equal(await run(["events", "get"], deps), EXIT_USAGE);
  assert.equal(calls.length, 0);
  assert.equal((stderrJson() as { code: string }).code, "invalid_usage");
});

test("events list는 옵션이 없으면 쿼리 없이 불러 서버 기본값(이번 달, 0쪽, 20개)에 맡기고 응답을 그대로 낸다", async () => {
  const { deps, calls, stdoutJson } = await setup([json(200, PAGE)]);

  const code = await run(["events", "list"], deps);

  assert.equal(code, EXIT_OK);
  assert.deepEqual(stdoutJson(), PAGE);
  assert.equal(calls[0].url, "http://localhost:8080/v1/events");
});

test("events list는 월, 고객, 유형, 페이지, 크기를 서버 쿼리 이름으로 보낸다", async () => {
  const { deps, calls } = await setup([json(200, PAGE)]);

  const code = await run(["events", "list", "--month", "2026-10", "--customer", CUSTOMER_ID, "--type", "llm.tokens", "--page", "2", "--size", "100"], deps);

  assert.equal(code, EXIT_OK);
  assert.equal(new URL(calls[0].url).pathname, "/v1/events");
  assert.deepEqual(query(calls[0].url), { month: "2026-10", customer_id: CUSTOMER_ID, type: "llm.tokens", page: "2", size: "100" });
});

test("events list는 --from과 --to를 받은 그대로 보내고 해석은 서버에 맡긴다", async () => {
  const { deps, calls } = await setup([json(200, { ...PAGE, month: null })]);

  const code = await run(["events", "list", "--from", "2026-11-14T10:00:00+09:00", "--to", "2026-11-14"], deps);

  assert.equal(code, EXIT_OK);
  assert.deepEqual(query(calls[0].url), { from: "2026-11-14T10:00:00+09:00", to: "2026-11-14" });
});

test("events list는 옵션이 틀리면 서버에 보내지 않고 종료 코드 2와 사유를 낸다", async () => {
  const cases: [string[], string][] = [
    [["--month", "2026-13"], "invalid_month"],
    [["--from", "2026-11-01"], "invalid_period"],
    [["--to", "2026-11-02"], "invalid_period"],
    [["--month", "2026-11", "--from", "2026-11-01", "--to", "2026-11-02"], "invalid_period"],
    [["--customer", "cus_external_42"], "invalid_customer_id"],
    [["--page", "-1"], "invalid_page"],
    [["--page", "1.5"], "invalid_page"],
    [["--size", "0"], "invalid_size"],
    [["--size", "ten"], "invalid_size"],
    [["--type", ""], "invalid_type"],
    [["--type", "  "], "invalid_type"],
  ];
  for (const [args, expected] of cases) {
    const { deps, calls, stderrJson } = await setup([]);
    assert.equal(await run(["events", "list", ...args], deps), EXIT_USAGE, args.join(" "));
    assert.equal(calls.length, 0, args.join(" "));
    assert.equal((stderrJson() as { code: string }).code, expected, args.join(" "));
  }
});

test("events list의 크기 상한은 서버가 판정하고 거절하면 errors까지 표준 오류에 낸다", async () => {
  const rejection = { code: "validation_error", message: "요청 값이 올바르지 않습니다", errors: [{ field: "size", message: "100 이하여야 합니다" }] };
  const { deps, calls, stderrJson } = await setup([json(400, rejection)]);

  assert.equal(await run(["events", "list", "--size", "101"], deps), EXIT_FAILED);
  assert.deepEqual(query(calls[0].url), { size: "101" });
  assert.deepEqual(stderrJson(), rejection);
});

test("events types는 실제로 들어온 유형 목록을 그대로 내고 --month를 쿼리로 보낸다", async () => {
  const types = { types: ["llm.tokens", "llm.tokenz"] };
  for (const [args, url] of [
    [[], "http://localhost:8080/v1/events/types"],
    [["--month", "2026-10"], "http://localhost:8080/v1/events/types?month=2026-10"],
  ] as const) {
    const { deps, calls, stdoutJson } = await setup([json(200, types)]);
    assert.equal(await run(["events", "types", ...args], deps), EXIT_OK);
    assert.equal(calls[0].url, url);
    assert.deepEqual(stdoutJson(), types);
  }
});

test("events types는 월 형식이 틀리면 서버에 보내지 않고 종료 코드 2다", async () => {
  const { deps, calls, stderrJson } = await setup([]);

  assert.equal(await run(["events", "types", "--month", "2026-1"], deps), EXIT_USAGE);
  assert.equal(calls.length, 0);
  assert.equal((stderrJson() as { code: string }).code, "invalid_month");
});
