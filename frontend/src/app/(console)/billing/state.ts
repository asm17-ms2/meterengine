export type FinalizeInvoicesState =
  | { status: "idle" }
  | { status: "failed"; message: string }
  | { status: "succeeded"; finalizedCount: number; failedCount: number; supplyAmount: number; taxAmount: number; totalAmount: number; invoices: { id: string; invoice_number?: string; customer_name: string; total_amount: number }[] };

export const FINALIZE_INVOICES_IDLE: FinalizeInvoicesState = { status: "idle" };
