export type ListCapabilities = {
  page: boolean;
  size: boolean;
  sort: boolean;
  q: boolean;
  customer_id: boolean;
  type: boolean;
  month_all: boolean;
  range?: boolean;
  archive?: boolean;
  archiveParameter?: "metric_status";
  preservedParameters?: readonly string[];
  pageBase?: 1;
  metric?: boolean;
  amount?: boolean;
  paymentStatus?: boolean;
  mode?: boolean;
};

export const CAPABILITIES = {
  listEvents: { page: true, size: true, sort: true, q: true, customer_id: true, type: true, month_all: false, range: true },
  aggregateBillableMetricUsages: { page: true, size: true, sort: true, q: true, customer_id: true, type: false, month_all: false, pageBase: 1, metric: true },
  previewDraftInvoice: { page: true, size: true, sort: true, q: true, customer_id: true, type: false, month_all: false, pageBase: 1, metric: true, amount: true },
  listInvoices: { page: true, size: true, sort: true, q: true, customer_id: true, type: false, month_all: true, pageBase: 1, metric: true, amount: true, range: true, paymentStatus: true, mode: true },
  listCustomers: { page: true, size: true, sort: true, q: true, customer_id: false, type: false, month_all: false, archive: true, pageBase: 1 },
  listBillableMetrics: { page: true, size: true, sort: true, q: true, customer_id: false, type: false, month_all: false, archive: true, pageBase: 1 },
  listBillableMetricPrices: { page: true, size: true, sort: true, q: true, customer_id: true, type: false, month_all: false, archive: true, archiveParameter: "metric_status", preservedParameters: ["plan_status", "rate_status", "metric"], pageBase: 1, metric: true },
} as const satisfies Record<string, ListCapabilities>;
