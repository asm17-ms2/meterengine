import type { MetricKey, ModelKind, SurfaceId } from "./catalog.ts";

export type AccountChoice = { id: string; name: string; email: string };
export type Viewer = AccountChoice;
export type ProviderInfo = { kind: "local-demo"; label: string; tokenMethod: string };

export type TokenUsage = Record<MetricKey, number>;
export type MetricPrices = Record<MetricKey, number | null>;

export type LlmModel = {
  id: string;
  name: string;
  kind: ModelKind;
  description: string;
  surfaces: SurfaceId[];
  prices: Partial<Record<SurfaceId, MetricPrices>>;
};

export type SessionResponse = {
  configured: boolean;
  user: Viewer | null;
  accounts: AccountChoice[];
  provider: ProviderInfo;
};

export type GenerationView = {
  id: string;
  surface: SurfaceId;
  modelId: string;
  modelName: string;
  conversationId: string | null;
  batchId: string | null;
  system: string;
  prompt: string;
  output: string;
  usage: TokenUsage;
  totalTokens: number;
  latencyMs: number;
  createdAt: string;
  state: "processing" | "completed" | "failed";
  meteringStatus: "pending" | "delivered" | "failed";
};

export type ConversationSummary = {
  id: string;
  title: string;
  modelId: string;
  createdAt: string;
  updatedAt: string;
  turns: number;
};

export type ConversationView = ConversationSummary & { messages: GenerationView[] };
export type BatchView = { id: string; modelId: string; items: GenerationView[] };

export type BillingLine = {
  label: string;
  metricCode: string | null;
  modelId: string | null;
  surface: SurfaceId | null;
  quantity: number;
  quantityNumerator?: number;
  quantityDenominator?: number;
  unitPrice: number;
  amount: number;
};

export type DraftView = {
  supplyAmount: number;
  taxAmount: number;
  totalAmount: number;
  lines: BillingLine[];
};

export type InvoiceView = DraftView & {
  id: string;
  month: string;
  finalizedAt: string;
  status: string;
  paidAmount: number | null;
  canceledAmount: number | null;
  amountDue: number | null;
};

export type MeterUsageView = {
  code: string;
  key: MetricKey;
  name: string;
  quantity: number;
  rawQuantity: number;
  unitSize: number;
  breakdown: Array<{ modelId: string; surface: SurfaceId; quantity: number; rawQuantity: number }>;
};

export type UsageStats = {
  totalRequests: number;
  usage: TokenUsage;
  pendingMetering: number;
  failedMetering: number;
};

// 화면마다 필요한 조회만 하도록 응답을 나눈다. 대화, API, 일괄 처리, 사용량은 UsageSnapshot을 쓴다.
type SnapshotBase = { user: Viewer; month: string };
export type UsageSnapshot = SnapshotBase & {
  provider: ProviderInfo;
  models: LlmModel[];
  stats: UsageStats;
  usage: { state: "ready" | "unavailable"; updatedAt: string; metrics: MeterUsageView[]; error?: string };
  pricing: { state: "ready" | "unavailable"; error?: string };
};
// 요청을 보낸 뒤에 다시 읽는 것. 앱의 원장에서만 세고 MeterEngine을 부르지 않는다.
export type UsageStatsSnapshot = SnapshotBase & { stats: UsageStats };
export type BillingSnapshot = SnapshotBase & {
  billing: {
    state: "ready" | "unavailable";
    draft: DraftView | null;
    invoices: InvoiceView[];
    paymentMethod: { cardNumber: string; authenticatedAt: string; archived: boolean } | null;
    canRegisterPaymentMethod: boolean;
    error?: string;
  };
};
export type HistorySnapshot = SnapshotBase & { requests: GenerationView[] };
export type WorkspaceSnapshot = UsageSnapshot | BillingSnapshot | HistorySnapshot;

export type ApiErrorBody = { error: { code: string; message: string; retryable?: boolean } };

export const PROVIDER_INFO: ProviderInfo = {
  kind: "local-demo",
  label: "로컬 모형",
  tokenMethod: "토큰 수는 글자 수로 어림한 시연용 값이에요. 실제 모델이 세는 토큰 수와 달라요.",
};

export const PROMPT_MAX_LENGTH = 4000;
export const SYSTEM_MAX_LENGTH = 4000;
export const BATCH_MAX_ITEMS = 20;
export const BATCH_ITEM_MAX_LENGTH = 2000;
