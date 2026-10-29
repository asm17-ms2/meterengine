import { Suspense } from "react";
import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import { WorkspaceHeader } from "@/components/workspace/WorkspaceHeader";
import { WorkspaceError, WorkspaceTable } from "@/components/workspace/WorkspaceView";
import { EnvironmentInput } from "@/components/workspace/EnvironmentSwitch";
import { workspaceFetch } from "@/lib/api/workspace";
import { readMonth } from "@/lib/month";
import { formatKstDateTime } from "@/lib/format";

const ERRORS: Record<string, string> = { customer_not_found: "고객을 찾을 수 없음", customer_archived: "보관된 고객", event_too_old: "수집 가능 기간 초과", validation_error: "요청 항목 확인 필요" };
type Rejection = { id: string; transaction_id: string | null; customer_id: string | null; type: string | null; occurred_at: string | null; error_code: string; error_fields: string[]; request_id: string | null; batch_index: number | null; recorded_at: string };
type Rejections = { month: string; recording_started_at: string | null; total: number; page: number; size: number; rejections: Rejection[] };

export default async function EventRejectionsPage({ searchParams }: { searchParams: Promise<Record<string, string | undefined>> }) {
  const params = await searchParams;
  const month = readMonth(params.month);
  const page = /^\d+$/.test(params.page ?? "") ? Math.min(100_000, Number(params.page)) : 0;
  const size = [20, 50, 100].includes(Number(params.size)) ? Number(params.size) : 20;
  const result = await workspaceFetch<Rejections>("/v1/event-rejections/search", { month, customer_id: params.customer_id || undefined, error_code: params.error_code || undefined, q: params.q, page: String(page), size: String(size) });
  const source = new URLSearchParams(Object.entries(params).filter((entry): entry is [string, string] => typeof entry[1] === "string"));
  function pageHref(nextPage: number) { const next = new URLSearchParams(source); next.set("page", String(nextPage)); return `/event-rejections?${next}`; }
  const rows = result.ok ? result.data.rejections.map(rejection => ({ ...rejection, error_label: ERRORS[rejection.error_code] ?? rejection.error_code, batch_row: rejection.batch_index === null ? null : rejection.batch_index + 1 })) : [];
  return <>
    <WorkspaceHeader title="수집 거절" description="고객 연결이나 수집 조건 때문에 저장하지 못한 요청을 확인하세요. 원본 이벤트로 저장한 뒤의 미터와 단가 문제는 이벤트 매핑 진단에서 확인합니다." />
    <section className="workspace-panel">
      <form method="get" className="workspace-actions">
        <Suspense><EnvironmentInput /></Suspense>
        <label className="workspace-field"><span>거절 기록 월</span><input type="month" name="month" defaultValue={month} required /></label>
        <label className="workspace-field"><span>거절 사유</span><select name="error_code" defaultValue={params.error_code ?? ""}><option value="">전체</option>{Object.entries(ERRORS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        <label className="workspace-field"><span>요청의 고객 ID (선택)</span><input name="customer_id" defaultValue={params.customer_id ?? ""} maxLength={36} placeholder="존재하지 않는 고객 ID도 조회할 수 있습니다" /></label>
        <label className="workspace-field"><span>전송 ID와 유형 검색</span><input name="q" defaultValue={params.q ?? ""} maxLength={255} /></label>
        <label className="workspace-field"><span>표시 건수</span><select name="size" defaultValue={size}>{[20, 50, 100].map(value => <option key={value} value={value}>{value}개</option>)}</select></label>
        <button className="month-apply" type="submit">조건 적용</button>
      </form>
      {result.ok ? <>
        <p role="status">선택한 조건의 수집 거절 {result.data.total.toLocaleString("ko-KR")}건입니다.</p>
        {result.data.recording_started_at && <p className="muted">최초 거절 기록은 {formatKstDateTime(result.data.recording_started_at)} KST입니다. 기록 기능 도입 전의 거절은 포함하지 않습니다.</p>}
        <WorkspaceTable serverPaged rows={rows} columns={[{ key: "transaction_id", label: "전송 ID" }, { key: "error_label", label: "거절 사유" }, { key: "error_fields", label: "확인할 항목" }, { key: "customer_id", label: "요청 고객 ID" }, { key: "type", label: "이벤트 유형" }, { key: "batch_row", label: "배치 행", isNumber: true }, { key: "occurred_at", label: "이벤트 발생 시각", isDate: true }, { key: "recorded_at", label: "거절 기록 시각", isDate: true }, { key: "request_id", label: "감사 요청 ID" }]} empty="선택한 조건의 수집 거절 기록이 없습니다." />
        <nav className="workspace-pagination" aria-label="수집 거절 페이지">{page > 0 && <Link href={pageHref(page - 1)}>이전</Link>}<span>{page + 1}페이지</span>{(page + 1) * size < result.data.total && <Link href={pageHref(page + 1)}>다음</Link>}</nav>
      </> : <WorkspaceError message={result.error.message} />}
      <p className="field-hint">이벤트 원문과 속성은 보관하지 않습니다. 조회 월은 이벤트 발생 월이 아닌 거절 기록 월입니다. JSON 파싱과 기본 요청 검증 단계의 오류는 요청 감사에서 확인하세요.</p>
    </section>
  </>;
}
