"use client";

import { useSearchParams } from "next/navigation";
import { useTenantAccess } from "@/components/auth/TenantAccess";

export function useWorkspaceSearch() {
  const params = useSearchParams();
  const access = useTenantAccess();
  const search = new URLSearchParams(params.toString());
  if (!search.get("organization_id") && access?.organizationId) search.set("organization_id", access.organizationId);
  return search;
}
