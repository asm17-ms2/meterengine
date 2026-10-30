import type {
  CustomerResponse,
  BillableMetricCalculation,
  DraftInvoiceResponse,
  InvoicePaymentResponse,
  InvoiceResponse,
  MeterEngineClient,
  PaymentMethodResponse,
} from "@meterengine/sdk";

export type PortalUsage = BillableMetricCalculation & {
  code: string;
  name: string;
  quantity: number;
  raw_quantity?: number;
  raw_quantity_numerator?: number;
  raw_quantity_denominator?: number;
  dimension_usages: Array<{ dimension_values: Record<string, unknown>; quantity: number; raw_quantity?: number; raw_quantity_numerator?: number; raw_quantity_denominator?: number }>;
};

export type CustomerPortalData = {
  customer: CustomerResponse;
  month: string;
  usages: PortalUsage[];
  draft: DraftInvoiceResponse["customers"][number] | null;
  invoices: InvoiceResponse[];
  payments: InvoicePaymentResponse[];
  paymentMethod: (PaymentMethodResponse & { archived_at?: string | null }) | null;
  errors: string[];
  hasUsageData: boolean;
  hasDraftData: boolean;
  hasInvoiceData: boolean;
  hasPaymentData: boolean;
  hasPaymentMethodData: boolean;
};

type PortalClient = Pick<MeterEngineClient, "getCustomer" | "aggregateBillableMetricUsages" | "previewDraftInvoice" | "listInvoices" | "listInvoicePayments" | "getPaymentMethod">;

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "조회 결과를 확인할 수 없습니다";
}

export async function loadCustomerPortal(client: PortalClient, id: string, month: string): Promise<CustomerPortalData> {
  const customer = await client.getCustomer(id);
  const [usage, draft, invoices, payments, method] = await Promise.allSettled([
    client.aggregateBillableMetricUsages({ month }),
    client.previewDraftInvoice({ month }),
    client.listInvoices({ month, customer_id: id }),
    client.listInvoicePayments({ month, customer_id: id }),
    client.getPaymentMethod(id),
  ]);
  const errors: string[] = [];
  for (const [name, result] of [["사용량", usage], ["청구 예정액", draft], ["청구 내역", invoices], ["납부 상태", payments]] as const) {
    if (result.status === "rejected") errors.push(`${name}: ${errorMessage(result.reason)}`);
  }
  let hasPaymentMethodData = method.status === "fulfilled";
  if (method.status === "rejected") {
    const code = typeof method.reason === "object" && method.reason !== null && "code" in method.reason ? method.reason.code : undefined;
    hasPaymentMethodData = code === "payment_method_not_found";
    if (code !== "payment_method_not_found") errors.push(`등록 카드: ${errorMessage(method.reason)}`);
  }
  const selectedInvoices = invoices.status === "fulfilled" ? invoices.value.invoices.filter((invoice) => invoice.customer_id === id) : [];
  const invoiceIds = new Set(selectedInvoices.map((invoice) => invoice.id));
  return {
    customer,
    month,
    usages: usage.status === "fulfilled" ? usage.value.billable_metric_usages.flatMap((metric) => metric.customers.filter((candidate) => candidate.customer_id === id).map((candidate) => ({ code: metric.code, name: metric.name, aggregation: metric.aggregation, target_property: metric.target_property, percentile: metric.percentile, unit_size: metric.unit_size, rounding_mode: metric.rounding_mode, rounding_precision: metric.rounding_precision, quantity: candidate.quantity, raw_quantity: candidate.raw_quantity, raw_quantity_numerator: candidate.raw_quantity_numerator, raw_quantity_denominator: candidate.raw_quantity_denominator, dimension_usages: candidate.dimension_usages }))) : [],
    draft: draft.status === "fulfilled" ? draft.value.customers.find((candidate) => candidate.customer_id === id) ?? null : null,
    invoices: selectedInvoices,
    payments: payments.status === "fulfilled" ? payments.value.invoice_payments.filter((payment) => invoiceIds.has(payment.invoice_id)) : [],
    paymentMethod: method.status === "fulfilled" && method.value.customer_id === id ? method.value : null,
    errors,
    hasUsageData: usage.status === "fulfilled",
    hasDraftData: draft.status === "fulfilled",
    hasInvoiceData: invoices.status === "fulfilled",
    hasPaymentData: payments.status === "fulfilled",
    hasPaymentMethodData,
  };
}

export function currentBillingMonth(now = new Date()): string {
  return new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit" }).format(now);
}

export function isBillingMonth(value: string): boolean {
  return /^(?!0000)\d{4}-(0[1-9]|1[0-2])$/.test(value);
}
