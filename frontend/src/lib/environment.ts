export type WorkspaceEnvironment = "legacy" | "test" | "live";

export function readEnvironment(value: string | null | undefined): WorkspaceEnvironment {
  return value === "test" || value === "live" ? value : "legacy";
}

export const ENVIRONMENT_LABELS: Record<WorkspaceEnvironment, string> = {
  legacy: "기존 데이터", test: "테스트 환경", live: "실제 환경",
};

export function environmentReadyPath(path: string): boolean {
  return ["/overview", "/customers", "/billable-metrics", "/billable-metric-prices", "/pricing", "/usage", "/events", "/event-issues", "/event-rejections", "/billing", "/invoices", "/payments", "/integrations", "/settings", "/members", "/audit"].some(prefix => path === prefix || path.startsWith(`${prefix}/`));
}

export function environmentSwitchPath(path: string): string {
  const root = `/${path.split("/").filter(Boolean)[0] ?? "customers"}`;
  return environmentReadyPath(root) ? root : "/customers";
}
