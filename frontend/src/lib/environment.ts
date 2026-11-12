export type WorkspaceEnvironment = "legacy" | "test" | "live";

export const DEFAULT_ENVIRONMENT: WorkspaceEnvironment = "test";

export function readEnvironment(value: string | null | undefined): WorkspaceEnvironment {
  return value === "legacy" || value === "live" ? value : DEFAULT_ENVIRONMENT;
}

/** 서버 환경 변수 METERENGINE_SHOW_LEGACY_ENVIRONMENT 값이 "true"일 때만 기존 데이터를 연다. */
export function showsLegacyEnvironment(flag: string | null | undefined): boolean {
  return flag?.trim() === "true";
}

export function environmentOptions(showLegacy: boolean): WorkspaceEnvironment[] {
  return showLegacy ? ["test", "live", "legacy"] : ["test", "live"];
}

export function isAllowedEnvironment(value: string, showLegacy: boolean): boolean {
  return environmentOptions(showLegacy).includes(value as WorkspaceEnvironment);
}

/** 플래그를 끈 서버에 남은 legacy 주소(즐겨찾기, 로그인 returnTo)인지 본다. */
export function isClosedLegacyEnvironment(value: string | null | undefined, showLegacy: boolean): boolean {
  return value === "legacy" && !showLegacy;
}

/** 닫힌 legacy 값만 주소에서 빼고 경로와 다른 쿼리는 그대로 둔다. */
export function withoutClosedLegacyEnvironment(path: string, showLegacy: boolean): string {
  const url = new URL(path, "https://console.invalid");
  if (!url.searchParams.getAll("environment").some(value => isClosedLegacyEnvironment(value, showLegacy))) return path;
  url.searchParams.delete("environment");
  return url.pathname + url.search + url.hash;
}

/** URL에 적을 환경 값. 기본 환경은 적지 않는다. */
export function environmentQueryValue(environment: WorkspaceEnvironment): WorkspaceEnvironment | undefined {
  return environment === DEFAULT_ENVIRONMENT ? undefined : environment;
}

export const ENVIRONMENT_LABELS: Record<WorkspaceEnvironment, string> = {
  test: "테스트 환경", live: "실제 환경", legacy: "기존 데이터",
};

export const ENVIRONMENT_SWITCH_TITLES: Record<WorkspaceEnvironment, string> = {
  test: "테스트 환경으로 전환할까요?", live: "실제 환경으로 전환할까요?", legacy: "기존 데이터로 전환할까요?",
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
