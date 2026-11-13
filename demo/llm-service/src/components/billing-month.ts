export type WorkspaceSection = "chat" | "api" | "batch" | "usage" | "billing" | "history";

const MONTH_SECTIONS: ReadonlySet<WorkspaceSection> = new Set(["usage", "billing", "history"]);

export function hasMonthFilter(section: WorkspaceSection): boolean {
  return MONTH_SECTIONS.has(section);
}

export function currentBillingMonth(now: Date = new Date()): string {
  return new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit" }).format(now);
}

// 모델을 쓰는 화면은 언제나 요청 시점의 한국 시간 월을 보여 준다.
export function workspaceBillingMonth(section: WorkspaceSection, selectedMonth: string, now: Date = new Date()): string {
  return hasMonthFilter(section) ? selectedMonth : currentBillingMonth(now);
}
