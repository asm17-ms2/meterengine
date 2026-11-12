import { Suspense } from "react";
import { Button } from "antd";
import { PageLinks } from "@/components/screen/ListPagination";
import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import { WorkspaceHeader } from "@/components/workspace/WorkspaceHeader";
import { WorkspaceError, WorkspaceTable } from "@/components/workspace/WorkspaceView";
import { EnvironmentInput } from "@/components/workspace/EnvironmentSwitch";
import { workspaceFetch } from "@/lib/api/workspace";
import { readMonth } from "@/lib/month";
import { workspaceHref } from "@/lib/workspace-href";
import { formatKstDateTime } from "@/lib/format";
import { CustomerFilterField } from "@/components/screen/CustomerFilterField";
import { MonthField } from "@/components/screen/MonthField";
import { customerOptions } from "@/lib/api/search";
import { readListQuery } from "@/lib/list-query";
import { CAPABILITIES } from "@/lib/api/capabilities";
import { reasonHints } from "@/lib/event-issue-hints";
import type { Metadata } from "next";

export const metadata: Metadata = { title: "이벤트 매핑 진단" };

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
  const hints: Partial<Record<IssueReason, string>> = Object.fromEntries(result.ok ? reasonHints(result.data.issues).map(({ reason: hintReason, hint }) => [hintReason, hint]) : []);
  const rows = result.ok ? result.data.issues.map((issue, index) => ({ ...issue, id: `${issue.transaction_id}-${issue.reason}-${issue.billable_metric_code ?? ""}-${index}`, reason_label: REASONS[issue.reason] })) : [];
  return <>
    <WorkspaceHeader title="이벤트 매핑 진단" description="받은 이벤트 가운데 미터, 집계할 수량, 단가가 연결되지 않은 것을 확인하세요. 한 이벤트에 여러 미터의 문제가 있으면 미터마다 한 줄씩 보여요." />
    <section className="workspace-panel">
      <form method="get" className="workspace-actions query-row">
        <Suspense><EnvironmentInput /></Suspense>
        <MonthField key={month} month={month} label="조회 월" />
        <label className="workspace-field"><span>문제 종류</span><select name="reason" defaultValue={reason ?? ""}><option value="">전체</option>{Object.entries(REASONS).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></label>
        <CustomerFilterField key={params.customer_id ?? "all"} query={readListQuery(params, CAPABILITIES.listEvents, { hasMonth: true })} customers={customers.ok ? customers.data.map(customer => ({ value: customer.value, label: customer.label + (customer.archived ? " (보관됨)" : "") })) : []} />
        <label className="workspace-field"><span>전송 ID, 고객과 유형 검색</span><input name="q" defaultValue={params.q ?? ""} maxLength={255} /></label>
        <label className="workspace-field"><span>표시 건수</span><select name="size" defaultValue={size}>{[20, 50, 100].map(value => <option value={value} key={value}>{value}건</option>)}</select></label>
        <Button type="primary" htmlType="submit">조건 적용</Button>
      </form>
      {result.ok ? <>
        <p role="status">선택한 조건의 문제 {result.data.total.toLocaleString("ko-KR")}건</p>
        <ul className="setup-list">
          <li><div><strong>{REASONS.unknown_event_type} {result.data.summary.unknown_event_type}건</strong>{hints.unknown_event_type && <p>{hints.unknown_event_type}</p>}</div>{result.data.summary.unknown_event_type > 0 && <Link href="/billable-metrics">미터 등록하기</Link>}</li>
          <li><div><strong>{REASONS.invalid_target_property} {result.data.summary.invalid_target_property}건</strong>{hints.invalid_target_property && <p>{hints.invalid_target_property}</p>}</div></li>
          <li><div><strong>{REASONS.unpriced_usage} {result.data.summary.unpriced_usage}건</strong>{hints.unpriced_usage && <p>{hints.unpriced_usage}</p>}</div>{result.data.summary.unpriced_usage > 0 && <Link href={`/pricing?month=${month}${params.customer_id ? `&customer_id=${encodeURIComponent(params.customer_id)}` : ""}`}>단가 등록하기</Link>}</li>
          <li><div><strong>이 달 전체에서 발생 시각이 미래인 이벤트 {result.data.warnings.future_event_count}건</strong></div>{result.data.warnings.future_event_count > 0 && <Link href={`/events?month=${month}`}>시각 확인</Link>}</li>
          <li><div><strong>음수 이벤트-미터 조합 {result.data.warnings.negative_event_metric_count}건</strong></div>{result.data.warnings.negative_event_metric_count > 0 && <Link href={`/billing?month=${month}`}>청구 검토에서 확인</Link>}</li>
        </ul>
        <p className="muted">{formatKstDateTime(result.data.cutoff_at)} KST 기준이에요. 발생 시각이 미래인 이벤트와 음수 이벤트-미터 조합은 문제 건수에 넣지 않고 따로 셌어요.</p>
        <WorkspaceTable serverPaged rows={rows} columns={[
          { key: "transaction_id", label: "전송 ID", hrefPrefix: `/events?month=${month}${params.customer_id ? `&customer_id=${encodeURIComponent(params.customer_id)}` : ""}&transaction_id=`, hrefKey: "transaction_id" }, { key: "reason_label", label: "문제" }, { key: "customer_name", label: "고객" }, { key: "type", label: "이벤트 유형" }, { key: "billable_metric_code", label: "미터" }, { key: "target_property", label: "수량 속성" }, { key: "occurred_at", label: "발생 시각", isDate: true },
        ]} empty="이 조건에서는 미터, 수량, 단가 연결 문제가 없어요." />
        <PageLinks label="진단 결과 페이지" pathname="/event-issues" params={Object.fromEntries(source)} page={page} size={size} total={result.data.total} />
      </> : <WorkspaceError message={result.error.message} />}
      <p className="field-hint">받을 때 거절된 이벤트는 저장하지 않아서 이 목록에 없어요. 수집 API 응답이나 <Link href={workspaceHref("/event-rejections", source)}>수집 거절</Link>, <Link href={workspaceHref("/audit", source)}>감사 기록</Link>에서 확인하세요. 미터나 단가를 고쳐도 이미 확정한 인보이스는 바뀌지 않아요.</p>
    </section>
  </>;
}
