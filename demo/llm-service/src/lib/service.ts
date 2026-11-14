import { createHash } from "node:crypto";
import { MeterEngineApiError, MeterEngineClient } from "@meterengine/sdk";
import type { InvoiceLineResponse, InvoicePaymentResponse, ListBillableMetricPricesResponse, ListBillableMetricUsagesResponse } from "@meterengine/sdk";
import { DIMENSION_PROPERTIES, EVENT_TYPE, METRICS, MODELS, SURFACES, TOKEN_UNIT_SIZE } from "./catalog.ts";
import type { MetricKey, SurfaceId } from "./catalog.ts";
import { viewer } from "./config.ts";
import type { OpenAbConfig, ServiceAccount } from "./config.ts";
import { BATCH_ITEM_MAX_LENGTH, BATCH_MAX_ITEMS, PROMPT_MAX_LENGTH, PROVIDER_INFO, SYSTEM_MAX_LENGTH } from "./contracts.ts";
import type { BatchView, BillingLine, ConversationSummary, ConversationView, GenerationView, LlmModel, MetricPrices, SessionResponse, WorkspaceSnapshot } from "./contracts.ts";
import { usageEvent } from "./metering-event.ts";
import { estimateTokens, LocalDemoProvider, PROMPT_CACHE_MIN_TOKENS, PROMPT_CACHE_TTL_MS } from "./provider.ts";
import type { GenerationOutput, GenerationProvider } from "./provider.ts";
import { ServiceError } from "./service-error.ts";
import { OpenAbStore } from "./store.ts";
import type { GenerationSpec } from "./store.ts";

export type MeteringClient = Pick<MeterEngineClient, "ingestEvent" | "aggregateBillableMetricUsages" | "previewDraftInvoice" | "listInvoices" | "listInvoicePayments" | "getPaymentMethod" | "listBillableMetricPrices">;
type RegistrationClient = Pick<MeterEngineClient, "createPaymentMethodRegistration">;
export type ServiceDependencies = {
  config: OpenAbConfig;
  store: OpenAbStore;
  client?: MeteringClient;
  registrationClient?: RegistrationClient;
  provider?: GenerationProvider;
  now?: () => number;
};

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const SURFACE_IDS = new Set<string>(SURFACES.map(surface => surface.id));

function isValidRequestId(value: unknown): value is string {
  return typeof value === "string" && UUID_PATTERN.test(value);
}

function isSurfaceId(value: unknown): value is SurfaceId {
  return typeof value === "string" && SURFACE_IDS.has(value);
}

function isValidText(value: unknown, maxLength: number, isRequired: boolean): value is string {
  if (typeof value !== "string" || value.includes("\0") || Array.from(value).length > maxLength) return false;
  return !isRequired || value.trim().length > 0;
}

function requestBody(input: unknown, message = "요청 내용을 확인해 주세요."): Record<string, unknown> {
  if (typeof input !== "object" || input === null || Array.isArray(input)) throw new ServiceError(400, "invalid_request", message);
  return input as Record<string, unknown>;
}

function modelFor(modelId: unknown, surface: SurfaceId) {
  const model = MODELS.find(item => item.id === modelId && item.surfaces.includes(surface));
  if (!model) throw new ServiceError(400, "invalid_model", "이 화면에서 쓸 수 있는 모델을 골라 주세요.");
  return model;
}

export function currentMonth(now = Date.now()): string {
  return new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit" }).format(new Date(now));
}

export function monthRange(month: string): { start: string; end: string } {
  if (!/^(?!0000)\d{4}-(0[1-9]|1[0-2])$/.test(month)) throw new ServiceError(400, "invalid_month", "조회할 월을 다시 골라 주세요.");
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
  return METRICS.find(metric => metric.code === code)?.name ?? (code ? "기타 사용료" : "기본료와 조정");
}

function dimensionSurface(values: Record<string, unknown>): SurfaceId | null {
  return isSurfaceId(values.surface) ? values.surface : null;
}

function billingLine(line: InvoiceLineResponse): BillingLine {
  return {
    label: line.line_type === "base_fee" ? "월 기본료" : line.line_type === "minimum_adjustment" ? "최소 사용료 보정" : metricName(line.billable_metric_code),
    metricCode: line.billable_metric_code,
    modelId: typeof line.dimension_values.model === "string" ? line.dimension_values.model : null,
    surface: dimensionSurface(line.dimension_values),
    quantity: line.quantity,
    quantityNumerator: line.quantity_numerator ?? undefined,
    quantityDenominator: line.quantity_denominator ?? undefined,
    unitPrice: line.unit_price,
    amount: line.amount,
  };
}

function hasDimensionContract(properties: string[]): boolean {
  return properties.length === DIMENSION_PROPERTIES.length && DIMENSION_PROPERTIES.every(property => properties.includes(property));
}

// 앱이 보여 주는 단가와 사용량은 catalog.ts의 미터 정의와 같을 때만 믿는다.
export function matchesMetricContract(code: string, usage: ListBillableMetricUsagesResponse | null, prices: ListBillableMetricPricesResponse | null): boolean {
  const definition = METRICS.find(metric => metric.code === code);
  const metric = usage?.billable_metric_usages.find(item => item.code === code);
  const priceMetric = prices?.billable_metric_prices.find(item => item.billable_metric_code === code);
  if (!definition || !metric || !priceMetric || !hasDimensionContract(priceMetric.dimension_properties)) return false;
  return metric.event_type === EVENT_TYPE && metric.aggregation === "sum" && metric.target_property === definition.property
    && (metric.unit_size ?? 1) === TOKEN_UNIT_SIZE && (metric.rounding_mode ?? "none") === "none" && metric.percentile == null;
}

function mergePriceSchedules(common: ListBillableMetricPricesResponse, customer: ListBillableMetricPricesResponse, customerId: string): ListBillableMetricPricesResponse {
  return { billable_metric_prices: common.billable_metric_prices.map(metric => {
    const customerMetric = customer.billable_metric_prices.find(item => item.billable_metric_code === metric.billable_metric_code);
    const hasMatchingDimensions = customerMetric && metric.dimension_properties.length === customerMetric.dimension_properties.length
      && metric.dimension_properties.every(value => customerMetric.dimension_properties.includes(value));
    return {
      ...metric,
      dimension_properties: hasMatchingDimensions ? metric.dimension_properties : [],
      price_rates: [...metric.price_rates.filter(rate => rate.customer_id === null), ...(customerMetric?.price_rates.filter(rate => rate.customer_id === customerId) ?? [])],
    };
  }) };
}

// 임베딩 모델은 입력 토큰만 생기므로 입력 단가만 있으면 된다.
export function isRequiredPrice(kind: LlmModel["kind"], key: MetricKey): boolean {
  return kind === "text" || key === "input";
}

function modelsWithPrices(data: ListBillableMetricPricesResponse | null, usage: ListBillableMetricUsagesResponse | null, customerId: string, month: string): LlmModel[] {
  const compatible = new Set(METRICS.filter(metric => matchesMetricContract(metric.code, usage, data)).map(metric => metric.code));
  function price(metricCode: string, modelId: string, surface: SurfaceId): number | null {
    if (!compatible.has(metricCode)) return null;
    const metric = data?.billable_metric_prices.find(item => item.billable_metric_code === metricCode);
    const candidates = metric?.price_rates.filter(rate => rate.model === "per_unit" && rate.dimension_values.model === modelId && rate.dimension_values.surface === surface
      && rate.effective_from <= month && (rate.effective_until === null || rate.effective_until > month)
      && (!rate.archived_from || rate.archived_from > month)
      && (rate.customer_id === null || rate.customer_id === customerId)).sort((left, right) => right.effective_from.localeCompare(left.effective_from)) ?? [];
    const rate = candidates.find(item => item.customer_id === customerId) ?? candidates.find(item => item.customer_id === null);
    return rate?.unit_price ?? null;
  }
  return MODELS.map(model => ({
    id: model.id, name: model.name, kind: model.kind, description: model.description, surfaces: [...model.surfaces],
    prices: Object.fromEntries(model.surfaces.map(surface => [surface, Object.fromEntries(METRICS.map(metric => [metric.key, price(metric.code, model.id, surface)])) as MetricPrices])),
  }));
}

function paidAmount(payment: InvoicePaymentResponse | undefined, invoiceTotal: number): number {
  if (!payment || !["paid", "partially_canceled", "canceled"].includes(payment.status)) return 0;
  return Math.max(0, Math.min(invoiceTotal, payment.amount - payment.canceled_amount));
}

function isValidOutput(output: GenerationOutput): boolean {
  const values = [output.usage.input, output.usage.output, output.usage.cacheRead, output.usage.cacheWrite, output.latencyMs];
  return typeof output.output === "string" && values.every(value => Number.isSafeInteger(value) && value >= 0) && Number.isSafeInteger(values.reduce((sum, value) => sum + value, 0));
}

// MeterEngine이 다시 보내도 같은 이유로 거절하는 수집 오류. 자동 재전송을 멈추고 직접 재전송만 받는다.
const PERMANENT_INGEST_REJECTIONS = new Set(["customer_archived", "customer_not_found", "event_too_old", "validation_error"]);

export class OpenAbService {
  readonly config: OpenAbConfig;
  readonly store: OpenAbStore;
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
    if (!account) throw new ServiceError(401, "session_required", "회사 계정을 골라 다시 들어와 주세요.");
    return account;
  }

  session(token?: string): SessionResponse {
    const account = this.accountForSession(token);
    return { configured: this.config.isConfigured, user: account ? viewer(account) : null, accounts: this.config.accounts.map(viewer), provider: PROVIDER_INFO };
  }

  login(accountId: unknown): string {
    if (typeof accountId !== "string" || !this.config.accounts.some(account => account.id === accountId)) throw new ServiceError(400, "invalid_account", "체험할 회사 계정을 골라 주세요.");
    return this.store.createSession(accountId, this.now());
  }

  logout(token?: string): void { this.store.revokeSession(token); }

  getRequest(account: ServiceAccount, id: string): GenerationView {
    const generation = this.store.getGeneration(account.id, id.toLowerCase());
    if (!generation) throw new ServiceError(404, "request_not_found", "이 계정의 요청을 찾을 수 없어요.");
    return generation;
  }

  listRequests(account: ServiceAccount): GenerationView[] { return this.store.listGenerations(account.id); }

  listConversations(account: ServiceAccount): ConversationSummary[] { return this.store.listConversations(account.id); }

  getConversation(account: ServiceAccount, id: string): ConversationView {
    const conversation = isValidRequestId(id) ? this.store.getConversation(account.id, id.toLowerCase()) : null;
    if (!conversation) throw new ServiceError(404, "conversation_not_found", "대화를 찾을 수 없어요. 목록에서 다시 골라 주세요.");
    return conversation;
  }

  // 대화(chat)와 API 플레이그라운드(api)의 요청 하나를 처리한다.
  async generate(account: ServiceAccount, input: unknown): Promise<GenerationView> {
    const body = requestBody(input);
    if (!isValidRequestId(body.requestId)) throw new ServiceError(400, "invalid_request_id", "요청 번호가 올바르지 않아요. 화면을 새로 고친 뒤 다시 보내 주세요.");
    if (body.surface !== "chat" && body.surface !== "api") throw new ServiceError(400, "invalid_surface", "대화나 API 화면에서 보내 주세요.");
    const surface: SurfaceId = body.surface;
    const model = modelFor(body.modelId, surface);
    if (!isValidText(body.prompt, PROMPT_MAX_LENGTH, true)) throw new ServiceError(400, "invalid_prompt", "메시지를 1자부터 4,000자까지 입력해 주세요.");
    const spec: GenerationSpec = { id: body.requestId.toLowerCase(), surface, modelId: model.id, modelName: model.name, prompt: body.prompt };
    if (surface === "chat") {
      if (!isValidRequestId(body.conversationId)) throw new ServiceError(400, "invalid_conversation", "대화를 찾을 수 없어요. 새 대화를 시작해 주세요.");
      spec.conversationId = body.conversationId.toLowerCase();
    } else if (body.system !== undefined && body.system !== "") {
      if (model.kind === "embedding" || !isValidText(body.system, SYSTEM_MAX_LENGTH, false)) throw new ServiceError(400, "invalid_system", "시스템 지시는 텍스트 모델에서 4,000자까지 넣을 수 있어요.");
      spec.system = body.system;
    }
    return this.run(account, spec);
  }

  // 일괄 처리는 건마다 요청 하나로 저장하고 이벤트도 건마다 하나씩 보낸다.
  async generateBatch(account: ServiceAccount, input: unknown): Promise<BatchView> {
    const body = requestBody(input);
    if (!isValidRequestId(body.batchId)) throw new ServiceError(400, "invalid_request_id", "요청 번호가 올바르지 않아요. 화면을 새로 고친 뒤 다시 보내 주세요.");
    const model = modelFor(body.modelId, "batch");
    const items = body.items;
    const invalid = new ServiceError(400, "invalid_batch", `한 번에 1건부터 ${BATCH_MAX_ITEMS}건까지, 건마다 2,000자까지 넣을 수 있어요.`);
    if (!Array.isArray(items) || items.length === 0 || items.length > BATCH_MAX_ITEMS) throw invalid;
    const specs = items.map((item: unknown, index): GenerationSpec => {
      const entry = requestBody(item);
      if (!isValidRequestId(entry.requestId) || !isValidText(entry.prompt, BATCH_ITEM_MAX_LENGTH, true)) throw invalid;
      return { id: entry.requestId.toLowerCase(), surface: "batch", modelId: model.id, modelName: model.name, prompt: entry.prompt, batchId: (body.batchId as string).toLowerCase(), batchIndex: index };
    });
    if (new Set(specs.map(spec => spec.id)).size !== specs.length) throw invalid;
    for (const spec of specs) {
      try { await this.run(account, spec); }
      catch (error) { if (!(error instanceof ServiceError) || error.code !== "generation_failed") throw error; }
    }
    return { id: specs[0].batchId!, modelId: model.id, items: this.store.listBatch(account.id, specs[0].batchId!) };
  }

  private async run(account: ServiceAccount, spec: GenerationSpec): Promise<GenerationView> {
    const claim = this.store.reserveGeneration(account.id, spec, this.now());
    if (claim.leaseToken) {
      try {
        const system = spec.system ?? "";
        const history = spec.conversationId && claim.turn ? this.store.chatHistory(account.id, spec.conversationId, claim.turn) : [];
        const isSystemCached = spec.surface === "api" && estimateTokens(system) >= PROMPT_CACHE_MIN_TOKENS
          && this.store.usePromptCache(account.id, createHash("sha256").update(`${spec.modelId}\n${system}`).digest("hex"), this.now(), PROMPT_CACHE_TTL_MS);
        const output = await this.provider.generate({ surface: spec.surface, modelId: spec.modelId, prompt: spec.prompt, system, history, isSystemCached }, { requestId: spec.id, accountId: account.id });
        if (!isValidOutput(output)) throw new Error("Invalid provider response");
        this.store.completeGeneration(account.id, spec.id, claim.leaseToken, output, usageEvent({
          requestId: spec.id, customerId: account.customerId, occurredAt: claim.generation.createdAt,
          modelId: spec.modelId, surface: spec.surface, usage: output.usage, latencyMs: output.latencyMs,
        }));
      } catch {
        this.store.failGeneration(account.id, spec.id, claim.leaseToken);
        throw new ServiceError(503, "generation_failed", "응답을 만들지 못했어요. 같은 요청으로 다시 보내면 이어서 처리해요.", true);
      }
    }
    await this.flushMetering({ accountId: account.id, generationId: spec.id, limit: 1 });
    return this.getRequest(account, spec.id);
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
    const body = requestBody(input, "다시 보낼 요청을 확인해 주세요.");
    const id = body.requestId;
    if (id !== undefined && !isValidRequestId(id)) throw new ServiceError(404, "request_not_found", "이 계정의 요청을 찾을 수 없어요.");
    const requestId = typeof id === "string" ? id.toLowerCase() : undefined;
    if (requestId && !this.store.getGeneration(account.id, requestId)) throw new ServiceError(404, "request_not_found", "이 계정의 요청을 찾을 수 없어요.");
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
    const billingErrors = [draft.status === "rejected" ? "청구 예정액" : "", invoices.status === "rejected" ? "확정 청구서" : "", payments.status === "rejected" ? "납부 상태" : "", method.status === "rejected" && methodCode !== "payment_method_not_found" ? "결제 카드" : ""].filter(Boolean);
    const priceData = commonPrices.status === "fulfilled" && customerPrices.status === "fulfilled" ? mergePriceSchedules(commonPrices.value, customerPrices.value, account.customerId) : null;
    const hasPriceQueryFailure = commonPrices.status === "rejected" || customerPrices.status === "rejected";
    const usageData = usage.status === "fulfilled" ? usage.value : null;
    const hasCompatibleMetrics = METRICS.every(metric => matchesMetricContract(metric.code, usageData, priceData));
    const models = modelsWithPrices(priceData, usageData, account.customerId, month);
    const hasMissingPrice = !hasCompatibleMetrics || models.some(model => model.surfaces.some(surface => METRICS.some(metric => isRequiredPrice(model.kind, metric.key) && model.prices[surface]?.[metric.key] == null)));
    const isPriceReadFailure = usage.status === "rejected" || hasPriceQueryFailure;
    if (payments.status === "fulfilled" && visibleInvoices.some(invoice => !visiblePayments.some(payment => payment.invoice_id === invoice.id))) billingErrors.push("일부 납부 상태");
    if (method.status === "fulfilled" && method.value.customer_id !== account.customerId) billingErrors.push("결제 카드");
    if (hasMissingPrice && isPriceReadFailure) billingErrors.push("적용 요금");
    const billingError = [
      billingErrors.length ? `청구 정보 일부를 불러오지 못했어요: ${billingErrors.join(", ")}. 잠시 뒤 새로 불러와 주세요.` : "",
      hasMissingPrice && !isPriceReadFailure ? "이번 달 요금 설정이 서비스 기준과 맞지 않아 요금을 표시하지 않아요. 서비스 운영자에게 문의해 주세요." : "",
    ].filter(Boolean).join(" ");
    const metricCodes = new Set(METRICS.map(metric => metric.code));
    return {
      user: viewer(account), month, provider: PROVIDER_INFO,
      models,
      stats: this.store.stats(account.id, range.start, range.end),
      requests: this.store.listGenerations(account.id, range.start, range.end),
      usage: {
        state: usage.status === "fulfilled" && hasCompatibleMetrics ? "ready" : "unavailable", updatedAt: new Date(this.now()).toISOString(),
        metrics: usage.status === "fulfilled" ? usage.value.billable_metric_usages.filter(metric => metricCodes.has(metric.code)).flatMap(metric => metric.customers.filter(customer => customer.customer_id === account.customerId).map(customer => ({
          code: metric.code, key: METRICS.find(item => item.code === metric.code)!.key, name: metricName(metric.code),
          quantity: customer.quantity, rawQuantity: customer.raw_quantity ?? customer.quantity, unitSize: metric.unit_size ?? 1,
          breakdown: customer.dimension_usages.flatMap(dimension => {
            const surface = dimensionSurface(dimension.dimension_values);
            return typeof dimension.dimension_values.model === "string" && surface ? [{ modelId: dimension.dimension_values.model, surface, quantity: dimension.quantity, rawQuantity: dimension.raw_quantity ?? dimension.quantity }] : [];
          }),
        }))) : [],
        ...(usage.status === "rejected" ? { error: "사용량을 불러오지 못했어요. 잠시 뒤 새로 불러와 주세요." }
          : hasPriceQueryFailure ? { error: "요금표를 불러오지 못해 적용 단가를 표시하지 않아요. 잠시 뒤 새로 불러와 주세요." }
          : !hasCompatibleMetrics ? { error: "사용량 집계 기준이 서비스 기준과 달라 사용량을 표시하지 않아요. 서비스 운영자에게 문의해 주세요." } : {}),
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
    if (!this.registrationClient) throw new ServiceError(503, "billing_unavailable", "카드 등록을 아직 쓸 수 없어요. 서비스 운영자에게 문의해 주세요.");
    try {
      const registration = await this.registrationClient.createPaymentMethodRegistration(account.customerId);
      if (!registration.token || registration.customer_id !== account.customerId || registration.mode !== (this.config.meterEngineEnvironment ?? "test")) throw new Error("Invalid registration");
      return { url: new URL(`/payment-method-registrations/${encodeURIComponent(registration.token)}`, this.config.meterEngineConsoleUrl).href, expiresAt: registration.expires_at, mode: registration.mode };
    } catch {
      throw new ServiceError(503, "billing_unavailable", "카드 등록을 시작하지 못했어요. 잠시 뒤 다시 시도해 주세요.", true);
    }
  }
}
