export type FinalizeInvoicesState =
  | { status: "idle" }
  | { status: "failed"; message: string }
  | { status: "succeeded"; finalizedCount: number };

export const FINALIZE_INVOICES_IDLE: FinalizeInvoicesState = { status: "idle" };
