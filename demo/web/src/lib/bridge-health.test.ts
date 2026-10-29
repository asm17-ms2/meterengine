import assert from "node:assert/strict";
import { test } from "node:test";

import { describeBridgeHealth, type BridgeResult } from "./bridge-health.ts";

const S1_HEALTH = {
  status: "ok",
  base_url: "http://localhost:8080",
  api_key: "me_live_****2LtA",
  organization: { id: "org-1", name: "도입사" },
  organization_error: null,
  owner: "demo",
  projects: {},
  customers: [],
  denied_sessions: 0,
  counts: { stored: 3, rejected: 1, error: 0, skipped: 0 },
};

function describe(body: unknown): string {
  return describeBridgeHealth({ ok: true, status: 200, body });
}

for (const code of ["invalid_credentials", "organization_not_active", "http_401", "connection_failed"]) {
  test(`S1 health가 HTTP 200이고 status ok여도 ${code}이면 전송 중단이다`, () => {
    const text = describe({ ...S1_HEALTH, organization: null, organization_error: code });
    assert.match(text, /연결됨, 전송 중단/);
    assert.ok(text.includes(code));
    assert.doesNotMatch(text, /정상|전송 준비됨/);
  });
}

test("도입사 정보가 남아 있어도 명시적 오류를 우선한다", () => {
  assert.match(describe({ ...S1_HEALTH, organization_error: "invalid_credentials" }), /전송 중단/);
});

test("S1에서 확인한 도입사와 오류 없음이 함께 있어야 전송 준비됨이다", () => {
  const text = describe(S1_HEALTH);
  assert.match(text, /연결됨, 전송 준비됨, 도입사 도입사/);
  assert.match(text, /주인 demo, 전송 대상 http:\/\/localhost:8080/);
  assert.match(text, /stored 3 rejected 1/);
  assert.doesNotMatch(text, /me_live_/);
});

test("S1 시작 직후 도입사 미확인은 HTTP 연결과 구분한다", () => {
  assert.match(describe({ ...S1_HEALTH, organization: null }), /연결됨, 전송 준비 미확인/);
});

test("현재 org_id 브리지는 API 키 전송 준비를 확인한 것으로 표시하지 않는다", () => {
  const { organization, organization_error: organizationError, api_key: apiKey, ...legacy } = S1_HEALTH;
  assert.ok(organization && organizationError === null && apiKey);
  const text = describe({ ...legacy, org_id: "org-1" });
  assert.match(text, /연결됨, API 키 전송 준비 미확인 \(기존 org_id 방식\)/);
  assert.doesNotMatch(text, /정상|전송 준비됨/);
});

for (const body of [
  {}, { status: "ok" }, { ...S1_HEALTH, status: "error" }, { ...S1_HEALTH, status: "degraded" },
  { ...S1_HEALTH, ready: false }, { ...S1_HEALTH, forwarding: false }, { ...S1_HEALTH, error: "failed" },
  { ...S1_HEALTH, organization: { id: "", name: "도입사" } },
  { ...S1_HEALTH, organization_error: undefined },
]) {
  test(`알 수 없거나 실패한 상태를 준비됨으로 표시하지 않는다: ${JSON.stringify(body)}`, () => {
    assert.doesNotMatch(describe(body), /정상|전송 준비됨/);
  });
}

for (const result of [
  { ok: false, message: "연결 거절" },
  { ok: true, status: 503, body: { error: "사용 불가" } },
  { ok: true, status: 200, body: "ok" },
  { ok: true, status: 200, body: null },
  { ok: true, status: 200, body: [] },
] satisfies BridgeResult[]) {
  test(`연결 또는 응답 확인 실패: ${JSON.stringify(result)}`, () => {
    assert.match(describeBridgeHealth(result), result.ok ? /연결됨, 상태 확인 실패/ : /연결 확인 실패/);
  });
}
