"use client";

import { useWorkspaceSearch } from "@/components/workspace/use-workspace-search";

import { ResourceSearchControls, resourceSearchFormId } from "@/components/screen/ResourceSearchControls";
import { ListPagination } from "@/components/screen/ListPagination";
import { CsvDownload } from "@/components/screen/CsvDownload";
import { CAPABILITIES } from "@/lib/api/capabilities";
import type { ListQuery } from "@/lib/list-query";

import { useMutationScope } from "@/components/auth/use-mutation-scope";
import { useTenantScope } from "@/components/auth/TenantAccess";

import { Alert, App, Button, Empty, Modal, Table, Tag } from "antd";
import { ScopedLink as Link } from "@/components/workspace/ScopedLink";

import { startTransition, useActionState, useEffect, useId, useRef, useState } from "react";
import { workspaceAction } from "@/app/(console)/workspace-actions";
import type { ArchiveStatus } from "@/lib/list-query";
import { ActionPanel } from "@/components/workspace/ActionPanel";
import type { BillableMetricResponse } from "@/lib/api/billable-metrics";
import { metricCalculationFormula } from "@/lib/billable-metric-calculation";
import type { CustomerResponse } from "@/lib/api/customers";
import type { BillableMetricPricePriceRate, ListBillableMetricPricesResponse, PricePlanResponse, PriceRateResponse } from "@/lib/api/pricing";
import styles from "./pricing.module.css";

const MODEL_LABELS = { per_unit: "단일 단가", graduated: "누진 구간", volume: "총량 구간" };
const MONEY = new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 8 });

export type PricingCustomer = Pick<CustomerResponse, "id" | "name" | "archived_at"> & { is_archived?: boolean };

export type PricingMetric = Pick<BillableMetricResponse, "code" | "name" | "dimension_properties" | "archived_at"> & Partial<BillableMetricResponse> & { rate_count?: number };

type Props = { query: ListQuery; total: number; summary: { metric_count: number; no_rate_count: number }; planHistoryStatus: ArchiveStatus; rateHistoryStatus: ArchiveStatus; metricStatus: ArchiveStatus; month: string; currentMonth: string; customerId: string; customers: PricingCustomer[]; metrics: PricingMetric[]; commonPrices: ListBillableMetricPricesResponse["billable_metric_prices"]; scopedPrices: ListBillableMetricPricesResponse["billable_metric_prices"]; histories: Record<string, PriceRateResponse[]>; commonPlans: PricePlanResponse[]; scopedPlans: PricePlanResponse[] };

function activePlan(plans: PricePlanResponse[], month: string) { return plans.find(plan => plan.effective_from <= month && (!plan.effective_until || plan.effective_until > month) && (!plan.archived_from || plan.archived_from > month)); }
function dimensionKey(rate: Pick<PriceRateResponse, "dimension_values">) { return JSON.stringify(Object.entries(rate.dimension_values).sort(([left], [right]) => left.localeCompare(right))); }
function dimensions(rate: Pick<PriceRateResponse, "dimension_values">) { return Object.entries(rate.dimension_values).map(([key, value]) => `${key}: ${String(value)}`).join(", ") || "모든 사용량"; }
function dateRange(rate: { effective_from: string; effective_until: string | null }) { return `${rate.effective_from}부터${rate.effective_until ? ` ${rate.effective_until} 전까지` : " 계속"}`; }

export function PricingWorkspace(props: Props) {
  const canManage = useTenantScope("admin");
  const search = useWorkspaceSearch();
  const matchesStatus = (entry: { archived_at?: string | null }, status: ArchiveStatus) => status === "all" || (status === "archived" ? Boolean(entry.archived_at) : !entry.archived_at);
  const visibleMetrics = props.metrics.filter(metric => matchesStatus(metric, props.metricStatus));
  const selectedCode = visibleMetrics.some(metric => metric.code === search.get("metric")) ? search.get("metric")! : visibleMetrics[0]?.code ?? "";
  const historyPlans = props.scopedPlans.filter(plan => matchesStatus(plan, props.planHistoryStatus));
  const canRestorePlan = (plan: PricePlanResponse) => Boolean(plan.archived_from && plan.archived_from > props.currentMonth && !props.scopedPlans.some(other => !other.archived_at && other.id !== plan.id && other.effective_from === plan.effective_from && other.customer_id === plan.customer_id));
  const canRestoreRate = (rate: PriceRateResponse) => Boolean(rate.archived_from && rate.archived_from > props.currentMonth && !(props.histories[selectedCode] ?? []).some(other => !other.archived_at && other.id !== rate.id && other.effective_from === rate.effective_from && other.customer_id === rate.customer_id && dimensionKey(other) === dimensionKey(rate)));
  const selectedMetric = props.metrics.find(metric => metric.code === selectedCode);
  const isCustomerArchived = props.customers.some(customer => customer.id === props.customerId && Boolean(customer.archived_at || customer.is_archived));
  const customerName = props.customers.find(customer => customer.id === props.customerId)?.name ?? "모든 고객 공통";
  const common = props.commonPrices.find(metric => metric.billable_metric_code === selectedCode)?.price_rates ?? [];
  const scoped = props.scopedPrices.find(metric => metric.billable_metric_code === selectedCode)?.price_rates ?? [];
  const resolved = new Map(common.map(rate => [dimensionKey(rate), { ...rate, isInherited: Boolean(props.customerId) }]));
  for (const rate of scoped) resolved.set(dimensionKey(rate), { ...rate, isInherited: false });
  const effectiveRates = [...resolved.values()];
  const scopedPlan = activePlan(props.scopedPlans, props.month);
  const plan = scopedPlan ?? activePlan(props.commonPlans, props.month);
  const isInheritedPlan = Boolean(props.customerId && !scopedPlan && plan);
  return <>
    <ResourceSearchControls query={props.query} capabilities={CAPABILITIES.listBillableMetricPrices} resource="billable-metric-prices" month={{ value: props.month }} customers={props.customers.map(customer => ({ value: customer.id, label: customer.name + (customer.archived_at || customer.is_archived ? " (보관됨)" : "") }))} />
    <input type="hidden" form={resourceSearchFormId("billable-metric-prices")} name="metric" value={props.query.preservedParameters?.metric ?? selectedCode} />
    <p className="field-hint">고객별 단가가 있으면 공통 단가보다 먼저 적용합니다. 미터를 선택하면 작성한 조회 조건도 함께 적용합니다.</p>
    <CsvDownload resource="billable-metric-prices" />
    <p role="status">조건 전체 미터 {props.summary.metric_count}개, 단가가 없는 미터 {props.summary.no_rate_count}개</p>
    <div className={styles.equation} aria-label="청구 금액 계산 순서"><span>사용량과 단가로 계산한 사용료</span><b>또는</b><span>최소 사용료 중 큰 금액</span><b>+</b><span>기본료</span><b>=</b><strong>세금 별도 청구 예정액</strong></div>
    <div className={styles.layout}>
      <section className={`workspace-panel ${styles.meterRail}`} aria-label="과금 미터 선택"><h2>무엇을 과금하나요</h2>{visibleMetrics.length ? visibleMetrics.map(metric => <button key={metric.code} type="submit" form={resourceSearchFormId("billable-metric-prices")} name="metric" value={metric.code} aria-pressed={metric.code === selectedCode}><strong>{metric.name}{metric.archived_at ? " (보관됨)" : ""}</strong><code>{metric.code}</code><small style={{ overflowWrap: "anywhere" }}>{metric.aggregation ? metricCalculationFormula({ ...metric, aggregation: metric.aggregation, target_property: metric.target_property ?? null }) : `등록 단가 ${metric.rate_count ?? 0}개`}</small></button>) : <Empty description={props.metricStatus === "archived" ? "보관된 미터가 없습니다. 전체에서 다른 미터를 확인하세요." : props.metricStatus === "all" ? "단가를 적용할 미터가 없습니다" : "사용 중인 미터가 없습니다. 보관됨 또는 전체에서 이전 미터를 확인하세요."}><Link href={props.metrics.length ? "/billable-metrics?status=all" : "/billable-metrics"}>{props.metrics.length ? "미터 관리에서 전체 보기" : "첫 과금 미터 만들기"}</Link></Empty>}<ListPagination query={props.query} capabilities={CAPABILITIES.listBillableMetricPrices} total={props.total} /></section>
      <section className="workspace-panel" aria-labelledby="current-prices-title"><div className={styles.sectionHeading}><div><h2 id="current-prices-title">{selectedMetric?.name ?? "사용량 단가"}</h2><p>{props.month} / {customerName}</p></div>{canManage && selectedMetric && !selectedMetric.archived_at && !isCustomerArchived && <PriceRateEditor key={`${props.query.organizationId}-${props.query.environment}-${selectedMetric.code}-${props.customerId}`} metric={selectedMetric} month={props.month} currentMonth={props.currentMonth} customerId={props.customerId} customerName={customerName} />}</div>
        {effectiveRates.length ? effectiveRates.map(rate => <div className={styles.rate} key={rate.id}><div className={styles.sectionHeading}><strong>{dimensions(rate)}</strong><Tag color={rate.isInherited ? "default" : "purple"}>{rate.isInherited ? "공통 단가 적용" : MODEL_LABELS[rate.model]}</Tag></div>{rate.archived_at && <p className="field-hint">보관 상태, {rate.archived_from ?? "다음 달"}부터 적용 중단</p>}<RateDetails rate={rate} /><small className="muted">{dateRange(rate)}</small></div>) : <Empty description={selectedMetric?.archived_at || isCustomerArchived ? "선택한 월에 적용된 단가가 없습니다. 이전 적용 월과 변경 이력을 확인하세요." : selectedMetric ? "이 월에 적용되는 단가가 없습니다. 단가를 등록하면 청구 예정액에 사용료가 반영됩니다." : "관리 상태를 바꿔 미터를 선택하세요."} />}
        <p className={styles.note}>조합이 일치하는 단가만 적용합니다. 미터에서 정한 단위와 반올림을 적용한 각 조합의 수량에 단가를 곱합니다. 각 라인에서 10원 미만을 버리고 합산합니다.</p>
      </section>
    </div>
    <section className="workspace-panel"><div className={styles.sectionHeading}><div><h2>기본료와 최소 사용료</h2><p>수량이 0보다 큰 사용량이 있는 고객에게만 적용합니다.</p></div>{isCustomerArchived ? <p className="field-hint">보관된 고객은 복원한 뒤 새 요금제를 등록할 수 있습니다.</p> : <ActionPanel title="요금제 버전 등록" operation="createPricePlan" description={`${customerName}에 적용할 월과 요금을 입력하세요. 최소 사용료를 먼저 채운 뒤 기본료를 더합니다.`} fields={[
      {name:"customer_id",type:"hidden",label:"고객",value:props.customerId},
      {name:"effective_from",type:"month",label:"적용 시작 월",value:props.month < props.currentMonth ? props.currentMonth : props.month},
      {name:"effective_until",type:"month",label:"적용 종료 월",isOptional:true,hint:"이 월부터 적용되지 않습니다. 비워 두면 계속 적용합니다."},
      {name:"base_fee",type:"number",label:"월 기본료 (원)",value:"0"},
      {name:"minimum_amount",type:"number",label:"월 최소 사용료 (원)",value:"0",hint:"사용료가 이 금액보다 작으면 차액을 더합니다. 기본료는 별도입니다."},
    ]} />}</div>
      {plan ? <><div className={styles.planAmounts}><div><span>기본료</span><strong>{MONEY.format(plan.base_fee)}원</strong></div><div><span>최소 사용료</span><strong>{MONEY.format(plan.minimum_amount)}원</strong></div><div><span>적용 기간</span><strong>{dateRange(plan)}</strong>{isInheritedPlan && <Tag>공통 요금제 적용</Tag>}{plan.archived_at && <span className="field-hint">보관, {plan.archived_from ?? "다음 달"}부터 중단</span>}</div></div></> : <p className="muted">이 월에는 기본료와 최소 사용료가 없습니다. 등록한 사용량 단가로만 계산합니다.</p>}
      <p className="field-hint">이력의 관리 상태 필터는 위 가격에 영향을 주지 않습니다. 위 가격은 선택한 월의 실제 적용 기준입니다.</p><PricingHistoryFilter parameterName="plan_status" value={props.planHistoryStatus} label="요금제 관리 상태" /><details className={styles.history} open><summary>요금제 변경 이력 ({historyPlans.length})</summary><Table<PricePlanResponse> size="small" rowKey="id" scroll={{x:650}} pagination={false} dataSource={historyPlans} locale={{ emptyText: props.planHistoryStatus === "archived" ? "보관된 요금제 이력이 없습니다" : props.planHistoryStatus === "all" ? "등록한 요금제 이력이 없습니다" : "사용 중인 요금제 이력이 없습니다. 보관됨 또는 전체에서 이전 요금제를 확인하세요." }} columns={[
        {title:"적용 기간",key:"period",render:(_,row)=>dateRange(row)},
        {title:"기본료",dataIndex:"base_fee",render:(value:number)=>`${MONEY.format(value)}원`},
        {title:"최소 사용료",dataIndex:"minimum_amount",render:(value:number)=>`${MONEY.format(value)}원`},
        {title:"관리 상태",key:"state",render:(_,row)=><span>{row.archived_at ? `보관됨, ${row.archived_from ?? "다음 달"}부터 중단` : "사용 중"}</span>},
        ...(canManage ? [{title:"작업",key:"actions",render:(_: unknown, row: PricePlanResponse)=>row.archived_at && !canRestorePlan(row) ? <span className="field-hint">적용 중단이 시작됐거나 대체 버전이 있습니다. 새 버전을 등록하세요.</span> : <ActionPanel title={row.archived_at ? "요금제 버전 복원" : "요금제 버전 보관"} operation={row.archived_at ? "restorePricePlan" : "deletePricePlan"} description={row.archived_at ? "이 요금제 버전을 다시 사용합니다. 적용 기간과 다른 버전의 중복 여부를 확인하세요." : "다음 달부터 이 요금제의 적용을 중단합니다. 현재 월과 과거 청구, 확정 인보이스는 보존되며 보관 목록에서 복원할 수 있습니다."} isDanger={!row.archived_at} fields={[{name:"id",type:"hidden",label:"요금제",value:row.id}]} />}] : []),
      ]} /></details>
    </section>
    {selectedMetric && <section className="workspace-panel"><h2>{selectedMetric.name} 단가 변경 이력</h2><p className="muted">적용 기간과 보관 상태는 별개입니다. 보관해도 현재 월과 과거 청구의 계산 근거는 유지됩니다.</p><PricingHistoryFilter parameterName="rate_status" value={props.rateHistoryStatus} label="단가 관리 상태" /><Table<PriceRateResponse> size="small" rowKey="id" scroll={{x:750}} pagination={{pageSize:8}} dataSource={(props.histories[selectedCode] ?? []).filter(rate => matchesStatus(rate, props.rateHistoryStatus))} locale={{emptyText: props.rateHistoryStatus === "archived" ? "보관된 단가 이력이 없습니다" : props.rateHistoryStatus === "all" ? "등록한 단가 이력이 없습니다" : "사용 중인 단가 이력이 없습니다. 보관됨 또는 전체에서 이전 단가를 확인하세요."}} columns={[
      {title:"조합",key:"dimensions",render:(_,rate)=>dimensions(rate)},
      {title:"적용 기간",key:"period",render:(_,rate)=>dateRange(rate)},
      {title:"계산 방식",key:"model",render:(_,rate)=><Tag>{MODEL_LABELS[rate.model]}</Tag>},
      {title:"단가",key:"price",render:(_,rate)=>rate.unit_price !== null ? `${MONEY.format(rate.unit_price)}원` : `${rate.tiers?.length ?? 0}개 구간`},
      {title:"관리 상태",key:"state",render:(_,rate)=><span>{rate.archived_at ? `보관됨, ${rate.archived_from ?? "다음 달"}부터 중단` : "사용 중"}</span>},
      ...(canManage ? [{title:"작업",key:"actions",render:(_: unknown, rate: PriceRateResponse)=>rate.archived_at && !canRestoreRate(rate) ? <span className="field-hint">적용 중단이 시작됐거나 대체 버전이 있습니다. 새 버전을 등록하세요.</span> : <ActionPanel title={rate.archived_at ? "단가 버전 복원" : "단가 버전 보관"} operation={rate.archived_at ? "restorePriceRate" : "deletePriceRate"} description={rate.archived_at ? "이 단가 버전을 다시 사용합니다. 적용 기간과 같은 조합의 다른 버전을 확인하세요." : "다음 달부터 이 단가 버전의 적용을 중단합니다. 현재 월과 과거 청구, 확정 인보이스는 보존되며 보관 목록에서 복원할 수 있습니다."} isDanger={!rate.archived_at} fields={[{name:"id",label:"단가",type:"hidden",value:rate.id},{name:"code",label:"미터",type:"hidden",value:selectedCode}]} />}] : []),
    ]} /></section>}
    <div className={styles.next}><div><strong>가격 설정이 끝났나요</strong><p>이벤트를 수집한 뒤 청구 예정액에서 실제 적용 금액과 누락된 조합을 확인하세요.</p></div><Link href={`/billing?month=${encodeURIComponent(props.month)}${props.customerId ? `&customer_id=${encodeURIComponent(props.customerId)}` : ""}`}>청구 예정액 확인</Link></div>
  </>;
}

function PricingHistoryFilter({ parameterName, value, label }: { parameterName: "plan_status" | "rate_status"; value: ArchiveStatus; label: string }) {
  const form = resourceSearchFormId("billable-metric-prices");
  return <div className="workspace-actions"><label className="workspace-field"><span>{label}</span><select key={value} form={form} name={parameterName} defaultValue={value}><option value="active">사용 중</option><option value="archived">보관됨</option><option value="all">전체</option></select></label><Button form={form} htmlType="submit">이력 조건 적용</Button></div>;
}

function RateDetails({ rate }: { rate: BillableMetricPricePriceRate }) {
  if (rate.model === "per_unit") return <p className={styles.unitPrice}><strong>{MONEY.format(rate.unit_price ?? 0)}원</strong> / 사용량 1</p>;
  let lower = 0;
  return <div className={styles.tierList}><p>{rate.model === "graduated" ? "구간마다 해당 수량에 단가를 곱해 더합니다." : "전체 수량이 속한 구간의 단가를 모든 수량에 적용합니다."}</p>{rate.tiers?.map((tier,index)=>{const start=lower;lower=tier.up_to??lower;return <div key={index}><span>{start === 0 ? "0부터" : `${MONEY.format(start)} 초과`}{tier.up_to === null ? " 계속" : ` ${MONEY.format(tier.up_to)}까지`}</span><strong>{MONEY.format(tier.unit_price)}원 / 1</strong></div>;})}</div>;
}

function PriceRateEditor({ metric, month, currentMonth, customerId, customerName }: { metric: Pick<BillableMetricResponse, "code" | "name" | "dimension_properties">; month: string; currentMonth: string; customerId: string; customerName: string }) {
  const bindScope = useMutationScope();
  const [isOpen,setIsOpen]=useState(false);
  const [model,setModel]=useState("per_unit");
  const [tiers,setTiers]=useState([{id:0,upTo:"100",unitPrice:"0"},{id:1,upTo:"",unitPrice:"0"}]);
  const [dimensionValues,setDimensionValues]=useState<Record<string,{kind:string;value:string}>>({});
  const [isDirty, setIsDirty] = useState(false);
  const { message } = App.useApp();
  const [state,action,isPending]=useActionState(async (previous: import("@/app/(console)/workspace-actions").WorkspaceActionState, data: FormData) => { const result = await workspaceAction(previous, bindScope(data)); if (result.isSuccess) { setIsDirty(false); setIsOpen(false); message.success("단가 버전을 저장했습니다."); } return result; },{});
  const fieldPrefix = useId();
  const formRef = useRef<HTMLFormElement>(null);
  const [clientErrors, setClientErrors] = useState<Record<string, string>>({});
  const [dismissedErrors, setDismissedErrors] = useState<{ state: typeof state; fields: string[] }>({ state, fields: [] });
  const errors: Record<string, string> = { ...Object.fromEntries(Object.entries(state.fieldErrors ?? {}).map(([name, value]) => [priceRateFieldName(name, tiers, model), value]).filter(([name]) => dismissedErrors.state !== state || !dismissedErrors.fields.includes(name))), ...clientErrors };
  const errorRef = useRef<HTMLDivElement>(null);
  useEffect(() => { if (state.message && !state.isSuccess) { const first = formRef.current?.querySelector<HTMLElement>('[aria-invalid="true"]'); if (first) first.focus(); else errorRef.current?.focus(); } }, [state]);
  const fieldId = (name: string) => `${fieldPrefix}-${encodeURIComponent(name)}`;
  const attributes = (name: string) => ({ id: fieldId(name), "data-error-field": name, "aria-invalid": Boolean(errors[name]), "aria-describedby": errors[name] ? `${fieldId(name)}-error` : undefined });
  const errorMessage = (name: string) => errors[name] ? <small id={`${fieldId(name)}-error`} className="field-error">{errors[name]}</small> : null;
  const fieldLabel = (name: string) => ({ effective_from: "적용 시작 월", effective_until: "적용 종료 월", unit_price: "사용량 1당 단가", dimension_values: "속성 조합", tiers: "요금 구간", model: "계산 방식" }[name] ?? (name.startsWith("dimension:") ? `${name.slice(10)} 값` : name.startsWith("tier:") ? `구간 ${tiers.findIndex(tier => tier.id === Number(name.split(":")[1])) + 1} ${name.endsWith(":upTo") ? "끝 수량" : "단가"}` : name));
  const close = () => { if (!isPending && (!isDirty || window.confirm("작성 중인 단가를 버리고 닫을까요?"))) setIsOpen(false); };
  const encodedDimensions = Object.fromEntries(metric.dimension_properties.map(key=>{const entry=dimensionValues[key] ?? {kind:"string",value:""};return [key,entry.kind==="number" ? Number(entry.value) : entry.kind==="boolean" ? entry.value==="true" : entry.value];}));
  const encodedTiers=tiers.map((tier,index)=>({up_to:index===tiers.length-1?null:tier.upTo,unit_price:tier.unitPrice}));
  const changeTier = (id: number, key: "upTo" | "unitPrice", value: string) => setTiers(current => current.map(tier => tier.id === id ? { ...tier, [key]: value } : tier));

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const nextErrors: Record<string, string> = {};
    const until = String(data.get("effective_until") ?? "");
    if (until && until <= String(data.get("effective_from") ?? "")) nextErrors.effective_until = "적용 종료 월은 시작 월보다 뒤여야 합니다.";
    if (model !== "per_unit") {
      let previous = 0;
      for (const [index, tier] of tiers.entries()) {
        const upper = Number(tier.upTo);
        if (index < tiers.length - 1) {
          if (!tier.upTo || !Number.isFinite(upper) || upper <= previous) nextErrors["tier:" + tier.id + ":upTo"] = "끝 수량은 0보다 크고 앞 구간보다 커야 합니다.";
          previous = upper;
        }
        if (!tier.unitPrice || !Number.isFinite(Number(tier.unitPrice)) || Number(tier.unitPrice) < 0) nextErrors["tier:" + tier.id + ":unitPrice"] = "단가는 0 이상의 숫자로 입력하세요.";
      }
    }
    for (const key of metric.dimension_properties) {
      const dimension = dimensionValues[key];
      const name = "dimension:" + key;
      if (!dimension?.value) nextErrors[name] = key + " 값을 입력하세요.";
      else if (dimension.kind === "number" && !Number.isFinite(Number(dimension.value))) nextErrors[name] = key + " 값은 숫자로 입력하세요.";
      else if (dimension.kind === "number" && Math.abs(Number(dimension.value)) > Number.MAX_SAFE_INTEGER) nextErrors[name] = key + " 값이 정확하게 입력할 수 있는 범위를 넘었습니다. API로 단가를 등록하세요.";
      else if (dimension.kind === "boolean" && !["true", "false"].includes(dimension.value)) nextErrors[name] = key + " 값은 true 또는 false로 입력하세요.";
    }
    setDismissedErrors({ state, fields: Object.keys(errors) });
    setClientErrors(nextErrors);
    if (Object.keys(nextErrors).length) {
      requestAnimationFrame(() => formRef.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus());
      return;
    }
    startTransition(() => action(data));
  }

  return <><Button type="primary" onClick={() => setIsOpen(true)}>단가 버전 등록</Button><Modal title={metric.name + " 단가 등록"} open={isOpen} onCancel={close} keyboard={!isPending} mask={{ closable: !isPending }} closable={{ disabled: isPending }} className="workspace-dialog" style={{ top: 24 }} styles={{ body: { maxHeight: "calc(100dvh - 150px)", overflowY: "auto", padding: "4px 4px 4px 0" } }} footer={null} width="min(680px, calc(100vw - 32px))" destroyOnHidden>
    <form ref={formRef} onChange={event => {
      setIsDirty(true);
      const target = event.target as HTMLElement;
      const name = target.dataset.errorField;
      if (!name) return;
      const names = [name, name.startsWith("tier:") ? "tiers" : name.startsWith("dimension:") ? "dimension_values" : name];
      setClientErrors(current => Object.fromEntries(Object.entries(current).filter(([key]) => !names.includes(key))));
      setDismissedErrors(current => ({ state, fields: [...(current.state === state ? current.fields : []), ...names] }));
    }} className="workspace-form" aria-busy={isPending} onSubmit={submit}>
      <input name="operation" type="hidden" value="createPriceRate" /><input name="code" type="hidden" value={metric.code} /><input name="customer_id" type="hidden" value={customerId} /><input name="dimension_values" type="hidden" value={JSON.stringify(encodedDimensions)} /><input name="tiers" type="hidden" value={JSON.stringify(encodedTiers)} />
      <p className="muted">{customerName} / {metric.name} ({metric.code})</p>
      {(Object.keys(errors).length > 0 || state.message && !state.isSuccess) && <div ref={errorRef} tabIndex={-1}><Alert showIcon type="error" title={Object.keys(errors).length ? "입력한 내용을 확인하세요." : state.message} description={Object.keys(errors).length ? <ul>{Object.entries(errors).map(([name, error]) => <li key={name}><a href={"#" + fieldId(name)} onClick={event => { event.preventDefault(); document.getElementById(fieldId(name))?.focus(); }}>{fieldLabel(name)}: {error}</a></li>)}</ul> : undefined} /></div>}
      <fieldset disabled={isPending}>
        <div className={styles.formGrid}><label className="workspace-field"><span>적용 시작 월</span><input {...attributes("effective_from")} type="month" name="effective_from" defaultValue={month < currentMonth ? currentMonth : month} min={currentMonth} required />{errorMessage("effective_from")}</label><label className="workspace-field"><span>적용 종료 월 (선택)</span><input {...attributes("effective_until")} aria-describedby={[errors.effective_until ? fieldId("effective_until") + "-error" : "", fieldId("effective_until") + "-hint"].filter(Boolean).join(" ")} type="month" name="effective_until" min={currentMonth} /><small id={fieldId("effective_until") + "-hint"} className="field-hint">이 월부터 적용되지 않습니다.</small>{errorMessage("effective_until")}</label></div>
        {metric.dimension_properties.length > 0 && <div {...attributes("dimension_values")} tabIndex={-1} role="group" aria-label="속성 조합">
          {metric.dimension_properties.map(key => <div key={key} className={styles.formGrid}><label className="workspace-field"><span>{key} 값</span><input {...attributes("dimension:" + key)} required value={dimensionValues[key]?.value ?? ""} onChange={event => setDimensionValues(current => ({ ...current, [key]: { kind: current[key]?.kind ?? "string", value: event.target.value } }))} />{errorMessage("dimension:" + key)}</label><label className="workspace-field"><span>{key} 값 종류</span><select data-error-field={"dimension:" + key} aria-label={key + " 값 종류"} value={dimensionValues[key]?.kind ?? "string"} onChange={event => setDimensionValues(current => ({ ...current, [key]: { kind: event.target.value, value: event.target.value === "boolean" ? "true" : current[key]?.value ?? "" } }))}><option value="string">문자열</option><option value="number">숫자</option><option value="boolean">불리언 (true/false)</option></select></label></div>)}
          {errorMessage("dimension_values")}
        </div>}
        <label className="workspace-field"><span>계산 방식</span><select {...attributes("model")} aria-label="계산 방식" name="model" value={model} onChange={event => setModel(event.target.value)}><option value="per_unit">단일 단가: 수량에 같은 단가 적용</option><option value="graduated">누진 구간: 구간별로 계산한 뒤 합산</option><option value="volume">총량 구간: 전체 수량으로 한 단가 선택</option></select>{errorMessage("model")}</label>
        {model === "per_unit" ? <label className="workspace-field"><span>사용량 1당 단가 (원)</span><input {...attributes("unit_price")} name="unit_price" type="number" min="0" step="any" defaultValue="0" required />{errorMessage("unit_price")}</label> : <div {...attributes("tiers")} tabIndex={-1} role="group" aria-label="요금 구간" className={styles.tierEditor}><p>끝 수량은 해당 구간에 포함됩니다. 마지막 구간은 상한 없이 적용합니다.</p>{tiers.map((tier, index) => <div key={tier.id} className={styles.tierEditorRow}><span>구간 {index + 1}</span><label className="workspace-field"><span>끝 수량</span>{index === tiers.length - 1 ? <input value="상한 없음" readOnly aria-label={"구간 " + (index + 1) + " 끝 수량"} /> : <input {...attributes("tier:" + tier.id + ":upTo")} aria-label={"구간 " + (index + 1) + " 끝 수량"} type="number" min="0" step="any" required value={tier.upTo} onChange={event => changeTier(tier.id, "upTo", event.target.value)} />}{errorMessage("tier:" + tier.id + ":upTo")}</label><label className="workspace-field"><span>1당 단가 (원)</span><input {...attributes("tier:" + tier.id + ":unitPrice")} aria-label={"구간 " + (index + 1) + " 단가"} type="number" min="0" step="any" required value={tier.unitPrice} onChange={event => changeTier(tier.id, "unitPrice", event.target.value)} />{errorMessage("tier:" + tier.id + ":unitPrice")}</label>{tiers.length > 1 && index < tiers.length - 1 && <Button aria-label={"구간 " + (index + 1) + " 삭제"} onClick={() => setTiers(current => current.filter(item => item.id !== tier.id))}>삭제</Button>}</div>)}<Button onClick={() => setTiers(current => [...current.slice(0, -1), { id: Math.max(...current.map(tier => tier.id)) + 1, upTo: "", unitPrice: "0" }, current[current.length - 1]])}>구간 추가</Button>{errorMessage("tiers")}</div>}
      </fieldset>
      <div className="workspace-dialog-actions"><Button onClick={close} disabled={isPending}>닫기</Button><Button type="primary" htmlType="submit" loading={isPending}>이 단가 버전 저장</Button></div>
    </form>
  </Modal></>;
}

function priceRateFieldName(name: string, tiers: { id: number }[], model: string): string {
  const known: Record<string, string> = { unitPrice: "unit_price", effectiveFrom: "effective_from", effectiveUntil: "effective_until", dimensionValues: "dimension_values", validPricing: model === "per_unit" ? "unit_price" : "tiers", valid_pricing: model === "per_unit" ? "unit_price" : "tiers" };
  const tier = /^tiers\[(\d+)\]\.(upTo|up_to|unitPrice|unit_price)$/.exec(name);
  if (tier && tiers[Number(tier[1])]) return "tier:" + tiers[Number(tier[1])].id + ":" + (["upTo", "up_to"].includes(tier[2]) ? "upTo" : "unitPrice");
  return known[name] ?? name;
}
