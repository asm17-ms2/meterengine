"use client";

import { createContext, useContext } from "react";
import { hasScope, type AccessScope, type OrganizationRole } from "@/lib/auth/permissions";

type TenantAccessValue = { organizationId?: string; scopes: readonly string[]; organizationRole?: OrganizationRole | null };
const TenantAccessContext = createContext<TenantAccessValue | null>(null);

export function TenantAccessProvider({ organizationId, scopes, organizationRole, children }: TenantAccessValue & { children: React.ReactNode }) {
  return <TenantAccessContext.Provider value={{ organizationId, scopes, organizationRole }}>{children}</TenantAccessContext.Provider>;
}

export function useTenantAccess() {
  return useContext(TenantAccessContext);
}

export function useTenantScope(scope: AccessScope) {
  return hasScope(useTenantAccess()?.scopes, scope);
}

export function TenantScope({ scope, children }: { scope: AccessScope; children: React.ReactNode }) {
  return useTenantScope(scope) ? children : null;
}
