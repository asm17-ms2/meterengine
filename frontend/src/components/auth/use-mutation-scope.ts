"use client";

import { useWorkspaceSearch } from "@/components/workspace/use-workspace-search";

import { useState } from "react";

import { useTenantAccess } from "@/components/auth/TenantAccess";
import { readEnvironment } from "@/lib/environment";

export function useMutationScope() {
  const access = useTenantAccess();
  const query = useWorkspaceSearch();
  const [scope] = useState(() => ({ organizationId: access?.organizationId ?? query.get("organization_id") ?? "", environment: readEnvironment(query.get("environment")) }));
  return (data: FormData) => {
    data.set("organization_id", scope.organizationId);
    data.set("environment", scope.environment);
    return data;
  };
}
