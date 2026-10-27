import { scopeField } from "@/lib/auth/scope-fields";
import { workspaceFetch } from "@/lib/api/workspace";
import { WorkspaceHeader } from "@/components/workspace/WorkspaceHeader";
import { ActionPanel } from "@/components/workspace/ActionPanel";
import { WorkspaceError, WorkspaceTable } from "@/components/workspace/WorkspaceView";

export type ApiKeyResponse = { id: string; label: string; mode: string; scopes: string[]; last_four: string; expires_at: string | null; revoked_at: string | null; rate_limit_per_minute: number | null; status: "active" | "revoked" | "expired" };
const KEY_STATUS_OPTIONS = [{ value: "active", label: "사용 중" }, { value: "revoked", label: "폐기됨" }, { value: "expired", label: "만료됨" }, { value: "all", label: "전체" }];

export default async function IntegrationsPage({ searchParams }: { searchParams: Promise<Record<string, string | undefined>> }) {
  const params = await searchParams;
  const keyStatus = KEY_STATUS_OPTIONS.some(option => option.value === params.key_status) ? params.key_status : "active";
  const activeKeysRequest = workspaceFetch<{ api_keys: ApiKeyResponse[] }>("/v1/api-keys", { status: "active" });
  const [keys, activeKeys] = await Promise.all([keyStatus === "active" ? activeKeysRequest : workspaceFetch<{ api_keys: ApiKeyResponse[] }>("/v1/api-keys", { status: keyStatus }), activeKeysRequest]);
  const keyRows = keys.ok ? keys.data.api_keys : [];
  return <>
    <WorkspaceHeader title="API 키와 통보" kicker="도입사 콘솔" description="사용량 전송과 청구 조회에 사용할 서버 연동 키를 관리하고, 처리 결과를 웹훅으로 받으세요." />
    <section className="workspace-panel">
      <div className="workspace-heading"><div><h2>서버 연동 키</h2><p className="field-hint">API 키는 서버의 권한입니다. 사람의 관리 권한은 사용자 관리에서 정합니다.</p></div><ActionPanel title="API 키 발급" operation="createApiKey" description="원문은 발급 직후 한 번만 표시됩니다. 도입사 서버의 비밀 저장소에 보관하세요." fields={[
        { name: "label", label: "키 이름", placeholder: "고객 포털 조회 서버", maxLength: 255 },
        ...([{ name: "mode", label: "기존 키 구분", value: "live", hint: "기존 데이터 환경의 키입니다. 여기서 test/live 구분은 데이터 격리를 뜻하지 않습니다. 격리된 키는 위의 업무 환경을 전환해 발급하세요.", options: [{ value: "live", label: "기존 운영 키 (live)" }, { value: "test", label: "기존 테스트 키 (test)" }] }]),
        scopeField(["read"], "API 권한"),
        { name: "rate_limit_per_minute", label: "분당 요청 한도", type: "number", min: 1, isOptional: true, hint: "비우면 무제한입니다. 1 이상의 정수로 입력하며 백엔드 인스턴스별로 적용합니다." },
        { name: "replaced_api_key_id", label: "교체할 기존 키", isOptional: true, options: activeKeys.ok ? activeKeys.data.api_keys.map(key => ({ value: key.id, label: `${key.label} (...${key.last_four})` })) : [], hint: "선택하면 기존 키는 교체 유예 시간이 지난 뒤 만료됩니다. 새 키를 서버에 적용한 뒤 기존 키의 만료 시각을 확인하세요." },
      ]} /></div>
      <form><label>API 키 상태<select name="key_status" defaultValue={keyStatus}>{KEY_STATUS_OPTIONS.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label><button>조회</button></form>
      {keys.ok ? <WorkspaceTable rows={keyRows} columns={[{ key: "label", label: "이름" }, { key: "status", label: "키 상태", isStatus: true, statusLabels: { active: "사용 중", revoked: "폐기됨", expired: "만료됨" } }, { key: "mode", label: "API 키 구분", modeKind: "api" }, { key: "scopes", label: "API 권한" }, { key: "last_four", label: "끝 네 글자" }, { key: "rate_limit_per_minute", label: "분당 한도 (미지정은 무제한)" }, { key: "expires_at", label: "만료 시각", isDate: true }, { key: "revoked_at", label: "폐기 시각", isDate: true }]} rowActions={Object.fromEntries(keyRows.filter(key => !key.revoked_at).map(key => [key.id, [...(key.status === "active" ? [{ title: "키 권한 변경", operation: "updateApiKeyScopes", description: `${key.label} (...${key.last_four}) 키의 권한만 변경합니다. 기존 키 값과 만료 시각은 유지되며 다음 요청부터 적용됩니다.`, fields: [{ name: "id", label: "", type: "hidden" as const, value: key.id }, scopeField(key.scopes, "API 권한")], label: "키 권한 저장" }] : []), { title: "키 폐기", operation: "deleteApiKey", description: `${key.label} (...${key.last_four}) 키를 폐기합니다. 즉시 이 키로 요청할 수 없습니다. 연동 서버가 새 키를 사용하는지 확인하세요.`, fields: [{ name: "id", label: "", type: "hidden" as const, value: key.id }], isDanger: true }]]))} empty={keyStatus === "revoked" ? "폐기된 API 키가 없습니다." : keyStatus === "expired" ? "만료된 API 키가 없습니다." : keyStatus === "all" ? "발급한 API 키가 없습니다. 사용할 권한을 정하고 키를 발급하세요." : "사용 중인 API 키가 없습니다. 폐기/만료 목록을 확인하거나 새 키를 발급하세요."} /> : <WorkspaceError message={keys.error.message} />}
      {!activeKeys.ok && keyStatus !== "active" && <WorkspaceError message={activeKeys.error.message} />}
    </section>
  </>;
}
