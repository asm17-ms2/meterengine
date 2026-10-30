import { MeterEngineApiError, MeterEngineClient } from "@meterengine/sdk";
import type { InvoiceLineResponse, InvoicePaymentResponse, ListBillableMetricPricesResponse, ListBillableMetricUsagesResponse } from "@meterengine/sdk";
import { viewer } from "./config.ts";
import type { LumenConfig, ServiceAccount } from "./config.ts";
import { EVENT_TYPE, METRIC_CODES, MODEL_CATALOG, PROVIDER_INFO } from "./contracts.ts";
import type { BillingLine, GenerationView, LlmModel, SessionResponse, WorkspaceSnapshot } from "./contracts.ts";
import { LocalDemoProvider } from "./provider.ts";
import type { GenerationProvider } from "./provider.ts";
import { ServiceError } from "./service-error.ts";
import { LumenStore } from "./store.ts";

export type MeteringClient = Pick<MeterEngineClient, "ingestEvent" | "aggregateBillableMetricUsages" | "previewDraftInvoice" | "listInvoices" | "listInvoicePayments" | "getPaymentMethod" | "listBillableMetricPrices">;
type RegistrationClient = Pick<MeterEngineClient, "createPaymentMethodRegistration">;
export type ServiceDependencies = {
  config: LumenConfig;
  store: LumenStore;
  client?: MeteringClient;
  registrationClient?: RegistrationClient;
  provider?: GenerationProvider;
  now?: () => number;
};

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function isValidRequestId(value: unknown): value is string {
  return typeof value === "string" && UUID_PATTERN.test(value);
}

export function currentMonth(now = Date.now()): string {
  return new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit" }).format(new Date(now));
}

export function monthRange(month: string): { start: string; end: string } {
  if (!/^(?!0000)\d{4}-(0[1-9]|1[0-2])$/.test(month)) throw new ServiceError(400, "invalid_month", "조회할 월을 다시 선택하세요.");
  const [year, number] = month.split("-").map(Number);
  const startDate = new Date(0);
  startDate.setUTCFullYear(year, number - 1, 1);
  startDate.setUTCHours(-9, 0, 0, 0);
  const endDate = new Date(0);
  endDate.setUTCFullYear(year, number, 1);
  endDate.setUTCHours(-9, 0, 0, 0);
  return { start: startDate.toISOString(), end: endDate.toISOString() };
}

function metricName(code: string | null): string {
  if (code === METRIC_CODES.input) return "입력 토큰";
  if (code === METRIC_CODES.output) return "출력 토큰";
  if (code === METRIC_CODES.requests) return "요청 사용료";
  return code ? "기타 사용료" : "기본료 및 조정";
}

function billingLine(line: InvoiceLineResponse): BillingLine {
  return {
    label: line.line_type === "base_fee" ? "월 기본료" : line.line_type === "minimum_adjustment" ? "최소 사용료 보정" : metricName(line.billable_metric_code),
    metricCode: line.billable_metric_code,
    modelId: typeof line.dimension_values.model === "string" ? line.dimension_values.model : null,
    quantity: line.quantity,
    quantityNumerator: line.quantity_numerator ?? undefined,
    quantityDenominator: line.quantity_denominator ?? undefined,
    unitPrice: line.unit_price,
    amount: line.amount,
  };
}

function matchesMetricContract(code: string, usage: ListBillableMetricUsagesResponse | null, prices: ListBillableMetricPricesResponse | null): boolean {
  const metric = usage?.billable_metric_usages.find(item => item.code === code);
  const priceMetric = prices?.billable_metric_prices.find(item => item.billable_metric_code === code);
  if (!metric || !priceMetric || priceMetric.dimension_properties.length !== 1 || priceMetric.dimension_properties[0] !== "model") return false;
  const isRequestCount = code === METRIC_CODES.requests;
  const target = code === METRIC_CODES.input ? "input_tokens" : code === METRIC_CODES.output ? "output_tokens" : null;
  return metric.event_type === EVENT_TYPE && metric.aggregation === (isRequestCount ? "count" : "sum")
    && metric.target_property === target && (metric.unit_size ?? 1) === (isRequestCount ? 1 : 1000)
    && (metric.rounding_mode ?? "none") === "none" && metric.percentile == null;
}

function mergePriceSchedules(common: ListBillableMetricPricesResponse, customer: ListBillableMetricPricesResponse, customerId: string): ListBillableMetricPricesResponse {
  return { billable_metric_prices: common.billable_metric_prices.map(metric => {
    const customerMetric = customer.billable_metric_prices.find(item => item.billable_metric_code === metric.billable_metric_code);
    const hasMatchingDimensions = customerMetric && metric.dimension_properties.length === customerMetric.dimension_properties.length
      && metric.dimension_properties.every((value, index) => value === customerMetric.dimension_properties[index]);
    return {
      ...metric,
      dimension_properties: hasMatchingDimensions ? metric.dimension_properties : [],
      price_rates: [...metric.price_rates.filter(rate => rate.customer_id === null), ...(customerMetric?.price_rates.filter(rate => rate.customer_id === customerId) ?? [])],
    };
  }) };
}

function modelsWithPrices(data: ListBillableMetricPricesResponse | null, usage: ListBillableMetricUsagesResponse | null, customerId: string, month: string): LlmModel[] {
  function price(metricCode: string, modelId: string): number | null {
    if (!matchesMetricContract(metricCode, usage, data)) return null;
    const metric = data?.billable_metric_prices.find(item => item.billable_metric_code === metricCode);
    const candidates = metric?.price_rates.filter(rate => rate.model === "per_unit" && rate.dimension_values.model === modelId
      && rate.effective_from <= month && (rate.effective_until === null || rate.effective_until > month)
      && (!rate.archived_from || rate.archived_from > month)
      && (rate.customer_id === null || rate.customer_id === customerId)).sort((left, right) => right.effective_from.localeCompare(left.effective_from)) ?? [];
    const rate = candidates.find(item => item.customer_id === customerId) ?? candidates.find(item => item.customer_id === null);
    return rate?.unit_price ?? null;
  }
  return MODEL_CATALOG.map(model => ({ ...model, inputPricePerThousand: price(METRIC_CODES.input, model.id), outputPricePerThousand: price(METRIC_CODES.output, model.id), requestPrice: price(METRIC_CODES.requests, model.id) }));
}

function paidAmount(payment: InvoicePaymentResponse | undefined, invoiceTotal: number): number {
  if (!payment || !["paid", "partially_canceled", "canceled"].includes(payment.status)) return 0;
  return Math.max(0, Math.min(invoiceTotal, payment.amount - payment.canceled_amount));
}

// MeterEngine이 다시 보내도 같은 이유로 거절하는 수집 오류. 자동 재전송을 멈추고 직접 재전송만 받는다.
const PERMANENT_INGEST_REJECTIONS = new Set(["customer_archived", "customer_not_found", "event_too_old", "validation_error"]);

export class LumenService {
  readonly config: LumenConfig;
  readonly store: LumenStore;
  private readonly client: MeteringClient;
  private readonly registrationClient: RegistrationClient | null;
  private readonly provider: GenerationProvider;
  private readonly now: () => number;

  constructor(dependencies: ServiceDependencies) {
    this.config = dependencies.config;
    this.store = dependencies.store;
    this.store.bindSource(JSON.stringify([this.config.meterEngineBaseUrl, this.config.meterEngineEnvironment ?? "api-key", this.config.meterEngineApiKey, this.config.billingApiKey]));
    this.client = dependencies.client ?? new MeterEngineClient({ baseUrl: this.config.meterEngineBaseUrl, apiKey: this.config.meterEngineApiKey, environment: this.config.meterEngineEnvironment, maxRetries: 0, timeoutMs: 2500 });
    this.registrationClient = dependencies.registrationClient ?? (this.config.billingApiKey ? new MeterEngineClient({ baseUrl: this.config.meterEngineBaseUrl, apiKey: this.config.billingApiKey, environment: this.config.meterEngineEnvironment, maxRetries: 0, timeoutMs: 3000 }) : null);
    this.provider = dependencies.provider ?? new LocalDemoProvider();
    this.now = dependencies.now ?? Date.now;
  }

  accountForSession(token?: string): ServiceAccount | null {
    const id = this.store.sessionAccount(token, this.now());
    return this.config.accounts.find(account => account.id === id) ?? null;
  }

  requireAccount(token?: string): ServiceAccount {
    const account = this.accountForSession(token);
    if (!account) throw new ServiceError(401, "session_required", "체험 계정으로 로그인하세요.");
    return account;
  }

  session(token?: string): SessionResponse {
    const account = this.accountForSession(token);
    return { configured: this.config.isConfigured, user: account ? viewer(account) : null, accounts: this.config.accounts.map(viewer), provider: PROVIDER_INFO };
  }

  login(accountId: unknown): string {
    if (typeof accountId !== "string" || !this.config.accounts.some(account => account.id === accountId)) throw new ServiceError(400, "invalid_account", "체험 계정을 선택하세요.");
    return this.store.createSession(accountId, this.now());
  }

  logout(token?: string): void { this.store.revokeSession(token); }

  getRequest(account: ServiceAccount, id: string): GenerationView {
    const generation = this.store.getGeneration(account.id, id.toLowerCase());
    if (!generation) throw new ServiceError(404, "request_not_found", "이 계정의 요청을 찾을 수 없습니다.");
    return generation;
  }

  listRequests(account: ServiceAccount): GenerationView[] { return this.store.listGenerations(account.id); }

  async generate(account: ServiceAccount, input: unknown): Promise<GenerationView> {
    if (typeof input !== "object" || input === null || Array.isArray(input)) throw new ServiceError(400, "invalid_request", "요청 내용을 확인하세요.");
    const body = input as Record<string, unknown>;
    if (!isValidRequestId(body.requestId)) throw new ServiceError(400, "invalid_request_id", "요청 번호를 새로 만든 뒤 다시 시도하세요.");
    const requestId = body.requestId.toLowerCase();
    const model = MODEL_CATALOG.find(item => item.id === body.modelId);
    if (!model) throw new ServiceError(400, "invalid_model", "제공하는 모델을 선택하세요.");
    if (typeof body.prompt !== "string" || !body.prompt.trim() || Array.from(body.prompt).length > 4000 || body.prompt.includes("\0")) throw new ServiceError(400, "invalid_prompt", "프롬프트를 1~4,000자 이내로 입력하세요.");
    const claim = this.store.reserveGeneration(account.id, requestId, model.id, model.name, body.prompt, this.now());
    if (claim.leaseToken) {
      try {
        const output = await this.provider.generate(body.prompt, model.id, { requestId, accountId: account.id });
        if (!Number.isSafeInteger(output.inputTokens) || output.inputTokens < 0 || !Number.isSafeInteger(output.outputTokens) || output.outputTokens < 0
          || !Number.isSafeInteger(output.inputTokens + output.outputTokens) || typeof output.output !== "string") throw new Error("Invalid provider response");
        this.store.completeGeneration(account.id, requestId, claim.leaseToken, output.output, output.inputTokens, output.outputTokens, {
          transaction_id: `lumen:${account.id}:${requestId}`,
          customer_id: account.customerId,
          type: EVENT_TYPE,
          timestamp: claim.generation.createdAt,
          properties: { model: model.id, input_tokens: output.inputTokens, output_tokens: output.outputTokens, total_tokens: output.inputTokens + output.outputTokens, source: "lumen-local-demo" },
        });
      } catch {
        this.store.failGeneration(account.id, requestId, claim.leaseToken);
        throw new ServiceError(503, "generation_failed", "응답을 만들지 못했습니다. 같은 요청으로 다시 시도할 수 있습니다.", true);
      }
    }
    await this.flushMetering({ accountId: account.id, generationId: requestId, limit: 1 });
    return this.getRequest(account, requestId);
  }

  async flushMetering(options: { accountId?: string; generationId?: string; limit?: number } = {}): Promise<void> {
    for (let index = 0; index < (options.limit ?? 10); index++) {
      const claim = this.store.claimOutbox(this.now(), options);
      if (!claim) break;
      try {
        const result = await this.client.ingestEvent(claim.event);
        if (result.transaction_id !== claim.transactionId || typeof result.duplicate !== "boolean") throw new Error("Invalid ingestion acknowledgement");
        this.store.finishOutbox(claim, this.now(), true);
      } catch (error) {
        const isRejected = error instanceof MeterEngineApiError && PERMANENT_INGEST_REJECTIONS.has(error.code);
        this.store.finishOutbox(claim, this.now(), false, isRejected ? Number.MAX_SAFE_INTEGER : undefined);
      }
    }
  }

  async retryMetering(account: ServiceAccount, input: unknown): Promise<{ delivered: number; pending: number; failed: number }> {
    if (typeof input !== "object" || input === null || Array.isArray(input)) throw new ServiceError(400, "invalid_request", "재전송할 요청을 확인하세요.");
    const id = (input as Record<string, unknown>).requestId;
    if (id !== undefined && !isValidRequestId(id)) throw new ServiceError(404, "request_not_found", "이 계정의 요청을 찾을 수 없습니다.");
    const requestId = typeof id === "string" ? id.toLowerCase() : undefined;
    if (requestId && !this.store.getGeneration(account.id, requestId)) throw new ServiceError(404, "request_not_found", "이 계정의 요청을 찾을 수 없습니다.");
    this.store.makeOutboxDue(account.id, requestId);
    await this.flushMetering({ accountId: account.id, generationId: requestId });
    return this.store.outboxCounts(account.id);
  }

  async workspace(account: ServiceAccount, month: string): Promise<WorkspaceSnapshot> {
    const range = monthRange(month);
    const [usage, draft, invoices, payments, method, commonPrices, customerPrices] = await Promise.allSettled([
      this.client.aggregateBillableMetricUsages({ month }), this.client.previewDraftInvoice({ month }),
      this.client.listInvoices({ month, customer_id: account.customerId }), this.client.listInvoicePayments({ month, customer_id: account.customerId }),
      this.client.getPaymentMethod(account.customerId), this.client.listBillableMetricPrices({ month }), this.client.listBillableMetricPrices({ month, customer_id: account.customerId }),
    ]);
    const visibleInvoices = invoices.status === "fulfilled" ? invoices.value.invoices.filter(invoice => invoice.customer_id === account.customerId) : [];
    const invoiceIds = new Set(visibleInvoices.map(invoice => invoice.id));
    const visiblePayments = payments.status === "fulfilled" ? payments.value.invoice_payments.filter(payment => invoiceIds.has(payment.invoice_id)) : [];
    const draftCustomer = draft.status === "fulfilled" ? draft.value.customers.find(customer => customer.customer_id === account.customerId) : null;
    const methodCode = method.status === "rejected" && typeof method.reason === "object" && method.reason !== null && "code" in method.reason ? method.reason.code : null;
    const billingErrors = [draft.status === "rejected" ? "청구 예정액" : "", invoices.status === "rejected" ? "확정 청구" : "", payments.status === "rejected" ? "납부 상태" : "", method.status === "rejected" && methodCode !== "payment_method_not_found" ? "등록 카드" : ""].filter(Boolean);
    const priceData = commonPrices.status === "fulfilled" && customerPrices.status === "fulfilled" ? mergePriceSchedules(commonPrices.value, customerPrices.value, account.customerId) : null;
    const hasPriceQueryFailure = commonPrices.status === "rejected" || customerPrices.status === "rejected";
    const usageData = usage.status === "fulfilled" ? usage.value : null;
    const hasCompatibleMetrics = Object.values(METRIC_CODES).every(code => matchesMetricContract(code, usageData, priceData));
    const models = modelsWithPrices(priceData, usageData, account.customerId, month);
    const hasMissingPrice = !hasCompatibleMetrics || models.some(model => model.inputPricePerThousand === null || model.outputPricePerThousand === null || model.requestPrice === null);
    const isPriceReadFailure = usage.status === "rejected" || hasPriceQueryFailure;
    if (payments.status === "fulfilled" && visibleInvoices.some(invoice => !visiblePayments.some(payment => payment.invoice_id === invoice.id))) billingErrors.push("일부 납부 상태");
    if (method.status === "fulfilled" && method.value.customer_id !== account.customerId) billingErrors.push("등록 카드");
    if (hasMissingPrice && isPriceReadFailure) billingErrors.push("적용 요금");
    const billingError = [
      billingErrors.length ? `다음 청구 정보를 확인하지 못했습니다: ${billingErrors.join(", ")}. 연결 상태를 확인한 뒤 새로고침하세요.` : "",
      hasMissingPrice && !isPriceReadFailure ? "이 달의 요금 설정이 예제 서비스의 기준과 맞지 않습니다. 운영자가 요금 설정을 확인해야 합니다." : "",
    ].filter(Boolean).join(" ");
    return {
      user: viewer(account), month, provider: PROVIDER_INFO,
      models,
      stats: this.store.stats(account.id, range.start, range.end),
      requests: this.store.listGenerations(account.id, range.start, range.end),
      usage: {
        state: usage.status === "fulfilled" && hasCompatibleMetrics ? "ready" : "unavailable", updatedAt: new Date(this.now()).toISOString(),
        metrics: usage.status === "fulfilled" ? usage.value.billable_metric_usages.filter(metric => Object.values(METRIC_CODES).includes(metric.code as typeof METRIC_CODES[keyof typeof METRIC_CODES])).flatMap(metric => metric.customers.filter(customer => customer.customer_id === account.customerId).map(customer => ({
          code: metric.code, name: metricName(metric.code), quantity: customer.quantity, rawQuantity: customer.raw_quantity ?? customer.quantity, unitSize: metric.unit_size ?? 1,
          modelQuantities: customer.dimension_usages.flatMap(dimension => typeof dimension.dimension_values.model === "string" ? [{ modelId: dimension.dimension_values.model, quantity: dimension.quantity, rawQuantity: dimension.raw_quantity ?? dimension.quantity }] : []),
        }))) : [],
        ...(usage.status === "rejected" ? { error: "사용량을 확인하지 못했습니다. 연결이 복구된 뒤 새로고침하세요." }
          : hasPriceQueryFailure ? { error: "공통 요금 또는 고객별 요금을 확인하지 못해 적용 가격을 확정할 수 없습니다. 연결이 복구된 뒤 새로고침하세요." }
          : !hasCompatibleMetrics ? { error: "집계 또는 요금 설정이 예제 서비스의 기준과 다릅니다. 운영자가 연결 설정을 확인해야 합니다." } : {}),
      },
      billing: {
        state: billingError ? "unavailable" : "ready",
        draft: draftCustomer ? { supplyAmount: draftCustomer.amount, taxAmount: Math.floor(draftCustomer.amount * 0.1), totalAmount: draftCustomer.amount + Math.floor(draftCustomer.amount * 0.1), lines: draftCustomer.lines.map(billingLine) } : null,
        invoices: visibleInvoices.map(invoice => {
          const payment = visiblePayments.find(item => item.invoice_id === invoice.id);
          const paid = payment ? paidAmount(payment, invoice.total_amount) : null;
          return { id: invoice.id, month: invoice.month, finalizedAt: invoice.finalized_at, supplyAmount: invoice.supply_amount, taxAmount: invoice.tax_amount, totalAmount: invoice.total_amount, lines: invoice.lines.map(billingLine), status: payment?.status ?? "unavailable", paidAmount: paid, canceledAmount: payment?.canceled_amount ?? null, amountDue: payment ? Math.max(0, invoice.total_amount - paid! - payment.canceled_amount) : null };
        }),
        paymentMethod: method.status === "fulfilled" && method.value.customer_id === account.customerId ? { cardNumber: method.value.card_number, authenticatedAt: method.value.authenticated_at, archived: Boolean(method.value.archived_at) } : null,
        canRegisterPaymentMethod: this.registrationClient !== null,
        ...(billingError ? { error: billingError } : {}),
      },
    };
  }

  async registerPaymentMethod(account: ServiceAccount): Promise<{ url: string; expiresAt: string; mode: "test" | "live" }> {
    if (!this.registrationClient) throw new ServiceError(503, "billing_unavailable", "카드 등록 연결이 준비되지 않았습니다. 서비스 운영자에게 문의하세요.");
    try {
      const registration = await this.registrationClient.createPaymentMethodRegistration(account.customerId);
      if (!registration.token || registration.customer_id !== account.customerId || registration.mode !== (this.config.meterEngineEnvironment ?? "test")) throw new Error("Invalid registration");
      return { url: new URL(`/payment-method-registrations/${encodeURIComponent(registration.token)}`, this.config.meterEngineConsoleUrl).href, expiresAt: registration.expires_at, mode: registration.mode };
    } catch {
      throw new ServiceError(503, "billing_unavailable", "카드 등록을 시작하지 못했습니다. 현재 환경의 결제 설정을 확인한 뒤 다시 시도하세요.", true);
    }
  }
}
