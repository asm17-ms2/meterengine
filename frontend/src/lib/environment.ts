export type WorkspaceEnvironment = "test" | "live";

export function isWorkspaceEnvironment(value: unknown): value is WorkspaceEnvironment {
  return value === "test" || value === "live";
}

export function readEnvironment(value: string | null | undefined): WorkspaceEnvironment {
  return value === "live" ? "live" : "test";
}

export const ENVIRONMENT_LABELS: Record<WorkspaceEnvironment, string> = {
  test: "테스트 환경", live: "실제 환경",
};

export const ENVIRONMENT_SWITCH_TITLES: Record<WorkspaceEnvironment, string> = {
  test: "테스트 환경으로 전환할까요?", live: "실제 환경으로 전환할까요?",
};

export function environmentNoticeClass(environment: WorkspaceEnvironment): string {
  return environment === "live" ? "environment-notice environment-notice-live" : "environment-notice";
}

export function environmentReadyPath(path: string): boolean {
  return ["/overview", "/customers", "/billable-metrics", "/billable-metric-prices", "/pricing", "/usage", "/events", "/event-issues", "/event-rejections", "/billing", "/invoices", "/payments", "/integrations", "/settings", "/members", "/audit"].some(prefix => path === prefix || path.startsWith(`${prefix}/`));
}

export function environmentSwitchPath(path: string): string {
  const root = `/${path.split("/").filter(Boolean)[0] ?? "customers"}`;
  return environmentReadyPath(root) ? root : "/customers";
}
