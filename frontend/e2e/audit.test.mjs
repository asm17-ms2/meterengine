import assert from "node:assert/strict";
import test from "node:test";
import { auditActorLabel } from "../src/lib/audit.ts";

test("an audit entry names the person who acted and falls back to the actor kind", () => {
  const names = new Map([["11111111-1111-4111-8111-111111111111", "김대표 (owner@example.com)"]]);
  assert.equal(auditActorLabel("user_account", "11111111-1111-4111-8111-111111111111", names), "김대표 (owner@example.com)");
  assert.equal(auditActorLabel("api_key", "22222222-2222-4222-8222-222222222222", names), "API 키");
  assert.equal(auditActorLabel("system", null, names), "시스템");
  assert.equal(auditActorLabel("future_actor", null, names), "future_actor");
});
