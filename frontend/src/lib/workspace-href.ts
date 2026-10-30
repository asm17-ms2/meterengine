const MONTHLY_PATHS = ["/overview", "/usage", "/events", "/event-issues", "/event-rejections", "/billing", "/invoices", "/payments", "/pricing", "/customers"];
const WORKSPACE_PATHS = [...MONTHLY_PATHS, "/billable-metrics", "/billable-metric-prices", "/integrations", "/settings", "/members", "/audit"];

export function workspaceHref(path: string, source: string | URLSearchParams, options: { inheritFilters?: boolean } = {}): string {
  if (!path.startsWith("/") || path.startsWith("//")) return path;
  const current = new URLSearchParams(source);
  const url = new URL(path, "http://workspace.local");
  if (!WORKSPACE_PATHS.some(prefix => url.pathname === prefix || url.pathname.startsWith(`${prefix}/`))) return path;
  const organizationId = current.get("organization_id");
  if (organizationId && !url.searchParams.has("organization_id")) url.searchParams.set("organization_id", organizationId);
  const environment = current.get("environment");
  if (environment && ["legacy", "test", "live"].includes(environment) && !url.searchParams.has("environment")) url.searchParams.set("environment", environment);
  if (options.inheritFilters === false) return `${url.pathname}${url.search}${url.hash}`;
  if (!MONTHLY_PATHS.some(prefix => url.pathname === prefix || url.pathname.startsWith(`${prefix}/`))) return `${url.pathname}${url.search}${url.hash}`;
  if (!current.has("month") && current.get("from")) {
    const date = new Date(current.get("from")!);
    if (Number.isFinite(date.getTime())) current.set("month", new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit" }).format(date));
  }
  for (const key of ["month", "customer_id"]) {
    const value = current.get(key);
    if (value && !url.searchParams.has(key)) url.searchParams.set(key, value);
  }
  if (url.pathname === "/events") {
    for (const key of ["from", "to"]) {
      const value = current.get(key);
      if (value && !url.searchParams.has(key)) url.searchParams.set(key, value);
    }
    if (url.searchParams.has("from") || url.searchParams.has("to")) url.searchParams.delete("month");
  }
  return `${url.pathname}${url.search}${url.hash}`;
}
