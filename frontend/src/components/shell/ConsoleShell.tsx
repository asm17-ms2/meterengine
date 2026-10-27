"use client";
import { Suspense } from "react";
import { TenantAccessProvider } from "@/components/auth/TenantAccess";
import { LogoutButton } from "@/components/auth/SessionControls";
import { NavMenu } from "@/components/shell/NavMenu";
import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import type { OrganizationRole } from "@/lib/auth/permissions";
export function ConsoleShell({ organizationId, organizationLabel, userLabel, isOperator, organizationRole, scopes, children }: { organizationId: string; organizationLabel: string; userLabel: string; isOperator: boolean; organizationRole?: OrganizationRole | null; scopes: string[]; children: React.ReactNode }) {
 return <TenantAccessProvider organizationId={organizationId} scopes={scopes} organizationRole={organizationRole}><Suspense><header><Link href="/customers">MeterEngine</Link><Link href="/organizations">{organizationLabel}</Link><span>{userLabel}</span>{isOperator && <Link href="/admin/organizations">플랫폼 운영</Link>}<LogoutButton /></header><aside><NavMenu /></aside><main id="main-content">{children}</main></Suspense></TenantAccessProvider>;
}
