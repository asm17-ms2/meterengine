"use client";

import { useWorkspaceSearch } from "@/components/workspace/use-workspace-search";

import { usePathname, useRouter } from "next/navigation";
import { useTenantAccess } from "@/components/auth/TenantAccess";
import { ENVIRONMENT_LABELS, environmentSwitchPath, readEnvironment } from "@/lib/environment";

export function EnvironmentSwitch() {
  const pathname = usePathname();
  const params = useWorkspaceSearch();
  const router = useRouter();
  const access = useTenantAccess();
  const environment = readEnvironment(params.get("environment"));
  return <label className={`workspace-environment workspace-environment-${environment}`}>
    <span>업무 환경</span>
    <select aria-label="업무 환경" value={environment} onChange={event => {
      const next = readEnvironment(event.target.value);
      if (!window.confirm("업무 환경을 바꾸면 현재 화면의 입력과 선택이 초기화됩니다. 계속할까요?")) return;
      const query = new URLSearchParams();
      if (access?.organizationId) query.set("organization_id", access.organizationId);
      if (params.get("month")) query.set("month", params.get("month")!);
      if (next !== "legacy") query.set("environment", next);
      const destination = environmentSwitchPath(pathname);
      router.push(`${destination}${query.size ? `?${query}` : ""}`);
    }}>
      {Object.entries(ENVIRONMENT_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
    </select>
  </label>;
}

export function EnvironmentInput() {
  const search = useWorkspaceSearch();
  const access = useTenantAccess();
  return <><input type="hidden" name="environment" value={readEnvironment(search.get("environment"))} /><input type="hidden" name="organization_id" value={access?.organizationId ?? search.get("organization_id") ?? ""} /></>;
}
