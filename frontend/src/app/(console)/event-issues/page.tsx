import { Suspense } from "react";
import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import { WorkspaceHeader } from "@/components/workspace/WorkspaceHeader";
import { WorkspaceError, WorkspaceTable } from "@/components/workspace/WorkspaceView";
import { EnvironmentInput } from "@/components/workspace/EnvironmentSwitch";
import { workspaceFetch } from "@/lib/api/workspace";
import { readMonth } from "@/lib/month";
import { workspaceHref } from "@/lib/workspace-href";
import { formatKstDateTime } from "@/lib/format";
import { CustomerFilterField } from "@/components/screen/CustomerFilterField";
import { customerOptions } from "@/lib/api/search";
import { readListQuery } from "@/lib/list-query";
import { CAPABILITIES } from "@/lib/api/capabilities";

type IssueReason = "unknown_event_type" | "invalid_target_property" | "unpriced_usage";
const REASONS: Record<IssueReason, string> = { unknown_event_type: "연결할 미터 없음", invalid_target_property: "집계할 수량 확인 필요", unpriced_usage: "적용 단가 없음" };
type Issue = { transaction_id: string; customer_id: string; customer_name: string; type: string; occurred_at: string; received_at: string; reason: IssueReason; billable_metric_code: string | null; target_property: string | null; action_hint: string };
type IssueResult = { month: string; cutoff_at: string; page: number; size: number; total: number; issues: Issue[]; summary: Record<IssueReason, number>; warnings: { scope: string; future_event_count: number; negative_event_metric_count: number } };

export default async function EventIssuesPage({ searchParams }: { searchParams: Promise<Record<string, string | undefined>> }) {
  const params = await searchParams;
  const month = readMonth(params.month);
  const page = /^\d+$/.test(params.page ?? "") ? Math.min(100_000, Number(params.page)) : 0;
  const size = [20, 50, 100].includes(Number(params.size)) ? Number(params.size) : 20;
  const reason = params.reason && Object.hasOwn(REASONS, params.reason) ? params.reason : undefined;
  const [result, customers] = await Promise.all([
    workspaceFetch<IssueResult>("/v1/event-issues/search", { month, customer_id: params.customer_id, reason, q: params.q, page: String(page), size: String(size) }),
    customerOptions(params.customer_id),
  ]);
  const source = new URLSearchParams(Object.entries(params).filter((entry): entry is [string, string] => typeof entry[1] === "string"));
  function pageHref(nextPage: number) { const next = new URLSearchParams(source); next.set("page", String(nextPage)); return `/event-issues?${next}`; }
  const rows = result.ok ? result.data.issues.map((issue, index) => ({ ...issue, id: `${issue.transaction_id}-${issue.reason}-${issue.billable_metric_code ?? ""}-${index}`, reason_label: REASONS[issue.reason] })) : [];
  return <>
    <WorkspaceHeader title="이벤트 매핑 진단" description="저장된 이벤트에서 미터, 집계 수량과 단가 연결을 확인하세요. 한 이벤트에 여러 미터의 문제가 있으면 각각 표시합니다." />
    <section className="workspace-panel">
      <form method="get" className="workspace-actions">
        <Suspense><EnvironmentInput /></Suspense>
        <label className="workspace-field"><span>대상 월</span><input type="month" name="month" defaultValue={month} required /></label>
        <label className="workspace-field"><span>문제 종류</span><select name="reason" defaultValue={reason ?? ""}><option value="">전체</option>{Object.entries(REASONS).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></label>
        <CustomerFilterField key={params.customer_id ?? "all"} query={readListQuery(params, CAPABILITIES.listEvents, { hasMonth: true })} customers={customers.ok ? customers.data.map(customer => ({ value: customer.value, label: customer.label + (customer.archived ? " (보관됨)" : "") })) : []} />
        <label className="workspace-field"><span>전송 ID, 고객과 유형 검색</span><input name="q" defaultValue={params.q ?? ""} maxLength={255} /></label>
        <label className="workspace-field"><span>표시 건수</span><select name="size" defaultValue={size}>{[20, 50, 100].map(value => <option value={value} key={value}>{value}개</option>)}</select></label>
        <button className="month-apply" type="submit">진단 조회</button>
      </form>
      {result.ok ? <>
        <p role="status">선택 조건 전체 문제 {result.data.total.toLocaleString("ko-KR")}건. 미터 없음 {result.data.summary.unknown_event_type}건, 수량 확인 {result.data.summary.invalid_target_property}건, 단가 없음 {result.data.summary.unpriced_usage}건.</p>
        <p className="muted">{formatKstDateTime(result.data.cutoff_at)} KST 기준입니다. 해당 월 전체의 미래 이벤트 {result.data.warnings.future_event_count}건과 음수 이벤트-미터 조합 {result.data.warnings.negative_event_metric_count}건은 위 문제 건수와 별도로 확인합니다.</p>
        <WorkspaceTable serverPaged rows={rows} columns={[
          { key: "transaction_id", label: "전송 ID", hrefPrefix: `/events?month=${month}${params.customer_id ? `&customer_id=${encodeURIComponent(params.customer_id)}` : ""}&transaction_id=`, hrefKey: "transaction_id" }, { key: "reason_label", label: "문제" }, { key: "action_hint", label: "확인할 내용" }, { key: "customer_name", label: "고객" }, { key: "type", label: "이벤트 유형" }, { key: "billable_metric_code", label: "미터" }, { key: "target_property", label: "수량 속성" }, { key: "occurred_at", label: "발생 시각", isDate: true },
        ]} empty="선택한 조건에서 미터, 수량 또는 단가 연결 문제가 발견되지 않았습니다." />
        <nav className="workspace-pagination" aria-label="진단 결과 페이지">{page > 0 && <Link href={pageHref(page - 1)}>이전</Link>}<span>{page + 1}페이지</span>{(page + 1) * size < result.data.total && <Link href={pageHref(page + 1)}>다음</Link>}</nav>
      </> : <WorkspaceError message={result.error.message} />}
      <p className="field-hint">수집 단계에서 거절된 요청은 이벤트 원본으로 저장되지 않아 이 목록에 포함되지 않습니다. 수집 응답과 <Link href={workspaceHref("/audit", source)}>감사 기록</Link>에서 요청 결과를 확인하세요. 정의나 단가를 수정해도 이미 확정한 청구서는 자동 변경되지 않습니다.</p>
    </section>
  </>;
}
