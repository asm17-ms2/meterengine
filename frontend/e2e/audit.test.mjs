import assert from "node:assert/strict";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import test from "node:test";
import { AUDIT_ACTION_OPTIONS, AUDIT_ACTIONS, auditActorLabel } from "../src/lib/audit.ts";

const BACKEND = new URL("../../backend/src/main/java/", import.meta.url).pathname;
const javaFiles = dir => readdirSync(dir).flatMap(name => {
  const path = join(dir, name);
  return statSync(path).isDirectory() ? javaFiles(path) : name.endsWith(".java") ? [path] : [];
});
const ACTION_LITERAL = /"([a-z]+(?:_[a-z]+)*_(?:created|updated|deleted|archived|restored|retried|accepted|revoked|finalized|viewed|requested|completed))"/g;

test("an audit entry names the person who acted and falls back to the actor kind", () => {
  const names = new Map([["11111111-1111-4111-8111-111111111111", "김대표 (owner@example.com)"]]);
  assert.equal(auditActorLabel("user_account", "11111111-1111-4111-8111-111111111111", names), "김대표 (owner@example.com)");
  assert.equal(auditActorLabel("api_key", "22222222-2222-4222-8222-222222222222", names), "API 키");
  assert.equal(auditActorLabel("system", null, names), "시스템");
  assert.equal(auditActorLabel("future_actor", null, names), "future_actor");
});

test("every audit action the backend records has a label on the audit screen", () => {
  const recorded = new Set(javaFiles(BACKEND).flatMap(path => [...readFileSync(path, "utf8").matchAll(ACTION_LITERAL)].map(match => match[1])));
  assert.ok(recorded.size > 0);
  for (const action of recorded) assert.ok(AUDIT_ACTIONS[action], `${action} has no audit label`);
});

test("the action filter does not offer actions the backend no longer records", () => {
  assert.equal(AUDIT_ACTION_OPTIONS.some(([action]) => action === "price_rate_deleted"), false);
  assert.ok(AUDIT_ACTION_OPTIONS.some(([action]) => action === "payment_setting_updated"));
});
