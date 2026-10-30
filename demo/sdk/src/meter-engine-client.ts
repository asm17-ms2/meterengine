import type { WorkspaceEnvironment, SearchParams, SearchPage, UsageSearchSummary, SearchResource, BillableMetricResponse, CustomerSearchItem, CustomerUsageSearchItem, InvoiceSearchItem, EventIssueReason, EventIssueSearchResponse, EventRejectionSearchResponse } from "./types.ts";
import { MeterEngineApiError, MeterEngineBatchError, MeterEngineConnectionError, MeterEngineTimeoutError } from "./errors.ts";
import { isRetryable, parseRetryAfter, retryDelayMs } from "./retry.ts";
import type {
  ArchiveStatus,
  ErrorResponse,
  IngestEventBatchRequest,
  IngestEventBatchResponse,
  IngestEventBatchResult,
  IngestEventInput,
  IngestEventRequest,
  IngestEventResponse,
  ListCustomersResponse,
  ListEventsParams,
  EventExportParams,
  CsvResource,
  ListEventsResponse,
  ListEventTypesParams,
  ListEventTypesResponse,
  EventResponse,
  CreateCustomerRequest,
  CustomerResponse,
  ListBillableMetricUsagesResponse,
  ListInvoicesResponse,
  InvoiceResponse,
  CreateInvoiceViewLinkRequest,
  InvoiceViewLinkResponse,
  InvoiceViewLinkSecretResponse,
  ListInvoiceViewLinksResponse,
  DraftInvoiceResponse,
  ListInvoicePaymentsResponse,
  ListPaymentMethodsResponse,
  PaymentMethodRegistrationResponse,
  ListWebhookEndpointsResponse,
  ListWebhookDeliveriesResponse,
  UnbilledEventsResponse,
  PaymentMethodResponse,
  OrganizationResponse,
  ListBillableMetricPricesResponse,
  ListPricePlansResponse,
} from "./types.ts";

const DEFAULT_TIMEOUT_MS = 10000;
const DEFAULT_MAX_RETRIES = 3;
const BATCH_SIZE = 100;
const LIST_EVENTS_PARAM_KEYS = ["page", "size", "customer_id", "month", "type", "from", "to", "q", "properties", "sort", "order"] as const;
const EVENT_EXPORT_PARAM_KEYS = [...LIST_EVENTS_PARAM_KEYS, "include_properties"] as const;
const LIST_EVENT_TYPES_PARAM_KEYS = ["customer_id", "month", "from", "to", "q", "properties"] as const;

export interface MeterEngineClientOptions {
  baseUrl: string;
  apiKey: string;
  environment?: WorkspaceEnvironment;
  organizationId?: string;
  timeoutMs?: number;
  maxRetries?: number;
  fetch?: typeof fetch;
  sleep?: (ms: number) => Promise<void>;
  random?: () => number;
}

export interface RequestOptions {
  signal?: AbortSignal;
}

type HttpMethod = "GET" | "POST" | "PATCH";

function defaultSleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function withTransactionId(request: IngestEventInput): IngestEventRequest {
  const transactionId = request.transaction_id === undefined ? crypto.randomUUID() : request.transaction_id;
  return { ...request, transaction_id: transactionId };
}

function isErrorResponse(body: unknown): body is ErrorResponse {
  return (
    typeof body === "object" &&
    body !== null &&
    "code" in body &&
    typeof body.code === "string" &&
    "message" in body &&
    typeof body.message === "string"
  );
}

function toErrorResponse(text: string, status: number): ErrorResponse {
  const fallback = { code: "http_error", message: `HTTP ${status}` };
  try {
    const body: unknown = JSON.parse(text);
    return isErrorResponse(body) ? body : fallback;
  } catch {
    return fallback;
  }
}

export class MeterEngineClient {
  private readonly baseUrl: string;
  private readonly apiKey: string;
  private readonly environment?: WorkspaceEnvironment;
  private readonly organizationId?: string;
  private readonly timeoutMs: number;
  private readonly maxRetries: number;
  private readonly fetchImpl: typeof fetch;
  private readonly sleep: (ms: number) => Promise<void>;
  private readonly random: () => number;

  constructor(options: MeterEngineClientOptions) {
    if (["\r", "\n", "\0"].some((char) => options.apiKey.includes(char))) {
      throw new Error("apiKey에 줄바꿈이나 NUL 문자가 있어 Authorization 헤더에 쓸 수 없습니다");
    }
    this.baseUrl = options.baseUrl.replace(/\/+$/, "");
    this.apiKey = options.apiKey;
    if (options.environment !== undefined && !["test", "live"].includes(options.environment)) throw new Error("environment는 test 또는 live여야 합니다");
    if (options.organizationId && /[\r\n\0]/.test(options.organizationId)) throw new Error("organizationId 형식이 올바르지 않습니다");
    this.environment = options.environment;
    this.organizationId = options.organizationId;
    this.timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;
    this.maxRetries = options.maxRetries ?? DEFAULT_MAX_RETRIES;
    this.fetchImpl = options.fetch ?? ((input, init) => fetch(input, init));
    this.sleep = options.sleep ?? defaultSleep;
    this.random = options.random ?? Math.random;
  }

  ingestEvent(request: IngestEventInput, options: RequestOptions = {}): Promise<IngestEventResponse> {
    return this.request<IngestEventResponse>("POST", "/v1/events", withTransactionId(request), options.signal);
  }

  async ingestEventBatch(requests: IngestEventInput[], options: RequestOptions = {}): Promise<IngestEventBatchResponse> {
    const events = requests.map(withTransactionId);
    const results: IngestEventBatchResult[] = [];
    for (let start = 0; start < events.length; start += BATCH_SIZE) {
      const body: IngestEventBatchRequest = { events: events.slice(start, start + BATCH_SIZE) };
      try {
        const response = await this.request<IngestEventBatchResponse>("POST", "/v1/event-batches", body, options.signal);
        results.push(...response.results.map((result) => ({ ...result, index: result.index + start })));
      } catch (error) {
        throw new MeterEngineBatchError(results, error instanceof Error ? error : new Error(String(error)), {
          startIndex: start,
          size: body.events.length,
        });
      }
    }
    return { results };
  }

  listEvents(params: ListEventsParams = {}): Promise<ListEventsResponse> {
    return this.request("GET", eventQueryPath("/v1/events", params, LIST_EVENTS_PARAM_KEYS));
  }

  listEventTypes(params: ListEventTypesParams = {}): Promise<ListEventTypesResponse> {
    return this.request("GET", eventQueryPath("/v1/events/types", params, LIST_EVENT_TYPES_PARAM_KEYS));
  }

  getEvent(transactionId: string): Promise<EventResponse> {
    return this.request("GET", queryPath("/v1/events/lookup", { transaction_id: transactionId }));
  }

  listCustomers(params: { external_id?: string; status?: ArchiveStatus } = {}): Promise<ListCustomersResponse> {
    return this.request<ListCustomersResponse>("GET", queryPath("/v1/customers", params));
  }

  getCustomer(id: string): Promise<CustomerResponse> {
    return this.request("GET", `/v1/customers/${encodeURIComponent(id)}`);
  }

  createCustomer(request: CreateCustomerRequest): Promise<CustomerResponse> {
    return this.request("POST", "/v1/customers", request, undefined, request.external_id !== undefined);
  }

  aggregateBillableMetricUsages(params: { month?: string } = {}): Promise<ListBillableMetricUsagesResponse> {
    return this.request("GET", queryPath("/v1/usage", params));
  }

  listInvoices(params: { month?: string; customer_id?: string } = {}): Promise<ListInvoicesResponse> {
    return this.request("GET", queryPath("/v1/invoices", params));
  }

  getInvoice(id: string): Promise<InvoiceResponse> {
    return this.request("GET", `/v1/invoices/${encodeURIComponent(id)}`);
  }

  createInvoiceViewLink(id: string, request: CreateInvoiceViewLinkRequest = {}): Promise<InvoiceViewLinkSecretResponse> {
    return this.request("POST", `/v1/invoices/${encodeURIComponent(id)}/view-links`, request, undefined, false);
  }

  listInvoiceViewLinks(id: string): Promise<ListInvoiceViewLinksResponse> {
    return this.request("GET", `/v1/invoices/${encodeURIComponent(id)}/view-links`);
  }

  revokeInvoiceViewLink(id: string, linkId: string): Promise<InvoiceViewLinkResponse> {
    return this.request("POST", `/v1/invoices/${encodeURIComponent(id)}/view-links/${encodeURIComponent(linkId)}/revoke`);
  }

  previewDraftInvoice(params: { month?: string } = {}): Promise<DraftInvoiceResponse> {
    return this.request("GET", queryPath("/v1/invoices/draft", params));
  }

  listInvoicePayments(params: { month?: string; customer_id?: string } = {}): Promise<ListInvoicePaymentsResponse> {
    return this.request("GET", queryPath("/v1/invoice-payments", params));
  }

  getPaymentMethod(id: string): Promise<PaymentMethodResponse> {
    return this.request("GET", `/v1/customers/${encodeURIComponent(id)}/payment-method`);
  }

  listPaymentMethods(params: { status?: ArchiveStatus } = {}): Promise<ListPaymentMethodsResponse> {
    return this.request("GET", queryPath("/v1/payment-methods", params));
  }

  getOrganization(): Promise<OrganizationResponse> {
    return this.request("GET", "/v1/organization");
  }

  listBillableMetricPrices(params: { month?: string; customer_id?: string } = {}): Promise<ListBillableMetricPricesResponse> {
    return this.request("GET", queryPath("/v1/billable-metric-prices", params));
  }

  listPricePlans(params: { month?: string; customer_id?: string; status?: ArchiveStatus } = {}): Promise<ListPricePlansResponse> {
    return this.request("GET", queryPath("/v1/price-plans", params));
  }

  createPaymentMethodRegistration(id: string): Promise<PaymentMethodRegistrationResponse> {
    return this.request("POST", `/v1/customers/${encodeURIComponent(id)}/payment-method-registrations`, undefined, undefined, false);
  }

  listWebhookEndpoints(params: { status?: ArchiveStatus } = {}): Promise<ListWebhookEndpointsResponse> {
    return this.request("GET", queryPath("/v1/webhook-endpoints", params));
  }

  listWebhookDeliveries(id: string, params: { page?: number; size?: number; status?: string } = {}): Promise<ListWebhookDeliveriesResponse> {
    return this.request("GET", queryPath(`/v1/webhook-endpoints/${encodeURIComponent(id)}/deliveries`, params));
  }

  getUnbilledEvents(): Promise<UnbilledEventsResponse> {
    return this.request("GET", "/v1/unbilled-events");
  }

  listEnvironments(): Promise<{ environments: { id: string; mode: WorkspaceEnvironment }[] }> {
    return this.request("GET", "/v1/organization/environments");
  }

  searchCustomers(params: SearchParams = {}): Promise<SearchPage<CustomerSearchItem>> {
    return this.request("GET", queryPath("/v1/customers/search", params));
  }

  searchBillableMetrics(params: SearchParams = {}): Promise<SearchPage<BillableMetricResponse>> {
    return this.request("GET", queryPath("/v1/billable-metrics/search", params));
  }

  searchBillableMetricPrices(params: SearchParams = {}): Promise<SearchPage<ListBillableMetricPricesResponse["billable_metric_prices"][number] & { billable_metric_name: string; rate_count: number }>> {
    return this.request("GET", queryPath("/v1/billable-metric-prices/search", params));
  }

  searchUsage(params: SearchParams = {}): Promise<SearchPage<CustomerUsageSearchItem, UsageSearchSummary>> {
    return this.request("GET", queryPath("/v1/usage/search", params));
  }

  searchDraftInvoices(params: SearchParams = {}): Promise<SearchPage<DraftInvoiceResponse["customers"][number]>> {
    return this.request("GET", queryPath("/v1/invoices/draft/search", params));
  }

  searchInvoices(params: SearchParams = {}): Promise<SearchPage<InvoiceSearchItem>> {
    return this.request("GET", queryPath("/v1/invoices/search", params));
  }

  getOptions(resource: "customers" | "billable-metrics", params: { q?: string; status?: ArchiveStatus } = {}): Promise<{ items: { value: string; label: string; archived: boolean }[] }> {
    return this.request("GET", queryPath(`/v1/${resource}/options`, params));
  }

  getAvailableMonths(resource: "events" | "usage" | "invoices" | "billable-metric-prices"): Promise<{ months: string[] }> {
    return this.request("GET", `/v1/${resource}/months`);
  }

  exportCsv(resource: "events", params?: EventExportParams): Promise<string>;
  exportCsv(resource: SearchResource, params?: SearchParams): Promise<string>;
  exportCsv(resource: CsvResource, params?: SearchParams | EventExportParams): Promise<string>;
  exportCsv(resource: CsvResource, params: SearchParams | EventExportParams = {}): Promise<string> {
    const allowed: CsvResource[] = ["customers", "billable-metrics", "billable-metric-prices", "usage", "invoices/draft", "invoices", "events"];
    if (!allowed.includes(resource)) throw new Error("지원하지 않는 내보내기 대상입니다");
    if (resource === "events") return this.request("GET", eventQueryPath("/v1/events/export", params as EventExportParams, EVENT_EXPORT_PARAM_KEYS), undefined, undefined, true, "text");
    return this.request("GET", queryPath(`/v1/${resource}/export`, params as SearchParams), undefined, undefined, true, "text");
  }

  searchEventIssues(params: { month: string; customer_id?: string; reason?: EventIssueReason; q?: string; page?: number; size?: number }): Promise<EventIssueSearchResponse> {
    return this.request("GET", queryPath("/v1/event-issues/search", params));
  }

  searchEventRejections(params: { month: string; customer_id?: string; error_code?: string; q?: string; page?: number; size?: number }): Promise<EventRejectionSearchResponse> {
    return this.request("GET", queryPath("/v1/event-rejections/search", params));
  }

  private async request<T>(method: HttpMethod, path: string, body?: unknown, signal?: AbortSignal, canRetry = true, responseType: "json" | "text" = "json"): Promise<T> {
    let attempt = 0;
    for (;;) {
      signal?.throwIfAborted();
      try {
        return await this.send<T>(method, path, body, signal, responseType);
      } catch (error) {
        signal?.throwIfAborted();
        if (!canRetry || !isRetryable(error) || attempt >= this.maxRetries) {
          throw error;
        }
        const retryAfterSeconds = error instanceof MeterEngineApiError ? error.retryAfterSeconds : undefined;
        await this.sleepUnlessAborted(retryDelayMs(attempt, retryAfterSeconds, this.random), signal);
        attempt += 1;
      }
    }
  }

  private sleepUnlessAborted(ms: number, signal: AbortSignal | undefined): Promise<void> {
    if (signal === undefined) {
      return this.sleep(ms);
    }
    return new Promise((resolve, reject) => {
      const onAbort = () => reject(signal.reason);
      signal.addEventListener("abort", onAbort, { once: true });
      this.sleep(ms).then(resolve, reject).finally(() => signal.removeEventListener("abort", onAbort));
    });
  }

  private async send<T>(method: HttpMethod, path: string, body: unknown, signal: AbortSignal | undefined, responseType: "json" | "text"): Promise<T> {
    const timeoutSignal = AbortSignal.timeout(this.timeoutMs);
    const headers: Record<string, string> = {
      Authorization: `Bearer ${this.apiKey}`,
      Accept: responseType === "text" ? "text/csv" : "application/json",
      ...(this.environment ? { "X-MeterEngine-Environment": this.environment } : {}),
      ...(this.organizationId ? { "X-MeterEngine-Organization-Id": this.organizationId } : {}),
    };
    if (body !== undefined) {
      headers["Content-Type"] = "application/json";
    }
    let response: Response;
    let text: string;
    try {
      response = await this.fetchImpl(`${this.baseUrl}${path}`, {
        method,
        redirect: "error",
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: signal === undefined ? timeoutSignal : AbortSignal.any([signal, timeoutSignal]),
      });
      text = await response.text();
    } catch (error) {
      if (signal?.aborted) {
        throw error;
      }
      if (timeoutSignal.aborted) {
        throw new MeterEngineTimeoutError(this.timeoutMs);
      }
      throw new MeterEngineConnectionError(error);
    }
    if (!response.ok) {
      const retryAfterSeconds = parseRetryAfter(response.headers.get("retry-after"), Date.now());
      throw new MeterEngineApiError(response.status, toErrorResponse(text, response.status), retryAfterSeconds);
    }
    return responseType === "text" ? text as T : JSON.parse(text) as T;
  }
}

function eventQueryPath(path: string, params: EventExportParams, keys: ReadonlyArray<keyof EventExportParams>): string {
  const query = new URLSearchParams();
  for (const key of keys) {
    const value = params[key];
    if (value !== undefined) query.set(key, typeof value === "object" ? JSON.stringify(value) : String(value));
  }
  return query.size === 0 ? path : `${path}?${query}`;
}

function queryPath(path: string, params: Record<string, string | number | undefined>): string {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined) query.set(key, String(value));
  }
  return query.size === 0 ? path : `${path}?${query}`;
}
