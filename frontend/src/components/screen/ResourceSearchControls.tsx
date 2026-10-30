"use client";

import { useWorkspaceSearch } from "@/components/workspace/use-workspace-search";

import { useRef, useState, type FormEvent } from "react";
import { usePathname, useRouter } from "next/navigation";
import { Button, Select } from "antd";
import { queryOptionsAction } from "@/app/query-options-actions";
import { CustomerFilter, type CustomerOption } from "@/components/screen/CustomerFilter";
import { MonthPicker } from "@/components/screen/MonthPicker";
import { useListNavigation } from "@/components/screen/use-list-navigation";
import { EnvironmentInput } from "@/components/workspace/EnvironmentSwitch";
import type { ListCapabilities } from "@/lib/api/capabilities";
import type { QueryOption } from "@/lib/api/search";
import { INVOICE_PAYMENT_STATUS_LABELS } from "@/lib/invoice-payment-status";
import { PAGE_SIZE_OPTIONS, type ListQuery, type ListQueryOverrides } from "@/lib/list-query";
import { resourceRangeErrors, submittedResourceParameters, type ResourceRangeError } from "@/lib/resource-query";


export type SearchResource = "customers" | "billable-metrics" | "billable-metric-prices" | "usage" | "billing" | "invoices";

type Props = {
  query: ListQuery;
  capabilities: ListCapabilities;
  resource: SearchResource;
  month?: { value: string; allowAll?: boolean };
  customers?: CustomerOption[];
  isLoadingOptions?: boolean;
};

export function resourceSearchFormId(resource: SearchResource) {
  return `resource-search-${resource}`;
}

const SORTS: Record<SearchResource, { value: string; label: string }[]> = {
  customers: [{ value: "name", label: "고객명" }, { value: "external_id", label: "외부 고객 ID" }, { value: "created_at", label: "등록 시각" }],
  "billable-metrics": [{ value: "code", label: "미터 코드" }, { value: "name", label: "미터명" }, { value: "event_type", label: "이벤트 유형" }, { value: "aggregation", label: "집계 방식" }],
  "billable-metric-prices": [{ value: "code", label: "미터 코드" }, { value: "name", label: "미터명" }, { value: "rate_count", label: "단가 수" }],
  usage: [{ value: "customer_name", label: "고객명" }, { value: "customer_id", label: "고객 ID" }, { value: "line_count", label: "집계 라인 수" }],
  billing: [{ value: "customer_name", label: "고객명" }, { value: "amount", label: "청구 예정액" }, { value: "line_count", label: "청구 라인 수" }],
  invoices: [{ value: "month", label: "청구 월" }, { value: "customer_name", label: "고객명" }, { value: "total_amount", label: "청구 금액" }, { value: "finalized_at", label: "확정 시각" }, { value: "invoice_number", label: "인보이스 번호" }, { value: "status", label: "수납 상태" }],
};

export function ResourceSearchControls(props: Props) {
  const { query } = useListNavigation(props.query, props.capabilities);
  return <ResourceSearchForm key={JSON.stringify(props.query)} {...props} query={query} />;
}

function ResourceSearchForm({ query, capabilities, resource, month, customers = [], isLoadingOptions = false }: Props) {
  const pathname = usePathname();
  const router = useRouter();
  const search = useWorkspaceSearch();
  const [draft, setDraft] = useState(query);
  const [options, setOptions] = useState<QueryOption[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [optionError, setOptionError] = useState("");
  const latestRequest = useRef(0);
  const metricSearchText = useRef("");
  const formId = resourceSearchFormId(resource);
  const appliedAdvancedCount = [
    Boolean(query.sort && (query.sort !== SORTS[resource][0].value || query.order && query.order !== (resource === "invoices" ? "desc" : "asc"))),
    query.size !== PAGE_SIZE_OPTIONS[0],
    Boolean(query.minAmount || query.maxAmount),
    Boolean(query.mode),
    Boolean(query.from || query.to),
  ].filter(Boolean).length;
  const [isAdvancedOpen, setIsAdvancedOpen] = useState(appliedAdvancedCount > 0);

  const [rangeErrors, setRangeErrors] = useState<ResourceRangeError[]>([]);

  function change(overrides: ListQueryOverrides) {
    setDraft(current => ({ ...current, ...overrides }));
  }

  function revealInvalid(event: FormEvent<HTMLFormElement>) {
    const target = event.target;
    if (!(target instanceof HTMLInputElement || target instanceof HTMLSelectElement)) return;
    const disclosure = target.closest("details");
    if (disclosure) {
      disclosure.open = true;
      setIsAdvancedOpen(true);
      target.focus();
    }
  }

  function apply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const submitter = (event.nativeEvent as SubmitEvent).submitter;
    const parameters = submittedResourceParameters(data, submitter instanceof HTMLButtonElement ? { name: submitter.name, value: submitter.value } : undefined);
    const errors = resourceRangeErrors(parameters);
    setRangeErrors(errors);
    if (errors.length) {
      const field = event.currentTarget.elements.namedItem(errors[0].field);
      if (field instanceof HTMLInputElement) {
        const disclosure = field.closest("details");
        if (disclosure) disclosure.open = true;
        setIsAdvancedOpen(true);
        field.focus();
      }
      return;
    }
    router.push(`${pathname}${parameters.size ? `?${parameters}` : ""}`);
  }

  function rangeErrorProps(field: ResourceRangeError["field"]) {
    return rangeErrors.some(error => error.field === field) ? { "aria-invalid": true, "aria-describedby": `${formId}-${field}-error` } : {};
  }

  function rangeErrorText(field: ResourceRangeError["field"]) {
    const error = rangeErrors.find(candidate => candidate.field === field);
    return error ? <span role="alert" className="field-error" id={`${formId}-${field}-error`}>{error.message}</span> : null;
  }

  async function loadMetrics(q = metricSearchText.current) {
    metricSearchText.current = q;
    const request = ++latestRequest.current;
    setIsLoading(true);
    try {
      const result = await queryOptionsAction("billable-metrics", q);
      if (latestRequest.current !== request) return;
      if (result.ok) { setOptions(result.data); setOptionError(""); }
      else { setOptions([]); setOptionError(result.error.message); }
    } catch {
      if (latestRequest.current !== request) return;
      setOptions([]);
      setOptionError("미터를 찾지 못했습니다.");
    } finally {
      if (latestRequest.current === request) setIsLoading(false);
    }
  }

  return <div className="resource-search">
    <p className="field-hint" id={`${formId}-hint`}>조건을 함께 선택한 뒤 조건 적용을 누르세요.</p>
    <form id={formId} method="get" className="workspace-actions" aria-label="조회 조건" aria-describedby={`${formId}-hint`} onSubmit={apply} onInvalidCapture={revealInvalid}>
      <EnvironmentInput />
      {Object.entries(draft.preservedParameters ?? {}).map(([name, value]) => <input key={name} type="hidden" name={name} value={value} />)}
      {!month && search.get("month") && <input type="hidden" name="month" value={search.get("month")!} />}
      {month && <div className="workspace-field"><label htmlFor={`${formId}-month`}>{resource === "billable-metric-prices" ? "적용 월" : "조회 기간"}</label><MonthPicker id={`${formId}-month`} label={resource === "billable-metric-prices" ? "적용 월" : "조회 기간"} query={draft} capabilities={capabilities} month={month.value} allowAll={month.allowAll} onChange={value => change({ month: value, from: undefined, to: undefined, rangeMode: undefined })} /><input type="hidden" name="month" value={draft.month ?? month.value} /></div>}
      {capabilities.archive && <label className="workspace-field"><span>{resource === "billable-metric-prices" ? "미터 관리 상태" : "관리 상태"}</span><select name={draft.archiveParameter ?? "status"} value={draft.status ?? "active"} onChange={event => change({ status: event.target.value as ListQuery["status"] })}><option value="active">사용 중</option><option value="archived">보관됨</option><option value="all">전체</option></select></label>}
      {capabilities.customer_id && <div className="workspace-field"><label htmlFor={`${formId}-customer`}>{resource === "billable-metric-prices" ? "적용 대상" : "고객"}</label><CustomerFilter id={`${formId}-customer`} query={draft} capabilities={capabilities} customers={customers} isLoadingOptions={isLoadingOptions} label={resource === "billable-metric-prices" ? "적용 대상" : "고객"} selectedCustomerId={draft.customerId} onChange={customerId => change({ customerId })} /><input type="hidden" name="customer_id" value={draft.customerId ?? ""} /></div>}
      {capabilities.metric && <div className="workspace-field"><label htmlFor={`${resource}-metric`}>과금 미터</label><Select id={`${resource}-metric`} aria-label="과금 미터" style={{ minWidth: 180 }} allowClear placeholder="모든 미터" value={draft.metricCode} options={options.map(option => ({ value: option.value, label: `${option.label}${option.archived ? " (보관됨)" : ""}` }))} loading={isLoading} showSearch={{ filterOption: false, onSearch: loadMetrics }} onOpenChange={open => { if (open) void loadMetrics(); }} onChange={metricCode => change({ metricCode })} notFoundContent={isLoading ? "조회 중" : optionError || "일치하는 미터가 없습니다"} /><input type="hidden" name="billable_metric_code" value={draft.metricCode ?? ""} /></div>}
      {capabilities.q && <label className="workspace-field resource-search-text"><span>{resource === "customers" ? "이름, 외부 ID, 청구 이메일로 찾기" : "검색"}</span><input type="search" name="q" maxLength={255} value={draft.q} onChange={event => change({ q: event.target.value })} placeholder={resource === "customers" ? "이름, 외부 ID, 청구 이메일" : "이름 또는 코드"} /></label>}
      {capabilities.paymentStatus && <label className="workspace-field"><span>수납 상태</span><select name="payment_status" value={draft.paymentStatus ?? ""} onChange={event => change({ paymentStatus: event.target.value })}><option value="">전체</option>{["unpaid", "pending", "paid", "failed", "partially_canceled", "canceled"].map(status => <option key={status} value={status}>{INVOICE_PAYMENT_STATUS_LABELS[status]}</option>)}</select></label>}
      <details className="event-advanced resource-advanced" open={isAdvancedOpen} onToggle={event => setIsAdvancedOpen(event.currentTarget.open)} style={{ width: "100%" }}>
        <summary>상세 조건{appliedAdvancedCount > 0 ? ` (적용 ${appliedAdvancedCount}개)` : ""}</summary>
        <div className="workspace-actions" style={{ marginTop: 12 }}>
          <label className="workspace-field"><span>정렬 기준</span><select name="sort" value={draft.sort ?? SORTS[resource][0].value} onChange={event => change({ sort: event.target.value })}>{SORTS[resource].map(sort => <option key={sort.value} value={sort.value}>{sort.label}</option>)}</select></label>
          <label className="workspace-field"><span>정렬 방향</span><select name="order" value={draft.order ?? (resource === "invoices" ? "desc" : "asc")} onChange={event => change({ order: event.target.value as ListQuery["order"] })}><option value="asc">오름차순</option><option value="desc">내림차순</option></select></label>
          {capabilities.amount && <><label className="workspace-field"><span>최소 금액 (원)</span><input type="number" name="min_amount" min="0" step="1" value={draft.minAmount ?? ""} onChange={event => change({ minAmount: event.target.value })} /></label><div className="workspace-field"><label htmlFor={`${formId}-max-amount`}>최대 금액 (원)</label><input id={`${formId}-max-amount`} type="number" name="max_amount" min="0" step="1" value={draft.maxAmount ?? ""} onChange={event => change({ maxAmount: event.target.value })} {...rangeErrorProps("max_amount")} />{rangeErrorText("max_amount")}</div></>}
          {capabilities.mode && (!query.environment || query.environment === "legacy") && <label className="workspace-field"><span>결제 모드</span><select name="mode" value={draft.mode ?? ""} onChange={event => change({ mode: event.target.value === "live" ? "live" : event.target.value === "test" ? "test" : undefined })}><option value="">현재 결제 모드</option><option value="test">테스트 결제</option><option value="live">실결제</option></select></label>}
          {resource === "invoices" && <><label className="workspace-field"><span>확정 시각 시작 (KST)</span><input type="datetime-local" step="1" name="from" value={draft.from ?? ""} onChange={event => change({ from: event.target.value })} /></label><div className="workspace-field"><label htmlFor={`${formId}-to`}>확정 시각 종료 전 (KST)</label><input id={`${formId}-to`} type="datetime-local" step="1" name="to" value={draft.to ?? ""} onChange={event => change({ to: event.target.value })} {...rangeErrorProps("to")} />{rangeErrorText("to")}</div></>}
          {capabilities.size && <label className="workspace-field"><span>페이지 크기</span><select aria-label="페이지 크기" name="size" value={draft.size} onChange={event => change({ size: Number(event.target.value) })}>{PAGE_SIZE_OPTIONS.map(size => <option key={size} value={size}>{size}건씩</option>)}</select></label>}
        </div>
      </details>
      <Button htmlType="submit">조건 적용</Button>
    </form>
    {optionError && <p role="status">미터 선택지를 불러오지 못했습니다. 다시 열어 조회하세요.</p>}
  </div>;
}
