import assert from "node:assert/strict";
import { test } from "node:test";
import { PROVIDER_INFO } from "../lib/contracts.ts";
import type { HistorySnapshot, UsageSnapshot, UsageStats, UsageStatsSnapshot } from "../lib/contracts.ts";
import { withUsageStats } from "./usage-stats.ts";

const USER = { id: "alpha", name: "가람데이터", email: "billing@garam.example" };
const EMPTY: UsageStats = { totalRequests: 0, usage: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0 }, pendingMetering: 0, failedMetering: 0 };
const PENDING: UsageStats = { ...EMPTY, totalRequests: 1, pendingMetering: 1 };

function usageSnapshot(): UsageSnapshot {
  return { user: USER, month: "2026-10", provider: PROVIDER_INFO, models: [], stats: EMPTY, usage: { state: "ready", updatedAt: "2026-10-26T12:00:00.000Z", metrics: [] }, pricing: { state: "ready" } };
}

function update(overrides: Partial<UsageStatsSnapshot> = {}): UsageStatsSnapshot {
  return { user: USER, month: "2026-10", stats: PENDING, ...overrides };
}

test("같은 계정과 달이면 통계만 바꾸고 단가와 사용량은 그대로 둔다", () => {
  const snapshot = usageSnapshot();
  const result = withUsageStats(snapshot, update());
  assert.deepEqual(result, { ...snapshot, stats: PENDING });
  assert.equal((result as UsageSnapshot).models, snapshot.models);
  assert.equal((result as UsageSnapshot).usage, snapshot.usage);
});

test("계정이나 달이 다르거나 사용량 화면의 자료가 아니면 바꾸지 않는다", () => {
  const snapshot = usageSnapshot();
  assert.equal(withUsageStats(snapshot, update({ user: { ...USER, id: "beta" } })), snapshot);
  assert.equal(withUsageStats(snapshot, update({ month: "2026-11" })), snapshot);
  const history: HistorySnapshot = { user: USER, month: "2026-10", requests: [] };
  assert.equal(withUsageStats(history, update()), history);
  assert.equal(withUsageStats(null, update()), null);
});
