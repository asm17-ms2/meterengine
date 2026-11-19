import { Suspense } from "react";
import { Button } from "antd";
import { PageLinks } from "@/components/screen/ListPagination";
import { WorkspaceHeader } from "@/components/workspace/WorkspaceHeader";
import { WorkspaceError, WorkspaceTable } from "@/components/workspace/WorkspaceView";
import { EnvironmentInput } from "@/components/workspace/EnvironmentSwitch";
import { workspaceFetch } from "@/lib/api/workspace";
import { readMonth } from "@/lib/month";
import { MonthField } from "@/components/screen/MonthField";
import { formatKstDateTime } from "@/lib/format";
import type { Metadata } from "next";

export const metadata: Metadata = { title: "수집 거절" };

const ERRORS: Record<string, string> = { customer_not_found: "고객을 찾을 수 없음", customer_archived: "보관된 고객", event_too_old: "수집 가능 기간 초과", validation_error: "요청 값 오류" };
type Rejection = { id: string; transaction_id: string | null; customer_id: string | null; type: string | null; occurred_at: string | null; error_code: string; error_fields: string[]; request_id: string | null; batch_index: number | null; recorded_at: string };
type Rejections = { month: string; recording_started_at: string | null; total: number; page: number; size: number; rejections: Rejection[] };

export default async function EventRejectionsPage({ searchParams }: { searchParams: Promise<Record<string, string | undefined>> }) {
  const params = await searchParams;
  const month = readMonth(params.month);
  const page = /^\d+$/.test(params.page ?? "") ? Math.min(100_000, Number(params.page)) : 0;
  const size = [20, 50, 100].includes(Number(params.size)) ? Number(params.size) : 20;
  const result = await workspaceFetch<Rejections>("/v1/event-rejections/search", { month, customer_id: params.customer_id || undefined, error_code: params.error_code || undefined, q: params.q, page: String(page), size: String(size) });
  const source = new URLSearchParams(Object.entries(params).filter((entry): entry is [string, string] => typeof entry[1] === "string"));
  const rows = result.ok ? result.data.rejections.map(rejection => ({ ...rejection, error_label: ERRORS[rejection.error_code] ?? rejection.error_code, batch_row: rejection.batch_index === null ? null : rejection.batch_index + 1 })) : [];
  return <>
    <WorkspaceHeader title="수집 거절" description="받을 때 거절해서 저장하지 않은 이벤트예요. 거절 사유를 확인하세요." />
    <section className="workspace-panel">
      <form method="get" className="workspace-actions query-row">
        <Suspense><EnvironmentInput /></Suspense>
        <MonthField key={month} month={month} label="조회 월" />
        <label className="workspace-field"><span>거절 사유</span><select name="error_code" defaultValue={params.error_code ?? ""}><option value="">전체</option>{Object.entries(ERRORS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        <label className="workspace-field"><span>요청에 담긴 고객 ID (선택)</span><input name="customer_id" defaultValue={params.customer_id ?? ""} maxLength={36} aria-describedby="rejection-customer-hint" /></label>
        <label className="workspace-field"><span>전송 ID와 유형 검색</span><input name="q" defaultValue={params.q ?? ""} maxLength={255} /></label>
        <label className="workspace-field"><span>표시 건수</span><select name="size" defaultValue={size}>{[20, 50, 100].map(value => <option key={value} value={value}>{value}건</option>)}</select></label>
        <Button type="primary" htmlType="submit">조건 적용</Button>
      </form>
      <p id="rejection-customer-hint" className="field-hint">요청 본문의 고객 ID(UUID)로 찾아요. 등록되지 않은 ID로도 찾을 수 있어요.</p>
      {result.ok ? <>
        <p role="status">선택한 조건의 수집 거절 {result.data.total.toLocaleString("ko-KR")}건</p>
        {result.data.recording_started_at && <p className="muted">{formatKstDateTime(result.data.recording_started_at)} KST부터 거절을 기록하고 있어요. 그 전의 거절은 이 목록에 없어요.</p>}
        <WorkspaceTable serverPaged rows={rows} columns={[{ key: "transaction_id", label: "전송 ID" }, { key: "error_label", label: "거절 사유" }, { key: "error_fields", label: "확인할 항목" }, { key: "customer_id", label: "요청 고객 ID", isId: true }, { key: "type", label: "이벤트 유형" }, { key: "batch_row", label: "묶음 안 순서", isNumber: true }, { key: "occurred_at", label: "이벤트 발생 시각", isDate: true }, { key: "recorded_at", label: "거절 기록 시각", isDate: true }, { key: "request_id", label: "요청 ID", isId: true }]} empty="선택한 조건의 수집 거절 기록이 없어요." />
        <PageLinks label="수집 거절 페이지" pathname="/event-rejections" params={Object.fromEntries(source)} page={page} size={size} total={result.data.total} />
      </> : <WorkspaceError message={result.error.message} />}
      <p className="field-hint">거절된 이벤트의 원문과 속성은 저장하지 않아요. 조회 월은 거절을 기록한 달이에요.</p>
    </section>
  </>;
}
