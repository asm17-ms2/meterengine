import type { Metadata } from "next";
import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import { readEnvironment } from "@/lib/environment";
import { scopeField } from "@/lib/auth/scope-fields";
import { ArchiveFilter } from "@/components/workspace/ArchiveFilter";
import { readArchiveStatus } from "@/lib/list-query";
import { workspaceFetch } from "@/lib/api/workspace";
import { WorkspaceHeader } from "@/components/workspace/WorkspaceHeader";
import { ActionPanel } from "@/components/workspace/ActionPanel";
import { WorkspaceError, WorkspaceTable } from "@/components/workspace/WorkspaceView";
import { EmptyState } from "@/components/screen/EmptyState";
import { StatusTag } from "@/components/screen/StatusTag";

export type ApiKeyResponse = { id: string; label: string; mode: string; scopes: string[]; last_four: string; expires_at: string | null; revoked_at: string | null; rate_limit_per_minute: number | null; status: "active" | "revoked" | "expired" };
type WebhookEndpointResponse = { id: string; url: string; event_types: string[]; enabled: boolean; created_at: string; archived_at?: string | null; latest_attempt_failed: boolean };
const WEBHOOK_TYPES = [{ value: "payment_succeeded", label: "결제 성공" }, { value: "payment_failed", label: "결제 실패" }, { value: "payment_canceled", label: "결제 취소" }, { value: "unbilled_usage_detected", label: "과금 제외 사용량" }, { value: "webhook_test", label: "시험 통보" }];
const KEY_STATUS_OPTIONS = [{ value: "active", label: "사용 중" }, { value: "revoked", label: "폐기됨" }, { value: "expired", label: "만료됨" }, { value: "all", label: "전체" }];

export const metadata: Metadata = { title: "API 키와 통보" };

export default async function IntegrationsPage({ searchParams }: { searchParams: Promise<Record<string, string | undefined>> }) {
  const params = await searchParams;
  const environment = readEnvironment(params.environment);
  const status = readArchiveStatus(params.status);
  const keyStatus = KEY_STATUS_OPTIONS.some(option => option.value === params.key_status) ? params.key_status : "active";
  const activeKeysRequest = workspaceFetch<{ api_keys: ApiKeyResponse[] }>("/v1/api-keys", { status: "active" });
  const [keys, activeKeys, webhooks] = await Promise.all([keyStatus === "active" ? activeKeysRequest : workspaceFetch<{ api_keys: ApiKeyResponse[] }>("/v1/api-keys", { status: keyStatus }), activeKeysRequest, workspaceFetch<{ endpoints: WebhookEndpointResponse[] }>("/v1/webhook-endpoints", { status })]);
  const keyRows = keys.ok ? keys.data.api_keys : [];
  return <>
    <WorkspaceHeader title="API 키와 통보" description="사용량을 보내고 고객부터 수납까지 조회할 때 쓸 API 키를 관리하고, 결제 결과와 과금 제외 사용량을 통보받을 주소를 등록하세요." />
    <section className="workspace-panel">
      <div className="workspace-heading"><div><h2>API 키</h2><p className="field-hint">API 키는 서버에서 API를 부를 때 써요. 사용자의 권한은 사용자 관리에서 정해요.</p></div><ActionPanel title="API 키 발급" operation="createApiKey" description="키는 발급 직후 한 번만 보여요. 서버의 비밀 저장소에 보관하세요." fields={[
        { name: "label", label: "키 이름", placeholder: "고객 포털 조회 서버", maxLength: 255 },
        ...(environment === "legacy" ? [{ name: "mode", label: "키 구분", value: "live", hint: "이 환경의 키는 테스트 데이터와 실제 데이터를 나누지 않아요. 데이터를 나눠 쓰려면 화면 위에서 테스트 환경이나 실제 환경으로 바꾼 뒤 발급하세요.", options: [{ value: "live", label: "운영 키 (live)" }, { value: "test", label: "테스트 키 (test)" }] }] : [{ name: "mode", label: "", type: "hidden" as const, value: environment }]),
        scopeField(["read"], "API 권한"),
        { name: "rate_limit_per_minute", label: "분당 요청 한도", type: "number", min: 1, max: 2147483647, isOptional: true, hint: "비우면 무제한이에요. 1 이상의 정수로 입력하세요. 한도를 넘은 요청도 일부 받을 수 있어요." },
        { name: "replaced_api_key_id", label: "교체할 기존 키", isOptional: true, options: activeKeys.ok ? activeKeys.data.api_keys.map(key => ({ value: key.id, label: `${key.label} (...${key.last_four})` })) : [], hint: "고르면 기존 키는 늦어도 24시간 뒤 만료돼요. 그 전에 서버의 키를 새 키로 바꾸고, 목록에서 기존 키의 만료 시각을 확인하세요." },
      ]} /></div>
      {environment !== "legacy" && <p className="field-hint">새 키는 지금 고른 환경에서만 쓸 수 있어요. 다른 환경의 데이터에는 접근할 수 없어요.</p>}
      <ArchiveFilter label="API 키 상태" parameterName="key_status" options={KEY_STATUS_OPTIONS} />
      {keys.ok ? <WorkspaceTable label="API 키 목록" rows={keyRows} columns={[{ key: "label", label: "이름" }, { key: "status", label: "키 상태", isStatus: true, statusLabels: { active: "사용 중", revoked: "폐기됨", expired: "만료됨" } }, { key: "mode", label: "API 키 구분", modeKind: "api" }, { key: "scopes", label: "API 권한" }, { key: "last_four", label: "끝 네 글자" }, { key: "rate_limit_per_minute", label: "분당 한도", emptyLabel: "무제한" }, { key: "expires_at", label: "만료 시각", isDate: true }, { key: "revoked_at", label: "폐기 시각", isDate: true }]} rowActions={Object.fromEntries(keyRows.filter(key => !key.revoked_at).map(key => [key.id, [...(key.status === "active" ? [{ title: "키 권한 변경", operation: "updateApiKeyScopes", description: `${key.label} (...${key.last_four}) 키의 권한만 바꿔요. 키 값과 만료 시각은 그대로이고, 다음 요청부터 적용돼요.`, fields: [{ name: "id", label: "", type: "hidden" as const, value: key.id }, scopeField(key.scopes, "API 권한")], label: "키 권한 저장" }] : []), { title: "키 폐기", operation: "deleteApiKey", description: `폐기하면 ${key.label} (...${key.last_four}) 키로 바로 요청할 수 없어요. 서버가 새 키로 바뀌었는지 먼저 확인하세요.`, fields: [{ name: "id", label: "", type: "hidden" as const, value: key.id }], isDanger: true }]]))} empty={keyStatus === "revoked" ? "폐기된 API 키가 없어요." : keyStatus === "expired" ? "만료된 API 키가 없어요." : keyStatus === "all" ? "발급한 API 키가 없어요. 쓸 권한을 정하고 키를 발급하세요." : "사용 중인 API 키가 없어요. 폐기됨이나 만료됨 목록을 확인하거나 새 키를 발급하세요."} /> : <WorkspaceError message={keys.error.message} status={keys.error.status} />}
      {!activeKeys.ok && keyStatus !== "active" && <WorkspaceError message={activeKeys.error.message} status={activeKeys.error.status} />}
    </section>
    <section className="workspace-panel">
      <div className="workspace-heading"><div><h2>통보 수신 주소</h2><p className="field-hint">수신 서버에서 서명을 검증하세요. 보내지 못한 통보는 자동으로 다시 보내고, 모든 시도를 발송 내역에 남겨요.</p></div><ActionPanel title="수신 주소 등록" operation="createWebhookEndpoint" description="서명 비밀값은 등록 직후 한 번만 보여요. 수신 서버에 안전하게 저장하세요." fields={[{ name: "url", label: "수신 URL", type: "url", placeholder: "https://service.example.com/webhooks/meterengine", maxLength: 2048 }, { name: "event_types", label: "받을 통보", isMultiple: true, value: "payment_succeeded,payment_failed,payment_canceled,unbilled_usage_detected", options: WEBHOOK_TYPES }]} /></div>
      <ArchiveFilter label="통보 관리 상태" />
      {webhooks.ok ? webhooks.data.endpoints.length ? <ul className="setup-list">{webhooks.data.endpoints.map(endpoint => <li key={endpoint.id}><div><strong>{endpoint.url}</strong><p>{endpoint.archived_at ? "보관됨" : endpoint.enabled ? "발송 중" : "발송 일시 중지"}{!endpoint.archived_at && endpoint.enabled && endpoint.latest_attempt_failed && <> <StatusTag color="error">최근 발송 실패</StatusTag></>} / {endpoint.event_types.map(type => WEBHOOK_TYPES.find(option => option.value === type)?.label ?? type).join(", ")}</p></div><Link href={`/integrations/${endpoint.id}`}>발송 내역과 설정</Link></li>)}</ul> : status === "archived" ? <EmptyState title="보관된 수신 주소가 없어요" /> : status === "all" ? <EmptyState title="등록한 수신 주소가 없어요" body="수신 주소를 등록하고 시험 통보로 연결을 확인하세요." /> : <EmptyState title="사용 중인 수신 주소가 없어요" body="보관 목록에서 복원하거나 새 수신 주소를 등록하세요." /> : <WorkspaceError message={webhooks.error.message} />}
    </section>
  </>;
}
