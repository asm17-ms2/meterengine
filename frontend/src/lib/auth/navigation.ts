const TENANT_PATHS = ["/audit", "/billable-metric-prices", "/billable-metrics", "/billing", "/customers", "/event-issues", "/event-rejections", "/events", "/integrations", "/invoices", "/members", "/organizations", "/overview", "/payments", "/pricing", "/settings", "/usage"];

export function safeReturnPath(raw: unknown, isOperator: boolean): string {
  const fallback = isOperator ? "/admin" : "/overview";
  if (typeof raw !== "string" || !raw.startsWith("/") || raw.startsWith("//") || /[\\\s#]/.test(raw) || raw.split("?")[0].includes("%")) return fallback;
  try {
    const url = new URL(raw, "https://console.invalid");
    if (url.origin !== "https://console.invalid") return fallback;
    const isTenantPath = TENANT_PATHS.includes(url.pathname) || /^\/(customers|payments|integrations)\/[0-9a-f-]{36}$/.test(url.pathname);
    const isInvitationPath = url.pathname === "/organization-invitations/continue";
    const isAdminPath = isOperator && ["/admin", "/admin/organizations", "/admin/users", "/admin/audit"].includes(url.pathname);
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

export function expiredLoginHref(raw: string | null): string {
  const path = typeof raw === "string" ? safeReturnPath(raw, true) : null;
  return path && path === raw ? `/login?reason=expired&returnTo=${encodeURIComponent(path)}` : "/login?reason=expired";
}
