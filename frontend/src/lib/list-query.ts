import type { ListCapabilities } from "@/lib/api/capabilities";
import { isDevStateEnabled, readDevState, type DevState } from "@/lib/dev-state";
import { ALL_MONTHS, readMonth, readMonthOrAll } from "@/lib/month";

export const DEFAULT_PAGE_SIZE = 20;
export const MAX_PAGE_SIZE = 100;
export const PAGE_SIZE_OPTIONS = [20, 50, 100] as const;

export type SortDirection = "asc" | "desc";

export type ListQuery = {
  month?: string;
  page: number;
  size: number;
  sort?: string;
  order?: SortDirection;
  q: string;
  customerId?: string;
  type?: string;
  devState: DevState;
};

export type MonthlyListQuery = ListQuery & { month: string };

export type ListQueryOverrides = Partial<
  Pick<ListQuery, "month" | "page" | "size" | "sort" | "order" | "q" | "customerId" | "type">
>;

export type ListQueryOptions = {
  hasMonth?: boolean;
};

export type PaginationFields = {
  total?: number;
  page?: number;
  size?: number;
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
    month: options.hasMonth
      ? capabilities.month_all
        ? readMonthOrAll(searchParams.month)
        : readMonth(searchParams.month)
      : undefined,
    page: capabilities.page ? readPage(searchParams.page) : 0,
    size: capabilities.size ? readSize(searchParams.size) : DEFAULT_PAGE_SIZE,
    sort,
    order: sort ? readOrder(searchParams.order) : undefined,
    q: readFirst(searchParams.q) ?? "",
    customerId: capabilities.customer_id ? readFirst(searchParams.customer_id) : undefined,
    type: capabilities.type ? readFirst(searchParams.type) : undefined,
    devState: readDevState(searchParams.state),
  };
}

export function toServerParams(
  query: ListQuery,
  capabilities: ListCapabilities,
): Record<string, string> {
  const params: Record<string, string> = {};
  if (query.month && query.month !== ALL_MONTHS) params.month = query.month;
  if (capabilities.page && query.page > 0) params.page = String(query.page);
  if (capabilities.size && query.size !== DEFAULT_PAGE_SIZE) params.size = String(query.size);
  if (capabilities.sort && query.sort) {
    params.sort = query.sort;
    if (query.order) params.order = query.order;
  }
  if (capabilities.q && query.q.trim() !== "") params.q = query.q.trim();
  if (capabilities.customer_id && query.customerId) params.customer_id = query.customerId;
  if (capabilities.type && query.type) params.type = query.type;
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

  const params = new URLSearchParams();
  if (next.month) params.set("month", next.month);
  if (next.customerId) params.set("customer_id", next.customerId);
  if (next.type) params.set("type", next.type);
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
