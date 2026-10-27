
import { MeterEngineApiError, MeterEngineConnectionError, MeterEngineTimeoutError } from "./errors.ts";
import { isRetryable, parseRetryAfter, retryDelayMs } from "./retry.ts";
import type { ArchiveStatus, ErrorResponse, ListBillableMetricUsagesResponse, ListInvoicesResponse, InvoiceResponse, DraftInvoiceResponse, ListInvoicePaymentsResponse, ListPaymentMethodsResponse, PaymentMethodRegistrationResponse, ListWebhookEndpointsResponse, ListWebhookDeliveriesResponse, PaymentMethodResponse, OrganizationResponse, ListBillableMetricPricesResponse, ListPricePlansResponse } from "./types.ts";

const DEFAULT_TIMEOUT_MS = 10000;
const DEFAULT_MAX_RETRIES = 3;

export interface MeterEngineClientOptions {
  baseUrl: string;
  apiKey: string;
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
    if (options.organizationId && /[\r\n\0]/.test(options.organizationId)) throw new Error("organizationId 형식이 올바르지 않습니다");
    this.organizationId = options.organizationId;
    this.timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;
    this.maxRetries = options.maxRetries ?? DEFAULT_MAX_RETRIES;
    this.fetchImpl = options.fetch ?? ((input, init) => fetch(input, init));
    this.sleep = options.sleep ?? defaultSleep;
    this.random = options.random ?? Math.random;
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

  listWebhookEndpoints(): Promise<ListWebhookEndpointsResponse> {
    return this.request("GET", "/v1/webhook-endpoints");
  }

  listWebhookDeliveries(id: string, params: { page?: number; size?: number; status?: string } = {}): Promise<ListWebhookDeliveriesResponse> {
    return this.request("GET", queryPath(`/v1/webhook-endpoints/${encodeURIComponent(id)}/deliveries`, params));
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

function queryPath(path: string, params: Record<string, string | number | undefined>): string {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined) query.set(key, String(value));
  }
  return query.size === 0 ? path : `${path}?${query}`;
}
