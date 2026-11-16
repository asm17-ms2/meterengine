import assert from "node:assert/strict";
import { test } from "node:test";
import { EXIT_FAILED, EXIT_OK, EXIT_USAGE } from "../exit-code.ts";
import { run } from "../program.ts";
import { LIVE_KEY, TEST_KEY, json, setup } from "../testing.ts";

const CUSTOMER_ID = "008cd6a7-6ff9-505d-9421-747e7d2d62aa";
const INVOICE_ID = "5b0d3a52-8f1e-4c47-9d0a-2f6c1e7b9a10";
const INVOICE = {
  id: INVOICE_ID,
  invoice_number: "INV-202610-0001",
  customer_id: CUSTOMER_ID,
  customer_name: "도담헬스",
  month: "2026-10",
  supply_amount: 1000,
  tax_amount: 100,
  total_amount: 1100,
  cutoff_at: "2026-11-01T13:00:00+09:00",
  finalized_at: "2026-11-01T13:05:00+09:00",
  lines: [],
};

test("invoices list는 월과 고객을 쿼리로 보내고 서버 응답을 그대로 낸다", async () => {
  const { deps, calls, err, stdoutJson } = await setup([json(200, { invoices: [INVOICE] })]);

  const code = await run(["invoices", "list", "--month", "2026-10", "--customer", CUSTOMER_ID], deps);

  assert.equal(code, EXIT_OK);
  assert.deepEqual(stdoutJson(), { invoices: [INVOICE] });
  assert.equal(err.join(""), "");
  assert.equal(calls.length, 1);
  assert.equal(calls[0].method, "GET");
  assert.equal(calls[0].url, `http://localhost:8080/v1/invoices?month=2026-10&customer_id=${CUSTOMER_ID}`);
  assert.equal(calls[0].headers.get("Authorization"), `Bearer ${TEST_KEY}`);
});

test("invoices list에 옵션이 없으면 쿼리 없이 모든 달을 조회한다", async () => {
  const { deps, calls, stdoutJson } = await setup([json(200, { invoices: [] })]);

  assert.equal(await run(["invoices", "list"], deps), EXIT_OK);
  assert.equal(calls[0].url, "http://localhost:8080/v1/invoices");
  assert.deepEqual(stdoutJson(), { invoices: [] });
});

test("invoices list의 월이나 고객 형식이 틀리면 서버에 보내지 않고 종료 코드 2다", async () => {
  for (const [args, expected] of [
    [["--month", "2026-13"], "invalid_month"],
    [["--month", "202611"], "invalid_month"],
    [["--customer", "cust-ext-001"], "invalid_customer_id"],
  ] as const) {
    const { deps, calls, out, stderrJson } = await setup([]);
    assert.equal(await run(["invoices", "list", ...args], deps), EXIT_USAGE);
    assert.equal(calls.length, 0);
    assert.equal(out.join(""), "");
    assert.equal((stderrJson() as { code: string }).code, expected);
  }
});

test("invoices list에서 서버가 고객을 못 찾으면 종료 코드 1과 서버 오류를 낸다", async () => {
  const notFound = { code: "customer_not_found", message: "고객을 찾을 수 없습니다" };
  const { deps, out, stderrJson } = await setup([json(404, notFound)]);

  assert.equal(await run(["invoices", "list", "--customer", CUSTOMER_ID], deps), EXIT_FAILED);
  assert.equal(out.join(""), "");
  assert.deepEqual(stderrJson(), notFound);
});

test("invoices get은 id로 인보이스 하나를 조회해 그대로 낸다", async () => {
  const { deps, calls, stdoutJson } = await setup([json(200, INVOICE)]);

  assert.equal(await run(["invoices", "get", INVOICE_ID], deps), EXIT_OK);
  assert.equal(calls[0].method, "GET");
  assert.equal(calls[0].url, `http://localhost:8080/v1/invoices/${INVOICE_ID}`);
  assert.deepEqual(stdoutJson(), INVOICE);
});

test("invoices get의 id가 UUID가 아니면 서버에 보내지 않고 종료 코드 2다", async () => {
  const { deps, calls, stderrJson } = await setup([]);

  assert.equal(await run(["invoices", "get", "../draft"], deps), EXIT_USAGE);
  assert.equal(calls.length, 0);
  assert.equal((stderrJson() as { code: string }).code, "invalid_invoice_id");
});

test("invoices get에서 없는 인보이스는 종료 코드 1이다", async () => {
  const notFound = { code: "invoice_not_found", message: "인보이스를 찾을 수 없습니다" };
  const { deps, stderrJson } = await setup([json(404, notFound)]);

  assert.equal(await run(["invoices", "get", INVOICE_ID], deps), EXIT_FAILED);
  assert.deepEqual(stderrJson(), notFound);
});

test("invoices get에 id가 없으면 종료 코드 2다", async () => {
  const { deps, calls, stderrJson } = await setup([]);

  assert.equal(await run(["invoices", "get"], deps), EXIT_USAGE);
  assert.equal(calls.length, 0);
  assert.equal((stderrJson() as { code: string }).code, "invalid_usage");
});

test("invoices draft의 월 형식이 틀리면 서버에 보내지 않고 종료 코드 2다", async () => {
  const { deps, calls, stderrJson } = await setup([]);

  assert.equal(await run(["invoices", "draft", "--month", "2026-1"], deps), EXIT_USAGE);
  assert.equal(calls.length, 0);
  assert.equal((stderrJson() as { code: string }).code, "invalid_month");
});

test("--profile로 저장한 키로 invoices list를 부른다", async () => {
  const { deps, calls } = await setup([json(200, { invoices: [] })], { env: {}, secret: LIVE_KEY });

  assert.equal(await run(["login", "--profile", "live"], deps), EXIT_OK);
  assert.equal(await run(["invoices", "list", "--profile", "live"], deps), EXIT_OK);
  assert.equal(calls[0].headers.get("Authorization"), `Bearer ${LIVE_KEY}`);
});
