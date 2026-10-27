export interface FieldError {
  field: string;
  message: string;
}

export interface ErrorResponse {
  code: string;
  message: string;
  errors?: FieldError[];
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
