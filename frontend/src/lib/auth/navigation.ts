const TENANT_PATHS = ["/billable-metric-prices", "/billable-metrics", "/billing", "/customers", "/events", "/integrations", "/invoices", "/members", "/organizations", "/usage"];

export function safeReturnPath(raw: unknown, isOperator: boolean): string {
  const fallback = isOperator ? "/admin/organizations" : "/customers";
  if (typeof raw !== "string" || !raw.startsWith("/") || raw.startsWith("//") || /[\\\s#]/.test(raw) || raw.split("?")[0].includes("%")) return fallback;
  try {
    const url = new URL(raw, "https://console.invalid");
    if (url.origin !== "https://console.invalid") return fallback;
    const isTenantPath = TENANT_PATHS.includes(url.pathname) || false;
    const isInvitationPath = url.pathname === "/organization-invitations/continue";
    const isAdminPath = isOperator && ["/admin", "/admin/organizations", "/admin/users"].includes(url.pathname);
    return isTenantPath || isAdminPath || isInvitationPath ? url.pathname + url.search : fallback;
  } catch {
    return fallback;
  }
}

export function returnOrganizationId(path: string): string | null {
  return new URL(path, "https://console.invalid").searchParams.get("organization_id");
}

export function withoutOrganizationId(path: string): string {
  const url = new URL(path, "https://console.invalid");
  url.searchParams.delete("organization_id");
  return url.pathname + url.search;
}
