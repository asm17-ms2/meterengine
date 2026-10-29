"use client";

import { useWorkspaceSearch } from "@/components/workspace/use-workspace-search";

import { Button } from "antd";
import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import { useRouter } from "next/navigation";
import { useId, useMemo, useRef, useState, useTransition, type FormEvent } from "react";
import { buildListHref, PAGE_SIZE_OPTIONS, rangeEndpoint, type ListQueryOverrides, type MonthlyListQuery } from "@/lib/list-query";
import { CustomerFilter, type CustomerOption } from "@/components/screen/CustomerFilter";
import { CAPABILITIES } from "@/lib/api/capabilities";

type RangeUnit = "date" | "datetime";

function localTime(value?: string) {
  if (!value) return "";
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return `${value}T00:00:00`;
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?$/.test(value)) return value;
  const date = new Date(value);
  return Number.isFinite(date.getTime()) ? new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false }).format(date).replace(" ", "T") : value;
}

function shiftDay(value: string, days: number) {
  const date = new Date(`${value}T00:00:00Z`);
  if (!Number.isFinite(date.getTime())) return value;
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

function monthRange(month: string) {
  const start = `${month}-01`;
  const end = new Date(`${start}T00:00:00Z`);
  end.setUTCMonth(end.getUTCMonth() + 1);
  end.setUTCDate(end.getUTCDate() - 1);
  return { from: start, to: end.toISOString().slice(0, 10) };
}

function toDateEnd(value: string) {
  return /T00:00(?::00)?$/.test(value) ? shiftDay(value.slice(0, 10), -1) : value.slice(0, 10);
}

function appliedPeriod(query: MonthlyListQuery) {
  if (!query.from && !query.to) return `${query.month} 월별, 한국 시간(KST)`;
  const from = localTime(query.from);
  const to = localTime(query.to);
  return query.rangeMode === "date"
    ? `${from.slice(0, 10) || "시작 제한 없음"}부터 ${to.slice(0, 10) || "종료 제한 없음"}까지, 종료 날짜 포함, KST`
    : `${from.replace("T", " ") || "시작 제한 없음"}부터 ${to.replace("T", " ") || "종료 제한 없음"} 전까지, KST`;
}

export function EventFilters({ query: initialQuery, customers, types, optionsError }: { query: MonthlyListQuery; customers: CustomerOption[]; types: string[]; optionsError?: string }) {
  const scope = useWorkspaceSearch();
  const organizationId = initialQuery.organizationId || scope.get("organization_id") || undefined;
  const query = useMemo(() => ({ ...initialQuery, organizationId }), [initialQuery, organizationId]);
  const router = useRouter();
  const formRef = useRef<HTMLFormElement>(null);
  const detailsRef = useRef<HTMLDetailsElement>(null);
  const unitDraftRef = useRef<{ unit: RangeUnit; from: string; to: string; convertedFrom: string; convertedTo: string } | undefined>(undefined);
  const id = useId();
  const hasRange = Boolean(query.from || query.to);
  const initialUnit: RangeUnit = hasRange && query.rangeMode !== "date" ? "datetime" : "date";
  const defaults = monthRange(query.month);
  const [period, setPeriod] = useState(hasRange ? "custom" : "month");
  const [unit, setUnit] = useState<RangeUnit>(initialUnit);
  const [month, setMonth] = useState(query.month);
  const [customerId, setCustomerId] = useState(query.customerId);
  const [from, setFrom] = useState(query.from ? initialUnit === "date" ? localTime(query.from).slice(0, 10) : localTime(query.from) : initialUnit === "date" ? defaults.from : `${defaults.from}T00:00:00`);
  const [to, setTo] = useState(query.to ? initialUnit === "date" ? localTime(query.to).slice(0, 10) : localTime(query.to) : initialUnit === "date" ? defaults.to : `${shiftDay(defaults.to, 1)}T00:00:00`);
  const [isEdited, setIsEdited] = useState(false);
  const [error, setError] = useState<{ field: "range" | "properties"; message: string }>();
  const [isPending, startTransition] = useTransition();
  const detailCount = Number(Boolean(query.sort && query.sort !== "occurred_at") || query.order === "asc") + Number(query.size !== 20) + Number(Boolean(query.properties));
  const resetHref = buildListHref("/events", query, { from: undefined, to: undefined, rangeMode: undefined, customerId: undefined, type: undefined, q: "", properties: undefined, sort: undefined, order: undefined });

  function selectMonth(value: string) {
    setMonth(value);
    unitDraftRef.current = undefined;
    if (value) {
      const range = monthRange(value);
      setFrom(unit === "date" ? range.from : `${range.from}T00:00:00`);
      setTo(unit === "date" ? range.to : `${shiftDay(range.to, 1)}T00:00:00`);
    }
  }

  function changeUnit(next: RangeUnit) {
    if (next === unit) return;
    const previous = unitDraftRef.current;
    const isReturning = previous?.unit === next && previous.convertedFrom === from && previous.convertedTo === to;
    const nextFrom = isReturning ? previous.from : !from ? from : next === "date" ? from.slice(0, 10) : `${from.slice(0, 10)}T00:00:00`;
    const nextTo = isReturning ? previous.to : !to ? to : next === "date" ? toDateEnd(to) : `${shiftDay(to.slice(0, 10), 1)}T00:00:00`;
    unitDraftRef.current = { unit, from, to, convertedFrom: nextFrom, convertedTo: nextTo };
    setFrom(nextFrom);
    setTo(nextTo);
    setUnit(next);
    setError(undefined);
  }

  function showError(field: "range" | "properties", message: string) {
    setError({ field, message });
    if (field === "properties" && detailsRef.current) detailsRef.current.open = true;
    requestAnimationFrame(() => formRef.current?.querySelector<HTMLElement>(field === "range" ? '[name="from"]' : '[name="properties"]')?.focus());
  }

  function navigate(values: FormData, range: ListQueryOverrides) {
    const properties = String(values.get("properties") ?? "").trim();
    if (properties) {
      try {
        const parsed: unknown = JSON.parse(properties);
        if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error();
      } catch { showError("properties", '속성 필터를 JSON 객체로 입력하세요. 예: {"model":"large"}'); return; }
    }
    setError(undefined);
    const href = buildListHref("/events", query, { month, customerId: String(values.get("customer_id") ?? "") || undefined, type: String(values.get("type") ?? "").trim() || undefined, q: String(values.get("q") ?? "").trim(), sort: String(values.get("sort") ?? "occurred_at"), order: values.get("order") === "asc" ? "asc" : "desc", properties: properties || undefined, size: Number(values.get("size")), ...range });
    startTransition(() => {
      if (href === buildListHref("/events", query)) {
        setIsEdited(false);
        router.refresh();
      } else router.push(href);
    });
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isPending) return;
    const submitter = (event.nativeEvent as SubmitEvent).submitter;
    if (submitter instanceof HTMLButtonElement && submitter.name === "month") {
      setPeriod("month");
      selectMonth(submitter.value);
      setIsEdited(true);
      navigate(new FormData(event.currentTarget), { month: submitter.value, from: undefined, to: undefined, rangeMode: undefined });
      return;
    }
    if (period === "custom" && (!from || !to || rangeEndpoint(from, false, unit) >= rangeEndpoint(to, true, unit))) {
      showError("range", "시작과 종료를 확인하세요. 종료는 시작보다 뒤여야 합니다.");
      return;
    }
    navigate(new FormData(event.currentTarget), { from: period === "month" ? undefined : from, to: period === "month" ? undefined : to, rangeMode: unit });
  }

  function recent(days: number) {
    if (!formRef.current) return;
    const end = new Date();
    const start = new Date(end.getTime() - days * 86_400_000);
    navigate(new FormData(formRef.current), { from: start.toISOString(), to: end.toISOString(), rangeMode: "datetime" });
  }

  return <form id="event-search" ref={formRef} className="event-filters" onSubmit={submit} onChange={() => { setIsEdited(true); setError(undefined); }} aria-busy={isPending}>
    <fieldset disabled={isPending} className="event-filter-fields">
      <fieldset className="event-period">
        <legend>조회 기간</legend>
        <div className="event-period-toolbar">
          <fieldset className="event-period-choice"><legend className="sr-only">기간 선택 방식</legend>
            <label><input type="radio" name="period_mode" value="month" checked={period === "month"} onChange={() => setPeriod("month")} />월별</label>
            <label><input type="radio" name="period_mode" value="custom" checked={period === "custom"} onChange={() => setPeriod("custom")} />직접 지정</label>
          </fieldset>
          <div className="event-period-shortcuts"><Button onClick={() => recent(1)}>최근 24시간</Button><Button onClick={() => recent(7)}>최근 7일</Button></div>
        </div>
        {period === "month" ? <label className="workspace-field event-month"><span>조회 월</span><input name="month" type="month" value={month} onChange={event => selectMonth(event.target.value)} required /></label> : <div className="event-range-grid">
          <label className="workspace-field"><span>지정 단위</span><select name="range_unit" value={unit} onChange={event => changeUnit(event.target.value as RangeUnit)}><option value="date">날짜</option><option value="datetime">날짜와 시각 (초 단위)</option></select></label>
          <label className="workspace-field"><span>시작 {unit === "date" ? "날짜" : "시각"} (포함)</span><input name="from" type={unit === "date" ? "date" : "datetime-local"} step={1} value={from} onChange={event => setFrom(event.target.value)} required aria-invalid={error?.field === "range"} aria-describedby={`${id}-range-help${error?.field === "range" ? ` ${id}-range-error` : ""}`} /></label>
          <label className="workspace-field"><span>종료 {unit === "date" ? "날짜 (해당 일 포함)" : "시각 (미포함)"}</span><input name="to" type={unit === "date" ? "date" : "datetime-local"} step={1} value={to} onChange={event => setTo(event.target.value)} required aria-invalid={error?.field === "range"} aria-describedby={`${id}-range-help${error?.field === "range" ? ` ${id}-range-error` : ""}`} /></label>
          <p id={`${id}-range-help`} className="field-hint">한국 시간(KST). {unit === "date" ? "종료 날짜 전체를 포함합니다." : "시작 시각을 포함하고 종료 시각은 포함하지 않습니다."}</p>
          {error?.field === "range" && <p id={`${id}-range-error`} role="alert" className="field-error">{error.message}</p>}
        </div>}
      </fieldset>
      <div className="event-filter-grid">
        <div className="workspace-field"><label htmlFor={`${id}-customer`}>고객</label><CustomerFilter id={`${id}-customer`} query={query} capabilities={CAPABILITIES.listEvents} customers={customers} selectedCustomerId={customerId} onChange={value => { setCustomerId(value); setIsEdited(true); setError(undefined); }} disabled={isPending} /><input type="hidden" name="customer_id" value={customerId ?? ""} /></div>
        <label className="workspace-field"><span>이벤트 유형</span><input name="type" list={`${id}-types`} defaultValue={query.type} placeholder="모든 유형" autoComplete="off" /><datalist id={`${id}-types`}>{types.map(type => <option key={type} value={type} />)}</datalist></label>
        <label className="workspace-field event-search"><span>이벤트 또는 고객 찾기</span><input type="search" name="q" defaultValue={query.q} placeholder="전송 ID, 고객명, 외부 고객 ID" autoComplete="off" /></label>
      </div>
      <details ref={detailsRef} className="event-advanced" open={detailCount > 0}>
        <summary>상세 조건{detailCount > 0 && <span className="field-hint"> (적용 {detailCount}개)</span>}</summary>
        <div className="event-filter-grid">
          <label className="workspace-field"><span>정렬 기준</span><select name="sort" defaultValue={query.sort ?? "occurred_at"}><option value="occurred_at">발생 시각</option><option value="received_at">수신 시각</option><option value="transaction_id">전송 ID</option><option value="customer_name">고객명</option><option value="type">이벤트 유형</option></select></label>
          <label className="workspace-field"><span>정렬 순서</span><select name="order" defaultValue={query.order ?? "desc"}><option value="desc">내림차순 (최신순)</option><option value="asc">오름차순</option></select></label>
          <label className="workspace-field"><span>페이지당 표시</span><select name="size" defaultValue={query.size}>{PAGE_SIZE_OPTIONS.map(size => <option key={size} value={size}>{size}건</option>)}</select></label>
        </div>
        <div className="workspace-field"><label htmlFor={`${id}-properties`}>속성과 값 (JSON 객체)</label><textarea id={`${id}-properties`} name="properties" rows={3} defaultValue={query.properties ?? ""} placeholder={'{"model":"large"}'} aria-invalid={error?.field === "properties"} aria-describedby={`${id}-properties-help${error?.field === "properties" ? ` ${id}-properties-error` : ""}`} /></div>
        <p className="field-hint" id={`${id}-properties-help`}>입력한 속성과 값이 모두 일치하는 이벤트를 찾습니다. 문자, 숫자와 불리언 값을 구분합니다.</p>
        {error?.field === "properties" && <p id={`${id}-properties-error`} role="alert" className="field-error">{error.message}</p>}
      </details>
      <div className="event-filter-submit"><Button htmlType="submit" loading={isPending}>조건 적용</Button><Link href={resetHref}>조건 초기화</Link>{isEdited && !isPending && <span className="field-hint">조건 변경됨. 조건 적용을 눌러 반영하세요.</span>}</div>
    </fieldset>
    <p role="status" aria-label="현재 조회 기간" className="event-applied-period">{isPending ? "조회 중입니다." : `현재 조회: ${appliedPeriod(query)}`}</p>
    {optionsError && <p role="status" className="field-hint">{optionsError} 유형은 직접 입력할 수 있습니다. <a href="">후보 다시 불러오기</a></p>}
  </form>;
}
