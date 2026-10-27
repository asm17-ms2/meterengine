export interface FieldError {
  field: string;
  message: string;
}

export interface ErrorResponse {
  code: string;
  message: string;
  errors?: FieldError[];
}

export type ArchiveStatus = "active" | "archived" | "all";

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
