const SCOPE_LABELS: Record<string, string> = { active: "사용 중 고객", archived: "보관된 고객", all: "전체 고객" };

// 검색어가 없을 때 '검색 전'이라고 쓰지 않도록, 보관 범위와 검색 여부를 숫자 앞에 밝힌다.
export function customerCountText({ status, q, total, totalUnfiltered }: { status?: string; q?: string; total: number; totalUnfiltered: number }): string {
  const scope = SCOPE_LABELS[status ?? "active"] ?? SCOPE_LABELS.active;
  const counted = `${q ? "검색어에 맞는 " : ""}${scope} ${total}곳`;
  return status === "all" && !q ? counted : `${counted}, 보관 포함 전체 ${totalUnfiltered}곳`;
}
