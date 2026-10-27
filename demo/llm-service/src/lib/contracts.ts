export type AccountChoice = { id: string; name: string; email: string };
export type Viewer = AccountChoice;
export type ProviderInfo = { kind: "local-demo"; label: string; tokenMethod: string };

export type LlmModel = {
  id: string;
  name: string;
  description: string;
  inputPricePerThousand: number | null;
  outputPricePerThousand: number | null;
  requestPrice: number | null;
};

export type SessionResponse = {
  configured: boolean;
  user: Viewer | null;
  accounts: AccountChoice[];
  provider: ProviderInfo;
};

export type GenerationView = {
  id: string;
  modelId: string;
  modelName: string;
  prompt: string;
  output: string;
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
  createdAt: string;
  state: "processing" | "completed" | "failed";
  meteringStatus: "pending" | "delivered" | "failed";
};

export type BillingLine = {
  label: string;
  metricCode: string | null;
  modelId: string | null;
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
  name: string;
  quantity: number;
  rawQuantity: number;
  unitSize: number;
  modelQuantities: Array<{ modelId: string; quantity: number; rawQuantity: number }>;
};

export type WorkspaceSnapshot = {
  user: Viewer;
  month: string;
  provider: ProviderInfo;
  models: LlmModel[];
  stats: {
    totalRequests: number;
    inputTokens: number;
    outputTokens: number;
    pendingMetering: number;
    failedMetering: number;
  };
  requests: GenerationView[];
  usage: { state: "ready" | "unavailable"; updatedAt: string; metrics: MeterUsageView[]; error?: string };
  billing: {
    state: "ready" | "unavailable";
    draft: DraftView | null;
    invoices: InvoiceView[];
    paymentMethod: { cardNumber: string; authenticatedAt: string; archived: boolean } | null;
    canRegisterPaymentMethod: boolean;
    error?: string;
  };
};

export type ApiErrorBody = { error: { code: string; message: string; retryable?: boolean } };

export const MODEL_CATALOG = [
  { id: "lumen-fast", name: "Lumen Fast", description: "짧은 초안과 빠른 아이디어 정리" },
  { id: "lumen-standard", name: "Lumen Standard", description: "구조를 갖춘 설명과 업무 초안" },
] as const;

export const PROVIDER_INFO: ProviderInfo = {
  kind: "local-demo",
  label: "로컬 시연 모델",
  tokenMethod: "입출력 문자를 기준으로 계산한 시연용 토큰입니다. 실제 모델의 토큰 수와 다릅니다.",
};

export const METRIC_CODES = { input: "llm-input-tokens", output: "llm-output-tokens", requests: "llm-requests" } as const;
export const EVENT_TYPE = "llm_generation";
