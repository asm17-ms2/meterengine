import { Suspense } from "react";
import { Button } from "antd";
import { PageLinks } from "@/components/screen/ListPagination";
import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import { WorkspaceHeader } from "@/components/workspace/WorkspaceHeader";
import { WorkspaceError, WorkspaceTable } from "@/components/workspace/WorkspaceView";
import { EnvironmentInput } from "@/components/workspace/EnvironmentSwitch";
import { AGGREGATE_REQUEST } from "@/lib/api/client";
import { workspaceFetch } from "@/lib/api/workspace";
import { readMonth } from "@/lib/month";
import { searchKey } from "@/lib/search-key";
import { AggregateLoading } from "@/components/screen/AggregateLoading";
import { workspaceHref } from "@/lib/workspace-href";
import { formatKstShortStamp } from "@/lib/format";
import { CustomerFilterField } from "@/components/screen/CustomerFilterField";
import { MonthField } from "@/components/screen/MonthField";
import { customerOptions } from "@/lib/api/search";
import { readListQuery } from "@/lib/list-query";
import { CAPABILITIES } from "@/lib/api/capabilities";
import { LinkButton } from "@/components/screen/LinkButton";
import type { Metadata } from "next";

export const metadata: Metadata = { title: "과금에서 빠진 이벤트" };

type IssueReason = "unknown_event_type" | "invalid_target_property" | "unpriced_usage";
const REASONS: Record<IssueReason, string> = { unknown_event_type: "연결할 미터 없음", invalid_target_property: "수량을 읽을 수 없음", unpriced_usage: "적용 단가 없음" };
const REASON_MEANINGS: Record<IssueReason, string> = {
  unknown_event_type: "이 이벤트 유형을 세는 미터가 없어요. 미터를 만들면 과금에 들어가요.",
  invalid_target_property: "미터가 읽는 속성이 없거나 형식이 달라요. 보내는 값을 확인하세요.",
  unpriced_usage: "수량은 셌지만 단가가 없어 청구액에 들어가지 않아요. 단가를 등록하세요.",
};
const COUNT = new Intl.NumberFormat("ko-KR");
type Issue = { transaction_id: string; customer_id: string; customer_name: string; type: string; occurred_at: string; received_at: string; reason: IssueReason; billable_metric_code: string | null; target_property: string | null };
type IssueResult = { month: string; cutoff_at: string; page: number; size: number; total: number; issues: Issue[]; summary: Record<IssueReason, number>; warnings: { scope: string; future_event_count: number; negative_event_metric_count: number } };

export default async function EventIssuesPage({ searchParams }: { searchParams: Promise<Record<string, string | undefined>> }) {
  const params = await searchParams;
  const month = readMonth(params.month);
  const size = [20, 50, 100].includes(Number(params.size)) ? Number(params.size) : 20;
  const reason = params.reason && Object.hasOwn(REASONS, params.reason) ? params.reason : undefined;
  const customers = await customerOptions(params.customer_id);
  const source = new URLSearchParams(Object.entries(params).filter((entry): entry is [string, string] => typeof entry[1] === "string"));
  return <>
    <WorkspaceHeader title="과금에서 빠진 이벤트" description="받았지만 청구액에 들어가지 않은 이벤트예요. 문제 종류마다 고칠 곳을 확인하세요." />
    <section className="workspace-panel">
      <form method="get" className="workspace-actions query-row">
        <Suspense><EnvironmentInput /></Suspense>
        <MonthField key={month} month={month} label="조회 월" />
        <label className="workspace-field"><span>문제 종류</span><select key={reason ?? ""} name="reason" defaultValue={reason ?? ""}><option value="">전체</option>{Object.entries(REASONS).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></label>
        <CustomerFilterField key={params.customer_id ?? "all"} query={readListQuery(params, CAPABILITIES.listEvents, { hasMonth: true })} customers={customers.ok ? customers.data.map(customer => ({ value: customer.value, label: customer.label + (customer.archived ? " (보관됨)" : "") })) : []} />
        <label className="workspace-field"><span>전송 ID, 고객, 유형으로 찾기</span><input name="q" defaultValue={params.q ?? ""} maxLength={255} /></label>
        <label className="workspace-field"><span>표시 건수</span><select name="size" defaultValue={size}>{[20, 50, 100].map(value => <option value={value} key={value}>{value}건</option>)}</select></label>
        <Button type="primary" htmlType="submit">조건 적용</Button>
      </form>
      <Suspense key={searchKey(params)} fallback={<AggregateLoading title="과금에서 빠진 이벤트를 찾는 중이에요" />}><EventIssues params={params} month={month} reason={reason} size={size} source={source} /></Suspense>
      <p>받을 때 거절된 이벤트는 이 목록에 없어요. <Link href={workspaceHref("/event-rejections", source)}>수집 거절</Link>에서 확인하세요. 미터나 단가를 고쳐도 이미 발행한 청구서는 바뀌지 않아요.</p>
    </section>
  </>;
}

async function EventIssues({ params, month, reason, size, source }: { params: Record<string, string | undefined>; month: string; reason?: string; size: number; source: URLSearchParams }) {
  const page = /^\d+$/.test(params.page ?? "") ? Math.min(100_000, Number(params.page)) : 0;
  const result = await workspaceFetch<IssueResult>("/v1/event-issues/search", { month, customer_id: params.customer_id, reason, q: params.q, page: String(page), size: String(size) }, AGGREGATE_REQUEST);
  if (!result.ok) return <WorkspaceError message={result.error.message} />;
  const { summary, warnings } = result.data;
  const rows = result.data.issues.map((issue, index) => ({ ...issue, id: `${issue.transaction_id}-${issue.reason}-${issue.billable_metric_code ?? ""}-${index}`, reason_label: REASONS[issue.reason] }));
  const scopeQuery = `month=${month}${params.customer_id ? `&customer_id=${encodeURIComponent(params.customer_id)}` : ""}`;
  const pricingHref = `/pricing?${scopeQuery}`;
  const reasonActions: Record<IssueReason, { label: string; href: string }> = { unknown_event_type: { label: "미터 만들기", href: "/billable-metrics" }, invalid_target_property: { label: "해당 이벤트 보기", href: `/event-issues?${scopeQuery}&reason=invalid_target_property` }, unpriced_usage: { label: "단가 등록하기", href: pricingHref } };
  return <>
    <p role="status">선택한 조건의 문제 {COUNT.format(result.data.total)}건</p>
    <ul className="attention-grid">
      {(Object.keys(REASONS) as IssueReason[]).map(issueReason => {
        const action = reasonActions[issueReason];
        return <li key={issueReason} className="attention-card"><strong>{REASONS[issueReason]} {COUNT.format(summary[issueReason])}건</strong><p>{REASON_MEANINGS[issueReason]}</p>{summary[issueReason] > 0 && <div className="workspace-actions"><LinkButton href={action.href}>{action.label}</LinkButton></div>}</li>;
      })}
      {warnings.future_event_count > 0 && <li className="attention-card"><strong>발생 시각이 미래인 이벤트 {COUNT.format(warnings.future_event_count)}건</strong><p>그 시각이 되면 집계에 들어가요. 보낸 시각이 맞는지 확인하세요.</p><div className="workspace-actions"><LinkButton href={`/events?month=${month}`}>시각 확인</LinkButton></div></li>}
      {warnings.negative_event_metric_count > 0 && <li className="attention-card"><strong>수량이 음수인 이벤트 {COUNT.format(warnings.negative_event_metric_count)}건</strong><p>집계값이 음수가 되면 청구에서 빠져요.</p><div className="workspace-actions"><LinkButton href={`/billing?month=${month}`}>청구 검토에서 확인</LinkButton></div></li>}
    </ul>
    <p>{formatKstShortStamp(new Date(result.data.cutoff_at))} 기준이에요.</p>
    <WorkspaceTable serverPaged rows={rows} columns={[
      { key: "transaction_id", label: "전송 ID", hrefPrefix: `/events?month=${month}${params.customer_id ? `&customer_id=${encodeURIComponent(params.customer_id)}` : ""}&transaction_id=`, hrefKey: "transaction_id" }, { key: "reason_label", label: "문제" }, { key: "customer_name", label: "고객" }, { key: "type", label: "이벤트 유형" }, { key: "billable_metric_code", label: "미터" }, { key: "target_property", label: "수량 속성" }, { key: "occurred_at", label: "발생 시각", isDate: true },
    ]} empty="이 조건에서는 미터, 수량, 단가 연결 문제가 없어요." />
    <PageLinks label="진단 결과 페이지" pathname="/event-issues" params={Object.fromEntries(source)} page={page} size={size} total={result.data.total} />
  </>;
}
