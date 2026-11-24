import "server-only";

import { serverFetch, type Result } from "@/lib/api/client";
import type { InvoiceResponse } from "@/lib/api/invoices";
import type { SearchPage } from "@/lib/api/search";
import type { InvoicePaymentResponse } from "@/lib/api/workspace";
import { config } from "@/lib/config";

export type OverviewPayment = InvoicePaymentResponse & { customer_id: string; customer_name: string; month: string };

export type Overview = {
  calculatedAt: string;
  customerCount: number;
  billableMetricCount: number;
  activeApiKeyCount: number;
  isSupplierConfigured: boolean;
  isPaymentConfigured: boolean;
  paymentMode: string;
  finalizedTotalAmount: number;
  collectedAmount: number;
  canceledAmount: number;
  outstandingAmount: number;
  failedPaymentCount: number;
  invoicePayments: OverviewPayment[];
  unresolvedInvoicePayments: OverviewPayment[];
};

type Supplier = { business_registration_number: string | null; representative_name: string | null; business_address: string | null };

// 서버의 결제 상태 분류와 같다. 취소는 받은 금액에서 뺀다.
const COLLECTED = new Set(["paid", "partially_canceled", "canceled"]);
const OUTSTANDING = new Set(["unpaid", "pending", "failed"]);

const fetchApi = <T>(path: string, params?: Record<string, string | undefined>) => serverFetch<T>(config.apiBaseUrl, path, params);

function withInvoices(payments: InvoicePaymentResponse[], invoices: InvoiceResponse[]): OverviewPayment[] {
  const invoiceById = new Map(invoices.map(invoice => [invoice.id, invoice]));
  return payments.flatMap(payment => {
    const invoice = invoiceById.get(payment.invoice_id);
    return invoice ? [{ ...payment, customer_id: invoice.customer_id, customer_name: invoice.customer_name, month: invoice.month }] : [];
  });
}

// 홈은 달 전체 청구 예정액을 계산하지 않고, 화면에 필요한 값을 개별 API로 받는다.
export async function getOverview(month: string): Promise<Result<Overview>> {
  const [customers, metrics, apiKeys, supplier, setting, credentials, monthInvoices, monthPayments, allPayments] = await Promise.all([
    fetchApi<SearchPage<unknown>>("/v1/customers/search", { status: "active", size: "1" }),
    fetchApi<SearchPage<unknown>>("/v1/billable-metrics/search", { status: "active", size: "1" }),
    fetchApi<{ api_keys: unknown[] }>("/v1/api-keys", { status: "active" }),
    fetchApi<Supplier>("/v1/organization/supplier"),
    fetchApi<{ mode: string }>("/v1/payment-setting"),
    fetchApi<{ toss_payments_credentials: { mode: string }[] }>("/v1/toss-payments-credentials", { status: "active" }),
    fetchApi<{ invoices: InvoiceResponse[] }>("/v1/invoices", { month }),
    fetchApi<{ invoice_payments: InvoicePaymentResponse[] }>("/v1/invoice-payments", { month }),
    fetchApi<{ invoice_payments: InvoicePaymentResponse[] }>("/v1/invoice-payments"),
  ]);
  if (!customers.ok) return customers;
  if (!metrics.ok) return metrics;
  if (!apiKeys.ok) return apiKeys;
  if (!supplier.ok) return supplier;
  if (!setting.ok) return setting;
  if (!credentials.ok) return credentials;
  if (!monthInvoices.ok) return monthInvoices;
  if (!monthPayments.ok) return monthPayments;
  if (!allPayments.ok) return allPayments;

  const unresolved = allPayments.data.invoice_payments.filter(payment => OUTSTANDING.has(payment.status));
  // 처리할 결제가 있을 때만 고객 이름을 붙이려고 전체 월의 청구서를 받는다.
  const allInvoices = unresolved.length > 0 ? await fetchApi<{ invoices: InvoiceResponse[] }>("/v1/invoices") : null;
  if (allInvoices && !allInvoices.ok) return allInvoices;

  const payments = monthPayments.data.invoice_payments;
  const sum = (values: number[]) => values.reduce((total, value) => total + value, 0);
  const { business_registration_number: number, representative_name: name, business_address: address } = supplier.data;
  return {
    ok: true,
    data: {
      calculatedAt: new Date().toISOString(),
      customerCount: customers.data.total,
      billableMetricCount: metrics.data.total,
      activeApiKeyCount: apiKeys.data.api_keys.length,
      isSupplierConfigured: Boolean(number && name && address),
      isPaymentConfigured: credentials.data.toss_payments_credentials.some(credential => credential.mode === setting.data.mode),
      paymentMode: setting.data.mode,
      finalizedTotalAmount: sum(payments.map(payment => payment.amount)),
      collectedAmount: sum(payments.filter(payment => COLLECTED.has(payment.status)).map(payment => payment.amount - payment.canceled_amount)),
      canceledAmount: sum(payments.map(payment => payment.canceled_amount)),
      outstandingAmount: sum(payments.filter(payment => OUTSTANDING.has(payment.status)).map(payment => payment.amount)),
      failedPaymentCount: payments.filter(payment => payment.status === "failed").length,
      invoicePayments: withInvoices(payments, monthInvoices.data.invoices),
      unresolvedInvoicePayments: allInvoices ? withInvoices(unresolved, allInvoices.data.invoices) : [],
    },
  };
}
