export interface IngestEventRequest {
  transaction_id: string;
  customer_id: string;
  type: string;
  timestamp: string;
  properties: Record<string, unknown>;
}

export type IngestEventInput = Omit<IngestEventRequest, "transaction_id"> & { transaction_id?: string };

export interface IngestEventResponse {
  transaction_id: string;
  duplicate: boolean;
}

export interface IngestEventBatchRequest {
  events: IngestEventRequest[];
}

export interface IngestEventBatchResult {
  index: number;
  transaction_id: string;
  result: "stored" | "duplicate" | "rejected";
  error?: ErrorResponse;
}

export interface IngestEventBatchResponse {
  results: IngestEventBatchResult[];
}

export interface FieldError {
  field: string;
  message: string;
}

export interface ErrorResponse {
  code: string;
  message: string;
  errors?: FieldError[];
}

export interface EventResponse {
  transaction_id: string;
  customer_id: string;
  customer_name: string;
  type: string;
  occurred_at: string;
  received_at: string;
  request_id?: string;
  actor_type?: string;
  actor_id?: string;
  properties: Record<string, unknown>;
}

export interface ListEventsResponse {
  events: EventResponse[];
  month: string | null;
  from?: string;
  to?: string;
  page: number;
  size: number;
  total: number;
}

export interface CustomerResponse {
  id: string;
  name: string | null;
  billing_email: string | null;
  created_at: string | null;
  version?: number;
  external_id?: string | null;
  archived_at?: string | null;
}

export interface ListCustomersResponse {
  customers: CustomerResponse[];
}

export interface ListEventsParams {
  page?: number;
  size?: number;
  customer_id?: string;
  month?: string;
  type?: string;
  from?: string;
  to?: string;
  q?: string;
  properties?: Record<string, unknown> | string;
  sort?: "occurred_at" | "received_at" | "transaction_id" | "customer_name" | "type";
  order?: "asc" | "desc";
}

export type ListEventTypesParams = Pick<ListEventsParams, "customer_id" | "month" | "from" | "to" | "q" | "properties">;
export type EventExportParams = ListEventsParams & { include_properties?: boolean };

export interface ListEventTypesResponse {
  types: string[];
}

export type ArchiveStatus = "active" | "archived" | "all";

export interface CreateCustomerRequest {
  name: string;
  billing_email?: string;
  external_id?: string;
}

export type BillableMetricAggregation = "sum" | "count" | "count_distinct" | "min" | "max" | "avg" | "first" | "last" | "median" | "percentile";
export type BillableMetricRoundingMode = "none" | "ceil" | "floor" | "round";

export interface BillableMetricCalculation {
  aggregation: string;
  target_property: string | null;
  percentile?: number | null;
  unit_size?: number;
  rounding_mode?: BillableMetricRoundingMode;
  rounding_precision?: number;
}

export interface BillableMetricUsageResponse extends BillableMetricCalculation {
  code: string;
  name: string;
  event_type: string;
  customers: Array<{
    customer_id: string;
    customer_name: string;
    quantity: number;
    raw_quantity?: number;
    raw_quantity_numerator?: number;
    raw_quantity_denominator?: number;
    dimension_usages: Array<{ dimension_values: Record<string, unknown>; quantity: number; raw_quantity?: number; raw_quantity_numerator?: number; raw_quantity_denominator?: number }>;
  }>;
}

export interface ListBillableMetricUsagesResponse {
  month: string;
  billable_metric_usages: BillableMetricUsageResponse[];
}

export interface InvoiceLineResponse {
  billable_metric_code: string;
  target_property: string | null;
  dimension_values: Record<string, unknown>;
  quantity: number;
  quantity_numerator?: number | null;
  quantity_denominator?: number | null;
  unit_price: number;
  amount: number;
  line_type: string;
  tier_index: number;
}

export interface InvoiceResponse {
  id: string;
  invoice_number: string;
  customer_id: string;
  customer_name: string;
  month: string;
  finalized_at: string;
  supply_amount: number;
  tax_amount: number;
  total_amount: number;
  lines: InvoiceLineResponse[];
}

export interface ListInvoicesResponse {
  invoices: InvoiceResponse[];
}

export interface CreateInvoiceViewLinkRequest {
  expires_in_days?: number;
  allow_payment_retry?: boolean;
}

export interface InvoiceViewLinkResponse {
  id: string;
  mode: "test" | "live";
  allow_payment_retry: boolean;
  created_at: string;
  expires_at: string;
  revoked_at: string | null;
}

export interface InvoiceViewLinkSecretResponse extends InvoiceViewLinkResponse {
  token: string;
}

export interface ListInvoiceViewLinksResponse {
  invoice_view_links: InvoiceViewLinkResponse[];
}

export interface DraftInvoiceResponse {
  month: string;
  calculated_at: string;
  total_amount: number;
  customers: Array<{
    customer_id: string;
    customer_name: string;
    amount: number;
    lines: InvoiceLineResponse[];
    unpriced_usages: Array<{ billable_metric_code: string; target_property: string | null; dimension_values: Record<string, unknown>; quantity: number }>;
    negative_usages: Array<{ billable_metric_code: string; target_property: string | null; dimension_values: Record<string, unknown>; quantity: number }>;
  }>;
}

export interface InvoicePaymentResponse {
  invoice_id: string;
  status: "not_required" | "unpaid" | "pending" | "paid" | "failed" | "partially_canceled" | "canceled";
  mode: "test" | "live";
  amount: number;
  canceled_amount: number;
  attempt_count: number;
  last_failure_code: string | null;
  next_retry_at: string | null;
  completed_at: string | null;
}

export interface ListInvoicePaymentsResponse {
  invoice_payments: InvoicePaymentResponse[];
}

export interface PaymentMethodResponse {
  customer_id: string;
  card_issuer_code: string;
  card_number: string;
  mode: "test" | "live";
  authenticated_at: string;
  archived_at?: string | null;
}

export interface ListPaymentMethodsResponse {
  payment_methods: PaymentMethodResponse[];
}

export interface OrganizationResponse {
  id: string;
  name: string;
  status: "pending" | "active" | "rejected" | "suspended";
  created_at: string;
}

export interface PriceRateTier {
  up_to: number | null;
  unit_price: number;
}

export interface ListBillableMetricPricesResponse {
  billable_metric_prices: Array<{
    billable_metric_code: string;
    dimension_properties: string[];
    price_rates: Array<{
      id: string;
      dimension_values: Record<string, unknown>;
      unit_price: number | null;
      customer_id: string | null;
      effective_from: string;
      effective_until: string | null;
      model: "per_unit" | "graduated" | "volume";
      tiers: PriceRateTier[] | null;
      archived_at?: string | null;
      archived_from?: string | null;
    }>;
  }>;
}

export interface ListPricePlansResponse {
  price_plans: Array<{
    id: string;
    customer_id: string | null;
    effective_from: string;
    effective_until: string | null;
    base_fee: number;
    minimum_amount: number;
    archived_at?: string | null;
    archived_from?: string | null;
  }>;
}

export interface PaymentMethodRegistrationResponse {
  token?: string;
  customer_id: string;
  customer_name: string;
  organization_name: string;
  client_key: string;
  customer_key: string;
  mode: "test" | "live";
  expires_at: string;
}

export interface WebhookEndpointResponse {
  id: string;
  url: string;
  event_types: string[];
  enabled: boolean;
  description: string | null;
  created_at: string;
  archived_at?: string | null;
  secret?: string;
}

export interface ListWebhookEndpointsResponse {
  endpoints: WebhookEndpointResponse[];
}

export interface ListWebhookDeliveriesResponse {
  page: number;
  size: number;
  total: number;
  deliveries: Array<{
    id: string;
    webhook_event_id: string;
    type: string;
    status: "pending" | "done" | "failed";
    attempt_count: number;
    next_attempt_at: string | null;
    last_attempted_at: string | null;
    response_status: number | null;
    failure_reason: string | null;
    created_at: string;
    attempts: Array<{ attempted_at: string; response_status: number | null; failure_reason: string | null; duration_ms: number }>;
  }>;
}

export interface UnbilledEventsResponse {
  month: string;
  cutoff_at: string;
  unmatched_type: { event_count: number; types: Array<{ type: string; event_count: number }> };
  non_numeric_value: { event_count: number; billable_metrics: Array<{ billable_metric_code: string; event_count: number }> };
  future_timestamp: { event_count: number; earliest_occurred_at: string | null; latest_occurred_at: string | null };
  negative_quantity: { event_count: number; billable_metrics: Array<{ billable_metric_code: string; event_count: number }> };
}

export type WorkspaceEnvironment = "test" | "live";
export type SearchParams = {
  page?: number; size?: number; q?: string; sort?: string; order?: "asc" | "desc";
  status?: string; month?: string; customer_id?: string; external_id?: string; billable_metric_code?: string;
  from?: string; to?: string; min_amount?: number; max_amount?: number; mode?: "test" | "live";
};
export type SearchPage<T, Summary = Record<string, unknown>> = {
  items: T[]; total: number; total_unfiltered: number; page: number; size: number;
  summary: Summary;
};
export type UsageSearchSummary = {
  month: string; customer_count: number; metric_count: number; line_count: number; nonzero_customer_count: number;
};
export type BillableMetricResponse = BillableMetricCalculation & {
  code: string; name: string; event_type: string; target_property: string | null;
  dimension_properties: string[]; archived_at: string | null; version?: number;
};
export type CustomerSearchItem = CustomerResponse & { version: number };
export type CustomerUsageSearchItem = { customer_id: string; customer_name: string; metrics: BillableMetricUsageResponse[] };
export type InvoiceSearchItem = { invoice: InvoiceResponse; payment: InvoicePaymentResponse };
export type SearchResource = "customers" | "billable-metrics" | "billable-metric-prices" | "usage" | "invoices/draft" | "invoices";
export type CsvResource = SearchResource | "events";
export type EventIssueReason = "unknown_event_type" | "invalid_target_property" | "unpriced_usage";
export type EventIssue = {
  transaction_id: string; customer_id: string; customer_name: string; type: string;
  occurred_at: string; received_at: string; reason: EventIssueReason;
  billable_metric_code: string | null; target_property: string | null;
  action_hint: string; negative_quantity: boolean;
};
export type EventIssueSearchResponse = {
  month: string; cutoff_at: string; page: number; size: number; total: number;
  issues: EventIssue[]; summary: Record<EventIssueReason, number>;
  warnings: { scope: "organization_month"; future_event_count: number; negative_event_metric_count: number };
};

export type EventRejection = {
  id: string; transaction_id: string | null; customer_id: string | null; type: string | null;
  occurred_at: string | null; error_code: string; error_fields: string[];
  request_id: string | null; batch_index: number | null; recorded_at: string;
};
export type EventRejectionSearchResponse = {
  month: string; recording_started_at: string | null; page: number; size: number; total: number;
  rejections: EventRejection[];
};
