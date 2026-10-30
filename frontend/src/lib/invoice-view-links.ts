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

type PublicInvoiceLine = PublicInvoice["lines"][number];

export function publicInvoiceLineName(line: Pick<PublicInvoiceLine, "line_type" | "billable_metric_code">): string {
  if (line.line_type === "base_fee") return "기본료";
  if (line.line_type === "minimum_adjustment") return "최소 사용료 조정";
  return line.billable_metric_code ?? "사용량";
}

export function publicInvoiceLineTier(line: Pick<PublicInvoiceLine, "line_type" | "tier_index">): string | null {
  if (line.line_type === "volume") return "총량 단가";
  if (line.line_type === "graduated_tier" && line.tier_index != null) return `구간 ${line.tier_index + 1}`;
  return null;
}

export function isValidInvoiceViewToken(value: unknown): value is string {
  return typeof value === "string" && /^me_invoice_[A-Za-z0-9]{32}$/.test(value);
}
