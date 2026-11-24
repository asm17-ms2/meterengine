import type { UsageStatsSnapshot, WorkspaceSnapshot } from "../lib/contracts.ts";

// 요청을 보낸 뒤에는 통계만 다시 읽어 끼운다. 그 사이 계정이나 달이 바뀌었으면 버린다.
export function withUsageStats(snapshot: WorkspaceSnapshot | null, update: UsageStatsSnapshot): WorkspaceSnapshot | null {
  if (!snapshot || !("usage" in snapshot) || snapshot.user.id !== update.user.id || snapshot.month !== update.month) return snapshot;
  return { ...snapshot, stats: update.stats };
}
