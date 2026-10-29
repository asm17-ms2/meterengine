import type { ListCapabilities } from "@/lib/api/capabilities";
import { isDevStateEnabled, readDevState, type DevState } from "@/lib/dev-state";
import { ALL_MONTHS, readMonth, readMonthOrAll } from "@/lib/month";

export const DEFAULT_PAGE_SIZE = 20;
export const MAX_PAGE_SIZE = 100;
export const PAGE_SIZE_OPTIONS = [20, 50, 100] as const;

export type SortDirection = "asc" | "desc";
export type ArchiveStatus = "active" | "archived" | "all";

export type ListQuery = {
  organizationId?: string;
  archiveParameter?: "metric_status";
  preservedParameters?: Record<string, string>;
  month?: string;
  from?: string;
  to?: string;
  rangeMode?: "date" | "datetime";
  properties?: string;
  status?: ArchiveStatus;
  page: number;
  size: number;
  sort?: string;
  order?: SortDirection;
  q: string;
  customerId?: string;
  type?: string;
  metricCode?: string;
  paymentStatus?: string;
  minAmount?: string;
  maxAmount?: string;
  mode?: "test" | "live";
  devState: DevState;
};

export type MonthlyListQuery = ListQuery & { month: string };

export type ListQueryOverrides = Partial<
  Pick<ListQuery, "month" | "from" | "to" | "rangeMode" | "properties" | "status" | "page" | "size" | "sort" | "order" | "q" | "customerId" | "type" | "metricCode" | "paymentStatus" | "minAmount" | "maxAmount" | "mode">
>;

export type ListQueryOptions = {
  hasMonth?: boolean;
};

export type PaginationFields = {
  total?: number;
  page?: number;
  size?: number;
  total_unfiltered?: number;
};

type SearchParams = Record<string, string | string[] | undefined>;

function readFirst(raw: string | string[] | undefined): string | undefined {
  const value = Array.isArray(raw) ? raw[0] : raw;
  return value === undefined || value.trim() === "" ? undefined : value;
}

function readPage(raw: string | string[] | undefined): number {
  const value = readFirst(raw);
  const parsed = Number(value);
  return value !== undefined && Number.isInteger(parsed) && parsed >= 0 ? parsed : 0;
}

function readSize(raw: string | string[] | undefined): number {
  const parsed = Number(readFirst(raw));
  return PAGE_SIZE_OPTIONS.some((option) => option === parsed) ? parsed : DEFAULT_PAGE_SIZE;
}

function readOrder(raw: string | string[] | undefined): SortDirection | undefined {
  const value = readFirst(raw);
  return value === "asc" || value === "desc" ? value : undefined;
}

export function readListQuery(
  searchParams: SearchParams,
  capabilities: ListCapabilities,
  options: ListQueryOptions & { hasMonth: true },
): MonthlyListQuery;
export function readListQuery(
  searchParams: SearchParams,
  capabilities: ListCapabilities,
  options?: ListQueryOptions,
): ListQuery;
export function readListQuery(
  searchParams: SearchParams,
  capabilities: ListCapabilities,
  options: ListQueryOptions = {},
): ListQuery {
  const sort = capabilities.sort ? readFirst(searchParams.sort) : undefined;
  return {
    
    organizationId: readFirst(searchParams.organization_id),
    archiveParameter: capabilities.archiveParameter,
    preservedParameters: capabilities.preservedParameters ? Object.fromEntries(capabilities.preservedParameters.flatMap(key => { const value = readFirst(searchParams[key]); return value ? [[key, value]] : []; })) : undefined,
    month: options.hasMonth
      ? capabilities.month_all
        ? readMonthOrAll(searchParams.month)
        : readMonth(searchParams.month)
      : undefined,
    page: capabilities.page ? readPage(searchParams.page) : 0,
    from: capabilities.range ? readFirst(searchParams.from) : undefined,
    to: capabilities.range ? readFirst(searchParams.to) : undefined,
    properties: capabilities.range ? readFirst(searchParams.properties) : undefined,
    status: capabilities.archive ? readArchiveStatus(readFirst(searchParams.status)) : undefined,
    rangeMode: capabilities.range && readFirst(searchParams.range_mode) === "date" ? "date" : "datetime",
    size: capabilities.size ? readSize(searchParams.size) : DEFAULT_PAGE_SIZE,
    sort,
    order: sort ? readOrder(searchParams.order) : undefined,
    q: readFirst(searchParams.q) ?? "",
    customerId: capabilities.customer_id ? readFirst(searchParams.customer_id) : undefined,
    type: capabilities.type ? readFirst(searchParams.type) : undefined,
    metricCode: capabilities.metric ? readFirst(searchParams.billable_metric_code) : undefined,
    paymentStatus: capabilities.paymentStatus ? readFirst(searchParams.payment_status) : undefined,
    minAmount: capabilities.amount ? readFirst(searchParams.min_amount) : undefined,
    maxAmount: capabilities.amount ? readFirst(searchParams.max_amount) : undefined,
    mode: capabilities.mode && readFirst(searchParams.mode) === "live" ? "live" : capabilities.mode && readFirst(searchParams.mode) === "test" ? "test" : undefined,
    devState: readDevState(searchParams.state),
  };
}

export function toServerParams(
  query: ListQuery,
  capabilities: ListCapabilities,
): Record<string, string> {
  const params: Record<string, string> = {};
  if (capabilities.range && (query.from || query.to)) {
    if (query.from) params.from = rangeEndpoint(query.from, false, query.rangeMode);
    if (query.to) params.to = rangeEndpoint(query.to, true, query.rangeMode);
  } else if (query.month && query.month !== ALL_MONTHS) params.month = query.month;
  if (capabilities.page && (query.page > 0 || capabilities.pageBase === 1)) params.page = String(query.page + (capabilities.pageBase ?? 0));
  if (capabilities.size && (query.size !== DEFAULT_PAGE_SIZE || capabilities.pageBase === 1)) params.size = String(query.size);
  if (capabilities.sort && query.sort) {
    params.sort = query.sort;
    if (query.order) params.order = query.order;
  }
  if (capabilities.range && query.properties) params.properties = query.properties;
  if (capabilities.archive && query.status) params.status = query.status;
  if (capabilities.q && query.q.trim() !== "") params.q = query.q.trim();
  if (capabilities.customer_id && query.customerId) params.customer_id = query.customerId;
  if (capabilities.type && query.type) params.type = query.type;
  if (capabilities.metric && query.metricCode) params.billable_metric_code = query.metricCode;
  if (capabilities.paymentStatus && query.paymentStatus) params.status = query.paymentStatus;
  if (capabilities.amount && query.minAmount) params.min_amount = query.minAmount;
  if (capabilities.amount && query.maxAmount) params.max_amount = query.maxAmount;
  if (capabilities.mode && query.mode) params.mode = query.mode;
  return params;
}

export function applyListOverrides(query: ListQuery, overrides: ListQueryOverrides = {}): ListQuery {
  const isConditionChanged = Object.keys(overrides).some((key) => key !== "page");
  const next = { ...query, ...overrides };
  return {
    ...next,
    page: isConditionChanged ? 0 : Math.max(next.page ?? 0, 0),
    size: next.size ?? DEFAULT_PAGE_SIZE,
    q: next.q ?? "",
  };
}

export function buildListHref(
  pathname: string,
  query: ListQuery,
  overrides: ListQueryOverrides = {},
): string {
  const next = applyListOverrides(query, overrides);

  const params = new URLSearchParams(next.preservedParameters);
  
  if (next.organizationId) params.set("organization_id", next.organizationId);
  if (next.from || next.to) {
    if (next.from) params.set("from", next.from);
    if (next.to) params.set("to", next.to);
    if (next.rangeMode === "date") params.set("range_mode", "date");
  } else if (next.month) params.set("month", next.month);
  if (next.properties) params.set("properties", next.properties);
  if (next.status && (next.status !== "active" || next.archiveParameter)) params.set(next.archiveParameter ?? "status", next.status);
  if (next.customerId) params.set("customer_id", next.customerId);
  if (next.type) params.set("type", next.type);
  if (next.metricCode) params.set("billable_metric_code", next.metricCode);
  if (next.paymentStatus) params.set("payment_status", next.paymentStatus);
  if (next.minAmount) params.set("min_amount", next.minAmount);
  if (next.maxAmount) params.set("max_amount", next.maxAmount);
  if (next.mode) params.set("mode", next.mode);
  const searchText = next.q.trim();
  if (searchText) params.set("q", searchText);
  if (next.sort) {
    params.set("sort", next.sort);
    if (next.order) params.set("order", next.order);
  }
  if (next.size !== DEFAULT_PAGE_SIZE) params.set("size", String(next.size));
  if (next.page > 0) params.set("page", String(next.page));
  if (isDevStateEnabled && next.devState !== "normal") params.set("state", next.devState);

  const search = params.toString();
  return search ? `${pathname}?${search}` : pathname;
}

export function rangeEndpoint(raw: string, isEnd: boolean, mode?: "date" | "datetime") {
  if (/^\d{4}-\d{2}-\d{2}$/.test(raw)) {
    const date = new Date(`${raw}T00:00:00Z`);
    if (isEnd && mode === "date") date.setUTCDate(date.getUTCDate() + 1);
    return Number.isFinite(date.getTime()) ? `${date.toISOString().slice(0, 10)}T00:00:00+09:00` : raw;
  }
  return /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?$/.test(raw) ? `${raw.length === 16 ? `${raw}:00` : raw}+09:00` : raw;
}

export function readArchiveStatus(value?: string): ArchiveStatus {
  return value === "archived" || value === "all" ? value : "active";
}
