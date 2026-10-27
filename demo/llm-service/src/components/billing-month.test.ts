import assert from "node:assert/strict";
import test from "node:test";
import { currentBillingMonth, workspaceBillingMonth } from "./billing-month.ts";

test("한국 시간의 월 경계에서 현재 청구 월을 바꾼다", () => {
  assert.equal(currentBillingMonth(new Date("2026-10-31T14:59:59.999Z")), "2026-10");
  assert.equal(currentBillingMonth(new Date("2026-10-31T15:00:00.000Z")), "2026-11");
});

test("지난달부터 열어 둔 플레이그라운드도 요청 시점의 한국 시간 월을 쓴다", () => {
  assert.equal(workspaceBillingMonth("playground", "2026-10", new Date("2026-10-31T15:00:00.000Z")), "2026-11");
});

test("사용량과 청구 및 기록의 직접 선택한 월은 유지한다", () => {
  const now = new Date("2026-10-31T15:00:00.000Z");
  for (const section of ["usage", "billing", "history"] as const) {
    assert.equal(workspaceBillingMonth(section, "2026-08", now), "2026-08");
  }
});

test("한국 시간의 연도 경계도 새해 첫 달로 전환한다", () => {
  assert.equal(workspaceBillingMonth("playground", "2026-12", new Date("2026-12-31T15:00:00.000Z")), "2027-01");
});
