export type WorkspaceSection = "playground" | "usage" | "billing" | "history";

export function currentBillingMonth(now: Date = new Date()): string {
  return new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit" }).format(now);
}

export function workspaceBillingMonth(section: WorkspaceSection, selectedMonth: string, now: Date = new Date()): string {
  return section === "playground" ? currentBillingMonth(now) : selectedMonth;
}
