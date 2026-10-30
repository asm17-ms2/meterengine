"use client";

import { useWorkspaceSearch } from "@/components/workspace/use-workspace-search";

import { DownOutlined } from "@ant-design/icons";
import { App, Select } from "antd";
import { usePathname, useRouter } from "next/navigation";
import { useTenantAccess } from "@/components/auth/TenantAccess";
import { ENVIRONMENT_LABELS, ENVIRONMENT_SWITCH_TITLES, environmentSwitchPath, readEnvironment } from "@/lib/environment";

export function EnvironmentSwitch() {
  const pathname = usePathname();
  const params = useWorkspaceSearch();
  const router = useRouter();
  const access = useTenantAccess();
  const { modal } = App.useApp();
  const environment = readEnvironment(params.get("environment"));
  function confirmSwitch(value: string) {
    const next = readEnvironment(value);
    if (next === environment) return;
    modal.confirm({
      title: ENVIRONMENT_SWITCH_TITLES[next],
      content: "현재 화면의 입력과 선택이 초기화됩니다.",
      okText: "전환",
      cancelText: "취소",
      onOk: () => {
        const query = new URLSearchParams();
        if (access?.organizationId) query.set("organization_id", access.organizationId);
        if (params.get("month")) query.set("month", params.get("month")!);
        if (next !== "legacy") query.set("environment", next);
        const destination = environmentSwitchPath(pathname);
        router.push(`${destination}${query.size ? `?${query}` : ""}`);
      },
    });
  }
  return <div className={`workspace-environment workspace-environment-${environment}`}>
    <span aria-hidden="true">업무 환경</span>
    <Select<string>
      aria-label="업무 환경"
      value={environment}
      status={environment === "live" ? "warning" : undefined}
      suffixIcon={<DownOutlined aria-hidden aria-label={undefined} />}
      options={Object.entries(ENVIRONMENT_LABELS).map(([value, label]) => ({ value, label }))}
      onChange={confirmSwitch}
    />
  </div>;
}

export function EnvironmentInput() {
  const search = useWorkspaceSearch();
  const access = useTenantAccess();
  return <><input type="hidden" name="environment" value={readEnvironment(search.get("environment"))} /><input type="hidden" name="organization_id" value={access?.organizationId ?? search.get("organization_id") ?? ""} /></>;
}
