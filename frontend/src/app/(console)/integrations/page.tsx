import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import { scopeField } from "@/lib/auth/scope-fields";
import { ArchiveFilter } from "@/components/workspace/ArchiveFilter";
import { readArchiveStatus } from "@/lib/list-query";
import { workspaceFetch } from "@/lib/api/workspace";
import { WorkspaceHeader } from "@/components/workspace/WorkspaceHeader";
import { ActionPanel } from "@/components/workspace/ActionPanel";
import { WorkspaceError, WorkspaceTable } from "@/components/workspace/WorkspaceView";

export type ApiKeyResponse = { id: string; label: string; mode: string; scopes: string[]; last_four: string; expires_at: string | null; revoked_at: string | null; rate_limit_per_minute: number | null; status: "active" | "revoked" | "expired" };
type WebhookEndpointResponse = { id: string; url: string; event_types: string[]; enabled: boolean; created_at: string; archived_at?: string | null };
const WEBHOOK_TYPES = [{ value: "payment_succeeded", label: "결제 성공" }, { value: "payment_failed", label: "결제 실패" }, { value: "payment_canceled", label: "결제 취소" }, { value: "unbilled_usage_detected", label: "과금 제외 사용량" }, { value: "webhook_test", label: "시험 통보" }];
const KEY_STATUS_OPTIONS = [{ value: "active", label: "사용 중" }, { value: "revoked", label: "폐기됨" }, { value: "expired", label: "만료됨" }, { value: "all", label: "전체" }];

export default async function IntegrationsPage({ searchParams }: { searchParams: Promise<Record<string, string | undefined>> }) {
  const params = await searchParams;
  const status = readArchiveStatus(params.status);
  const keyStatus = KEY_STATUS_OPTIONS.some(option => option.value === params.key_status) ? params.key_status : "active";
  const activeKeysRequest = workspaceFetch<{ api_keys: ApiKeyResponse[] }>("/v1/api-keys", { status: "active" });
  const [keys, activeKeys, webhooks] = await Promise.all([keyStatus === "active" ? activeKeysRequest : workspaceFetch<{ api_keys: ApiKeyResponse[] }>("/v1/api-keys", { status: keyStatus }), activeKeysRequest, workspaceFetch<{ endpoints: WebhookEndpointResponse[] }>("/v1/webhook-endpoints", { status })]);
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
      <ArchiveFilter label="API 키 상태" parameterName="key_status" options={KEY_STATUS_OPTIONS} />
      {keys.ok ? <WorkspaceTable rows={keyRows} columns={[{ key: "label", label: "이름" }, { key: "status", label: "키 상태", isStatus: true, statusLabels: { active: "사용 중", revoked: "폐기됨", expired: "만료됨" } }, { key: "mode", label: "API 키 구분", modeKind: "api" }, { key: "scopes", label: "API 권한" }, { key: "last_four", label: "끝 네 글자" }, { key: "rate_limit_per_minute", label: "분당 한도 (미지정은 무제한)" }, { key: "expires_at", label: "만료 시각", isDate: true }, { key: "revoked_at", label: "폐기 시각", isDate: true }]} rowActions={Object.fromEntries(keyRows.filter(key => !key.revoked_at).map(key => [key.id, [...(key.status === "active" ? [{ title: "키 권한 변경", operation: "updateApiKeyScopes", description: `${key.label} (...${key.last_four}) 키의 권한만 변경합니다. 기존 키 값과 만료 시각은 유지되며 다음 요청부터 적용됩니다.`, fields: [{ name: "id", label: "", type: "hidden" as const, value: key.id }, scopeField(key.scopes, "API 권한")], label: "키 권한 저장" }] : []), { title: "키 폐기", operation: "deleteApiKey", description: `${key.label} (...${key.last_four}) 키를 폐기합니다. 즉시 이 키로 요청할 수 없습니다. 연동 서버가 새 키를 사용하는지 확인하세요.`, fields: [{ name: "id", label: "", type: "hidden" as const, value: key.id }], isDanger: true }]]))} empty={keyStatus === "revoked" ? "폐기된 API 키가 없습니다." : keyStatus === "expired" ? "만료된 API 키가 없습니다." : keyStatus === "all" ? "발급한 API 키가 없습니다. 사용할 권한을 정하고 키를 발급하세요." : "사용 중인 API 키가 없습니다. 폐기/만료 목록을 확인하거나 새 키를 발급하세요."} /> : <WorkspaceError message={keys.error.message} />}
      {!activeKeys.ok && keyStatus !== "active" && <WorkspaceError message={activeKeys.error.message} />}
    </section>
    <section className="workspace-panel">
      <div className="workspace-heading"><div><h2>처리 결과 통보</h2><p className="field-hint">수신 서버에서 서명을 검증하세요. 발송 실패는 재시도하며 모든 시도를 기록합니다.</p></div><ActionPanel title="수신 주소 등록" operation="createWebhookEndpoint" description="서명 비밀값은 등록 직후 한 번만 표시됩니다. 수신 서버에 안전하게 저장하세요." fields={[{ name: "url", label: "수신 URL", type: "url", placeholder: "https://service.example.com/webhooks/meterengine", maxLength: 2048 }, { name: "event_types", label: "받을 통보", isMultiple: true, value: "payment_succeeded,payment_failed,payment_canceled,unbilled_usage_detected", options: WEBHOOK_TYPES }]} /></div>
      <ArchiveFilter label="통보 관리 상태" />
      {webhooks.ok ? webhooks.data.endpoints.length ? <ul className="setup-list">{webhooks.data.endpoints.map(endpoint => <li key={endpoint.id}><div><strong>{endpoint.url}</strong><p>{endpoint.archived_at ? "보관됨" : endpoint.enabled ? "발송 중" : "일시 중지"} / {endpoint.event_types.map(type => WEBHOOK_TYPES.find(option => option.value === type)?.label ?? type).join(", ")}</p></div><Link href={`/integrations/${endpoint.id}`}>발송 내역과 설정</Link></li>)}</ul> : <p className="muted">{status === "archived" ? "보관된 수신 주소가 없습니다." : status === "all" ? "등록한 수신 주소가 없습니다. 수신 주소를 등록하고 시험 통보로 연결을 확인하세요." : "사용 중인 수신 주소가 없습니다. 보관 목록에서 복원하거나 새 수신 주소를 등록하세요."}</p> : <WorkspaceError message={webhooks.error.message} />}
    </section>
  </>;
}
