"use client";

import { useWorkspaceSearch } from "@/components/workspace/use-workspace-search";

import { DownOutlined } from "@ant-design/icons";
import { App, Select } from "antd";
import { usePathname, useRouter } from "next/navigation";
import { useTenantAccess } from "@/components/auth/TenantAccess";
import { ENVIRONMENT_LABELS, ENVIRONMENT_SWITCH_TITLES, environmentOptions, environmentQueryValue, environmentSwitchPath, isLockedEnvironment, LIVE_ENVIRONMENT_PATH, readEnvironment } from "@/lib/environment";

export function EnvironmentSwitch({ showLegacy, isLiveEnvironmentOpen }: { showLegacy: boolean; isLiveEnvironmentOpen: boolean }) {
  const pathname = usePathname();
  const params = useWorkspaceSearch();
  const router = useRouter();
  const access = useTenantAccess();
  const { modal } = App.useApp();
  const environment = readEnvironment(params.get("environment"));
  function confirmSwitch(value: string) {
    const next = readEnvironment(value);
    if (next === environment) return;
    // 잠긴 실제 환경을 고르면 전환하지 않고 이용 신청 화면을 연다.
    if (isLockedEnvironment(next, isLiveEnvironmentOpen)) {
      modal.confirm({
        title: "실제 환경 이용 신청 화면으로 갈까요?",
        content: "실제 환경은 이용 신청이 승인되면 쓸 수 있어요. 이 화면에서 입력하거나 고른 내용은 지워져요.",
        okText: "이동",
        cancelText: "취소",
        onOk: () => router.push(`${LIVE_ENVIRONMENT_PATH}${access?.organizationId ? `?${new URLSearchParams({ organization_id: access.organizationId })}` : ""}`),
      });
      return;
    }
    modal.confirm({
      title: ENVIRONMENT_SWITCH_TITLES[next],
      content: "이 화면에서 입력하거나 고른 내용은 지워져요.",
      okText: "전환",
      cancelText: "취소",
      onOk: () => {
        const query = new URLSearchParams();
        if (access?.organizationId) query.set("organization_id", access.organizationId);
        if (params.get("month")) query.set("month", params.get("month")!);
        const value = environmentQueryValue(next);
        if (value) query.set("environment", value);
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
      options={environmentOptions(showLegacy && isLiveEnvironmentOpen).map(value => ({ value, label: isLockedEnvironment(value, isLiveEnvironmentOpen) ? `${ENVIRONMENT_LABELS[value]} (승인 전)` : ENVIRONMENT_LABELS[value] }))}
      onChange={confirmSwitch}
    />
  </div>;
}

// GET 조회 폼 전용이라 기본 환경은 주소에 적지 않는다. 서버 액션 폼은 ActionPanel이 환경 값을 늘 넣는다.
export function EnvironmentInput() {
  const search = useWorkspaceSearch();
  const access = useTenantAccess();
  const environment = environmentQueryValue(readEnvironment(search.get("environment")));
  return <>{environment && <input type="hidden" name="environment" value={environment} />}<input type="hidden" name="organization_id" value={access?.organizationId ?? search.get("organization_id") ?? ""} /></>;
}
