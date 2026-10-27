export type InvoiceViewLink = {
  id: string;
  created_at: string;
  expires_at: string;
  revoked_at: string | null;
  mode: string;
  allow_payment_retry: boolean;
};

export type CreatedInvoiceViewLink = InvoiceViewLink & { token: string };

export type PublicInvoice = {
  invoice_number: string;
  organization_name: string;
  customer_name: string;
  month: string;
  finalized_at: string;
  supply_amount: number;
  tax_amount: number;
  total_amount: number;
  lines: {
    billable_metric_code: string | null;
    target_property: string | null;
    dimension_values: Record<string, unknown>;
    quantity: number;
    quantity_numerator?: number | null;
    quantity_denominator?: number | null;
    unit_price: number;
    amount: number;
    line_type?: string;
    tier_index?: number;
  }[];
  payment: {
    status: string;
    mode: string;
    amount: number;
    paid_amount: number | null;
    canceled_amount: number;
    outstanding_amount: number | null;
    completed_at: string | null;
  };
  payment_retry: {
    allowed: boolean;
    reason: string;
    version: string | null;
  };
  expires_at: string;
};

export function isValidInvoiceViewToken(value: unknown): value is string {
  return typeof value === "string" && /^me_invoice_[A-Za-z0-9]{32}$/.test(value);
}
