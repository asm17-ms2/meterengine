"use client";

import { useMutationScope } from "@/components/auth/use-mutation-scope";
import { useTenantScope } from "@/components/auth/TenantAccess";

import { Alert, App, Button, Empty, Modal, Table, Tag } from "antd";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { startTransition, useActionState, useEffect, useRef, useState } from "react";
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

type Props = { planHistoryStatus: ArchiveStatus; rateHistoryStatus: ArchiveStatus; metricStatus: ArchiveStatus; month: string; currentMonth: string; customerId: string; customers: CustomerResponse[]; metrics: BillableMetricResponse[]; commonPrices: ListBillableMetricPricesResponse["billable_metric_prices"]; scopedPrices: ListBillableMetricPricesResponse["billable_metric_prices"]; histories: Record<string, PriceRateResponse[]>; commonPlans: PricePlanResponse[]; scopedPlans: PricePlanResponse[] };

function activePlan(plans: PricePlanResponse[], month: string) { return plans.find(plan => plan.effective_from <= month && (!plan.effective_until || plan.effective_until > month) && (!plan.archived_from || plan.archived_from > month)); }
function dimensionKey(rate: Pick<PriceRateResponse, "dimension_values">) { return JSON.stringify(Object.entries(rate.dimension_values).sort(([left], [right]) => left.localeCompare(right))); }
function dimensions(rate: Pick<PriceRateResponse, "dimension_values">) { return Object.entries(rate.dimension_values).map(([key, value]) => `${key}: ${String(value)}`).join(", ") || "모든 사용량"; }
function dateRange(rate: { effective_from: string; effective_until: string | null }) { return `${rate.effective_from}부터${rate.effective_until ? ` ${rate.effective_until} 전까지` : " 계속"}`; }

export function PricingWorkspace(props: Props) {
  const canManage = useTenantScope("admin");
  const router = useRouter();
  const search = useSearchParams();
  const matchesStatus = (entry: { archived_at?: string | null }, status: ArchiveStatus) => status === "all" || (status === "archived" ? Boolean(entry.archived_at) : !entry.archived_at);
  const visibleMetrics = props.metrics.filter(metric => matchesStatus(metric, props.metricStatus));
  const selectedCode = visibleMetrics.some(metric => metric.code === search.get("metric")) ? search.get("metric")! : visibleMetrics[0]?.code ?? "";
  const setSelectedCode = (code: string) => { const next = new URLSearchParams(search.toString()); next.set("metric", code); router.replace(`/pricing?${next}`, { scroll: false }); };
  const historyPlans = props.scopedPlans.filter(plan => matchesStatus(plan, props.planHistoryStatus));
  const selectedMetric = props.metrics.find(metric => metric.code === selectedCode);
  const isCustomerArchived = Boolean(props.customers.find(customer => customer.id === props.customerId)?.archived_at);
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
    <form className={styles.scope} method="get">
      <input type="hidden" name="plan_status" value={props.planHistoryStatus} />
      <input type="hidden" name="rate_status" value={props.rateHistoryStatus} />
      <input type="hidden" name="metric_status" value={props.metricStatus} />
      {selectedCode && <input type="hidden" name="metric" value={selectedCode} />}
      <label className="workspace-field"><span>적용 월</span><input aria-label="적용 월" type="month" name="month" defaultValue={props.month} required /></label>
      <label className="workspace-field"><span>적용 대상</span><select aria-label="적용 대상" name="customer_id" defaultValue={props.customerId}><option value="">모든 고객 공통</option>{props.customers.map(customer => <option value={customer.id} key={customer.id}>{customer.name}{customer.archived_at ? " (보관됨)" : ""}</option>)}</select></label>
      <Button htmlType="submit" type="primary">이 조건으로 보기</Button>
      <p>고객별 단가가 있으면 공통 단가보다 먼저 적용합니다.</p>
    </form>
    <div className={styles.equation} aria-label="청구 금액 계산 순서"><span>사용량과 단가로 계산한 사용료</span><b>또는</b><span>최소 사용료 중 큰 금액</span><b>+</b><span>기본료</span><b>=</b><strong>세금 별도 청구 예정액</strong></div>
    <div className={styles.layout}>
      <section className={`workspace-panel ${styles.meterRail}`} aria-label="과금 미터 선택"><h2>무엇을 과금하나요</h2>{visibleMetrics.length ? visibleMetrics.map(metric => <button key={metric.code} type="button" aria-pressed={metric.code === selectedCode} onClick={() => setSelectedCode(metric.code)}><strong>{metric.name}{metric.archived_at ? " (보관됨)" : ""}</strong><code>{metric.code}</code><small style={{ overflowWrap: "anywhere" }}>{metricCalculationFormula(metric)}</small></button>) : <Empty description={props.metricStatus === "archived" ? "보관된 미터가 없습니다. 전체에서 다른 미터를 확인하세요." : props.metricStatus === "all" ? "단가를 적용할 미터가 없습니다" : "사용 중인 미터가 없습니다. 보관됨 또는 전체에서 이전 미터를 확인하세요."}><Link href={props.metrics.length ? "/billable-metrics?status=all" : "/billable-metrics"}>{props.metrics.length ? "미터 관리에서 전체 보기" : "첫 과금 미터 만들기"}</Link></Empty>}</section>
      <section className="workspace-panel" aria-labelledby="current-prices-title"><div className={styles.sectionHeading}><div><h2 id="current-prices-title">{selectedMetric?.name ?? "사용량 단가"}</h2><p>{props.month} / {customerName}</p></div>{canManage && selectedMetric && !selectedMetric.archived_at && !isCustomerArchived && <PriceRateEditor key={`${selectedMetric.code}-${props.customerId}`} metric={selectedMetric} month={props.month} currentMonth={props.currentMonth} customerId={props.customerId} customerName={customerName} />}</div>
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
      <p className="field-hint">선택한 월에 적용된 가격과 변경 이력입니다.</p><details className={styles.history} open><summary>요금제 변경 이력 ({historyPlans.length})</summary><Table<PricePlanResponse> size="small" rowKey="id" scroll={{x:650}} pagination={false} dataSource={historyPlans} locale={{ emptyText: props.planHistoryStatus === "archived" ? "보관된 요금제 이력이 없습니다" : props.planHistoryStatus === "all" ? "등록한 요금제 이력이 없습니다" : "사용 중인 요금제 이력이 없습니다. 보관됨 또는 전체에서 이전 요금제를 확인하세요." }} columns={[
        {title:"적용 기간",key:"period",render:(_,row)=>dateRange(row)},
        {title:"기본료",dataIndex:"base_fee",render:(value:number)=>`${MONEY.format(value)}원`},
        {title:"최소 사용료",dataIndex:"minimum_amount",render:(value:number)=>`${MONEY.format(value)}원`},
      ]} /></details>
    </section>
    {selectedMetric && <section className="workspace-panel"><h2>{selectedMetric.name} 단가 변경 이력</h2><p className="muted">적용 기간에 따라 단가 변경 이력을 확인하세요.</p><Table<PriceRateResponse> size="small" rowKey="id" scroll={{x:750}} pagination={{pageSize:8}} dataSource={(props.histories[selectedCode] ?? []).filter(rate => matchesStatus(rate, props.rateHistoryStatus))} locale={{emptyText: props.rateHistoryStatus === "archived" ? "보관된 단가 이력이 없습니다" : props.rateHistoryStatus === "all" ? "등록한 단가 이력이 없습니다" : "사용 중인 단가 이력이 없습니다. 보관됨 또는 전체에서 이전 단가를 확인하세요."}} columns={[
      {title:"조합",key:"dimensions",render:(_,rate)=>dimensions(rate)},
      {title:"적용 기간",key:"period",render:(_,rate)=>dateRange(rate)},
      {title:"계산 방식",key:"model",render:(_,rate)=><Tag>{MODEL_LABELS[rate.model]}</Tag>},
      {title:"단가",key:"price",render:(_,rate)=>rate.unit_price !== null ? `${MONEY.format(rate.unit_price)}원` : `${rate.tiers?.length ?? 0}개 구간`},
    ]} /></section>}
    <div className={styles.next}><div><strong>가격 설정이 끝났나요</strong><p>이벤트를 수집한 뒤 청구 예정액에서 실제 적용 금액과 누락된 조합을 확인하세요.</p></div><Link href={`/billing?month=${encodeURIComponent(props.month)}${props.customerId ? `&customer_id=${encodeURIComponent(props.customerId)}` : ""}`}>청구 예정액 확인</Link></div>
  </>;
}

function RateDetails({ rate }: { rate: BillableMetricPricePriceRate }) {
  if (rate.model === "per_unit") return <p className={styles.unitPrice}><strong>{MONEY.format(rate.unit_price ?? 0)}원</strong> / 사용량 1</p>;
  let lower = 0;
  return <div className={styles.tierList}><p>{rate.model === "graduated" ? "구간마다 해당 수량에 단가를 곱해 더합니다." : "전체 수량이 속한 구간의 단가를 모든 수량에 적용합니다."}</p>{rate.tiers?.map((tier,index)=>{const start=lower;lower=tier.up_to??lower;return <div key={index}><span>{start === 0 ? "0부터" : `${MONEY.format(start)} 초과`}{tier.up_to === null ? " 계속" : ` ${MONEY.format(tier.up_to)}까지`}</span><strong>{MONEY.format(tier.unit_price)}원 / 1</strong></div>;})}</div>;
}

function PriceRateEditor({ metric, month, currentMonth, customerId, customerName }: { metric: BillableMetricResponse; month: string; currentMonth: string; customerId: string; customerName: string }) {
  const [isOpen,setIsOpen]=useState(false);
  const [model,setModel]=useState("per_unit");
  const [tiers,setTiers]=useState([{id:0,upTo:"100",unitPrice:"0"},{id:1,upTo:"",unitPrice:"0"}]);
  const [dimensionValues,setDimensionValues]=useState<Record<string,{kind:string;value:string}>>({});
  const [isDirty, setIsDirty] = useState(false);
  const { message } = App.useApp();
  const bindMutationScope = useMutationScope();
  const [state,action,isPending]=useActionState(async (previous: import("@/app/(console)/workspace-actions").WorkspaceActionState, data: FormData) => { const result = await workspaceAction(previous, bindMutationScope(data)); if (result.isSuccess) { setIsDirty(false); setIsOpen(false); message.success("단가 버전을 저장했습니다."); } return result; },{});
  const [error,setError]=useState("");
  const errorRef = useRef<HTMLDivElement>(null);
  useEffect(() => { if (error || (state.message && !state.isSuccess)) errorRef.current?.focus(); }, [error, state]);
  const close = () => { if (!isPending && (!isDirty || window.confirm("작성 중인 단가를 버리고 닫을까요?"))) setIsOpen(false); };
  const encodedDimensions = Object.fromEntries(metric.dimension_properties.map(key=>{const entry=dimensionValues[key] ?? {kind:"string",value:""};return [key,entry.kind==="number" ? Number(entry.value) : entry.kind==="boolean" ? entry.value==="true" : entry.value];}));
  const encodedTiers=tiers.map((tier,index)=>({up_to:index===tiers.length-1?null:tier.upTo,unit_price:tier.unitPrice}));
  const changeTier=(id:number,key:"upTo"|"unitPrice",value:string)=>setTiers(current=>current.map(tier=>tier.id===id?{...tier,[key]:value}:tier));
  return <><Button type="primary" onClick={()=>setIsOpen(true)}>단가 버전 등록</Button><Modal title={`${metric.name} 단가 등록`} open={isOpen} onCancel={close} keyboard={!isPending} mask={{ closable: !isPending }} closable={{ disabled: isPending }} className="workspace-dialog" style={{ top: 24 }} styles={{ body: { maxHeight: "calc(100dvh - 150px)", overflowY: "auto", padding: "4px 4px 4px 0" } }} footer={null} width="min(680px, calc(100vw - 32px))" destroyOnHidden>
    <form onChange={() => setIsDirty(true)} className="workspace-form" aria-busy={isPending} onSubmit={event=>{event.preventDefault();let problem="";if(model!=="per_unit"){let previous=0;for(const tier of tiers.slice(0,-1)){const upper=Number(tier.upTo);if(!Number.isFinite(upper)||upper<=previous){problem="구간의 끝 수량은 0보다 크고 앞 구간보다 커야 합니다.";break;}previous=upper;}}for(const key of metric.dimension_properties){const dimension=dimensionValues[key];if(!dimension?.value || (dimension.kind==="number" && !Number.isFinite(Number(dimension.value))))problem="각 속성의 값을 입력하세요.";if(dimension?.kind==="boolean" && !["true","false"].includes(dimension.value))problem="불리언 속성은 true 또는 false로 입력하세요.";if(dimension?.kind==="number" && Math.abs(Number(dimension.value))>Number.MAX_SAFE_INTEGER)problem="숫자 속성이 정확하게 입력할 수 있는 범위를 넘었습니다. API로 단가를 등록하세요.";}if(problem){setError(problem);}else { setError(""); const data = new FormData(event.currentTarget); startTransition(() => action(data)); }}}>
      <input name="operation" type="hidden" value="createPriceRate" /><input name="code" type="hidden" value={metric.code} /><input name="customer_id" type="hidden" value={customerId} /><input name="dimension_values" type="hidden" value={JSON.stringify(encodedDimensions)} /><input name="tiers" type="hidden" value={JSON.stringify(encodedTiers)} />
      <p className="muted">{customerName} / {metric.name} ({metric.code})</p>
      <fieldset disabled={isPending}>
        <div className={styles.formGrid}><label className="workspace-field"><span>적용 시작 월</span><input type="month" name="effective_from" defaultValue={month<currentMonth?currentMonth:month} min={currentMonth} required /></label><label className="workspace-field"><span>적용 종료 월 (선택)</span><input type="month" name="effective_until" min={currentMonth} /><small className="field-hint">이 월부터 적용되지 않습니다.</small></label></div>
        {metric.dimension_properties.map(key=><div key={key} className={styles.formGrid}><label className="workspace-field"><span>{key} 값</span><input required value={dimensionValues[key]?.value??""} onChange={event=>setDimensionValues(current=>({...current,[key]:{kind:current[key]?.kind??"string",value:event.target.value}}))} /></label><label className="workspace-field"><span>{key} 값 종류</span><select aria-label={`${key} 값 종류`} value={dimensionValues[key]?.kind??"string"} onChange={event=>setDimensionValues(current=>({...current,[key]:{kind:event.target.value,value:event.target.value==="boolean"?"true":current[key]?.value??""}}))}><option value="string">문자열</option><option value="number">숫자</option><option value="boolean">불리언 (true/false)</option></select></label></div>)}
        <label className="workspace-field"><span>계산 방식</span><select aria-label="계산 방식" name="model" value={model} onChange={event=>setModel(event.target.value)}><option value="per_unit">단일 단가: 수량에 같은 단가 적용</option><option value="graduated">누진 구간: 구간별로 계산한 뒤 합산</option><option value="volume">총량 구간: 전체 수량으로 한 단가 선택</option></select></label>
        {model==="per_unit"?<label className="workspace-field"><span>사용량 1당 단가 (원)</span><input name="unit_price" type="number" min="0" step="any" defaultValue="0" required /></label>:<div className={styles.tierEditor}><p>끝 수량은 해당 구간에 포함됩니다. 마지막 구간은 상한 없이 적용합니다.</p>{tiers.map((tier,index)=><div key={tier.id} className={styles.tierEditorRow}><span>구간 {index+1}</span><label className="workspace-field"><span>끝 수량</span>{index===tiers.length-1?<input value="상한 없음" readOnly aria-label={`구간 ${index+1} 끝 수량`} />:<input aria-label={`구간 ${index+1} 끝 수량`} type="number" min="0" step="any" required value={tier.upTo} onChange={event=>changeTier(tier.id,"upTo",event.target.value)} />}</label><label className="workspace-field"><span>1당 단가 (원)</span><input aria-label={`구간 ${index+1} 단가`} type="number" min="0" step="any" required value={tier.unitPrice} onChange={event=>changeTier(tier.id,"unitPrice",event.target.value)} /></label>{tiers.length>1 && index<tiers.length-1 && <Button aria-label={`구간 ${index+1} 삭제`} onClick={()=>setTiers(current=>current.filter(item=>item.id!==tier.id))}>삭제</Button>}</div>)}<Button onClick={()=>setTiers(current=>[...current.slice(0,-1),{id:Math.max(...current.map(tier=>tier.id))+1,upTo:"",unitPrice:"0"},current[current.length-1]])}>구간 추가</Button></div>}
      </fieldset>
      {(error || state.message) && <div ref={errorRef} tabIndex={-1}><Alert showIcon type={error || !state.isSuccess ? "error" : "success"} title={error || state.message} /></div>}
      <div className="workspace-dialog-actions"><Button onClick={close} disabled={isPending}>닫기</Button><Button type="primary" htmlType="submit" loading={isPending}>이 단가 버전 저장</Button></div>
    </form>
  </Modal></>;
}
