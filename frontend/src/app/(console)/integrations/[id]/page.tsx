import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import { workspaceFetch } from "@/lib/api/workspace";
import { WorkspaceHeader } from "@/components/workspace/WorkspaceHeader";
import { ActionPanel } from "@/components/workspace/ActionPanel";
import { WorkspaceError, WorkspaceTable } from "@/components/workspace/WorkspaceView";
import { PageLinks } from "@/components/screen/ListPagination";

type WebhookDeliveryResponse = Record<string, unknown> & { id: string; status: string; type: string };
const EVENT_TYPES = [{ value: "payment_succeeded", label: "결제 성공" }, { value: "payment_failed", label: "결제 실패" }, { value: "payment_canceled", label: "결제 취소" }, { value: "unbilled_usage_detected", label: "과금 제외 사용량" }, { value: "webhook_test", label: "테스트 웹훅" }];
const DELIVERY_STATES = { waiting: "발송 대기", retry: "재시도 대기", done: "발송 완료", failed: "발송 실패" };
const FAILURE_REASONS: Record<string, string> = { timeout: "응답 시간 초과", connection_error: "연결 실패", unexpected_status: "2xx가 아닌 응답", blocked_address: "허용되지 않는 주소", endpoint_disabled: "발송을 멈춘 웹훅 URL", lease_expired: "발송 결과를 기록하지 못함", secret_unavailable: "서명 비밀값을 읽지 못함" };
const eventLabel = (type: string) => EVENT_TYPES.find(option => option.value === type)?.label ?? type;
const deliveryState = (delivery: WebhookDeliveryResponse) => delivery.status === "pending" ? (Number(delivery.attempt_count) > 0 ? "retry" : "waiting") : delivery.status;

export default async function WebhookDetailPage({ params, searchParams }: { params: Promise<{ id: string }>; searchParams: Promise<Record<string, string | undefined>> }) {
  const { id } = await params;
  const rawPage = (await searchParams).page;
  const page = rawPage && /^\d+$/.test(rawPage) ? Math.min(Number(rawPage), 1_000_000) : 0;
  const [endpoint, deliveries] = await Promise.all([workspaceFetch<{ url: string; enabled: boolean; event_types: string[]; description: string | null; archived_at?: string | null }>(`/v1/webhook-endpoints/${encodeURIComponent(id)}`), workspaceFetch<{ total: number; deliveries: WebhookDeliveryResponse[] }>(`/v1/webhook-endpoints/${encodeURIComponent(id)}/deliveries`, { page: String(page), size: "20" })]);
  if (!endpoint.ok || !deliveries.ok) return <WorkspaceError message={!endpoint.ok ? endpoint.error.message : !deliveries.ok ? deliveries.error.message : "조회하지 못했어요."} />;
  return <>
    <WorkspaceHeader title="웹훅 발송 내역" description={endpoint.data.url}><Link href={`/integrations?status=${endpoint.data.archived_at ? "archived" : "active"}`}>API 키와 웹훅으로</Link>
      {endpoint.data.archived_at ? <ActionPanel title="웹훅 URL 복원" operation="restoreWebhookEndpoint" description="웹훅 URL을 복원해요. 발송 상태와 받을 웹훅을 확인한 뒤 테스트 웹훅으로 연결을 점검하세요." fields={[{ name: "id", label: "", type: "hidden", value: id }]} /> : <>
      <ActionPanel title="테스트 웹훅 보내기" triggerType="primary" operation="testWebhookEndpoint" description="테스트 웹훅을 곧 보내요. 수신 서버가 실제로 받았는지, 서명 검증이 되는지 확인하세요." fields={[{ name: "id", label: "", type: "hidden", value: id }]} />
      <ActionPanel title="웹훅 설정 수정" operation="updateWebhookEndpoint" fields={[{ name: "id", label: "", type: "hidden", value: id }, { name: "url", label: "웹훅 URL", type: "url", value: endpoint.data.url }, { name: "event_types", label: "받을 웹훅", isMultiple: true, value: endpoint.data.event_types.join(","), options: EVENT_TYPES }, { name: "enabled", label: "발송 상태", value: String(endpoint.data.enabled), options: [{ value: "true", label: "발송 중" }, { value: "false", label: "발송 일시 중지" }] }, { name: "description", label: "", type: "hidden", value: endpoint.data.description ?? "" }]} />
      <ActionPanel title="웹훅 URL 보관" operation="deleteWebhookEndpoint" isDanger description="이 URL로 보내던 웹훅을 멈추고 보관 목록으로 옮겨요. 지난 발송 내역은 남고, 나중에 복원할 수 있어요." fields={[{ name: "id", label: "", type: "hidden", value: id }]} />
    </>}
    </WorkspaceHeader>
    {endpoint.data.archived_at && <p role="status">보관된 웹훅 URL이에요. 웹훅을 보내지 않고, 지난 발송 내역은 남아 있어요.</p>}
    <section className="workspace-panel"><p className="field-hint">총 {deliveries.data.total.toLocaleString("ko-KR")}건</p><WorkspaceTable rows={deliveries.data.deliveries.map(delivery => ({ ...delivery, type_label: eventLabel(delivery.type), state: deliveryState(delivery), failure_label: typeof delivery.failure_reason === "string" ? FAILURE_REASONS[delivery.failure_reason] ?? delivery.failure_reason : null }))} columns={[{ key: "type_label", label: "종류" }, { key: "state", label: "상태", isStatus: true, statusLabels: DELIVERY_STATES }, { key: "attempt_count", label: "시도 수", isNumber: true }, { key: "response_status", label: "수신 응답" }, { key: "failure_label", label: "실패 사유" }, { key: "next_attempt_at", label: "다음 발송", isDate: true }]} rowActions={Object.fromEntries((endpoint.data.archived_at ? [] : deliveries.data.deliveries).filter(delivery => delivery.status === "failed").map(delivery => [delivery.id, [{ title: "다시 보내기", operation: "retryWebhookDelivery", description: `${eventLabel(delivery.type)} 웹훅을 같은 webhook-id로 다시 보내요. 웹훅 URL과 실패 사유를 먼저 확인하세요. 같은 webhook-id는 수신 서버에서 한 번만 처리하세요.`, fields: [{ name: "id", label: "", type: "hidden" as const, value: id }, { name: "delivery_id", label: "", type: "hidden" as const, value: delivery.id }] }]]))} empty="발송한 웹훅이 없어요. 테스트 웹훅을 보내 연결을 확인하세요." /><PageLinks label="발송 내역 페이지" pathname={`/integrations/${encodeURIComponent(id)}`} params={{}} page={page} size={20} total={deliveries.data.total} /></section>
  </>;
}
