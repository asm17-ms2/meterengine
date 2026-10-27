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
};

export const CAPABILITIES = {
  listEvents: { page: true, size: true, sort: true, q: true, customer_id: true, type: true, month_all: false, range: true },
  aggregateBillableMetricUsages: { page: false, size: false, sort: false, q: false, customer_id: true, type: false, month_all: false },
  previewDraftInvoice: { page: false, size: false, sort: false, q: false, customer_id: true, type: false, month_all: false },
  listInvoices: { page: false, size: false, sort: false, q: false, customer_id: true, type: false, month_all: true },
  listCustomers: { page: false, size: false, sort: false, q: false, customer_id: false, type: false, month_all: false, archive: true },
  listBillableMetrics: { page: false, size: false, sort: false, q: false, customer_id: false, type: false, month_all: false, archive: true },
  listBillableMetricPrices: { page: false, size: false, sort: false, q: false, customer_id: false, type: false, month_all: false, archive: true },
} as const satisfies Record<string, ListCapabilities>;
