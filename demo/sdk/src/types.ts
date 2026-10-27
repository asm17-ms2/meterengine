export interface FieldError {
  field: string;
  message: string;
}

export interface ErrorResponse {
  code: string;
  message: string;
  errors?: FieldError[];
}

export interface OrganizationResponse {
  id: string;
  name: string;
  status: "pending" | "active" | "rejected" | "suspended";
  created_at: string;
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
