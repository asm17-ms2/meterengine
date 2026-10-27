const TENANT_PATHS = ["/billable-metric-prices", "/billable-metrics", "/billing", "/customers", "/events", "/integrations", "/invoices", "/members", "/organizations", "/customers", "/payments", "/pricing", "/settings", "/usage"];

export function safeReturnPath(raw: unknown, isOperator: boolean): string {
  const fallback = isOperator ? "/admin" : "/customers";
  if (typeof raw !== "string" || !raw.startsWith("/") || raw.startsWith("//") || /[\\\s#]/.test(raw) || raw.split("?")[0].includes("%")) return fallback;
  try {
    const url = new URL(raw, "https://console.invalid");
    if (url.origin !== "https://console.invalid") return fallback;
    const isTenantPath = TENANT_PATHS.includes(url.pathname) || /^\/(payments|integrations)\/[0-9a-f-]{36}$/.test(url.pathname);
    const isInvitationPath = url.pathname === "/organization-invitations/continue";
    const isAdminPath = isOperator && ["/admin", "/admin/organizations", "/admin/users"].includes(url.pathname);
    return isTenantPath || isAdminPath || isInvitationPath ? url.pathname + url.search : fallback;
  } catch {
    return fallback;
  }
}
