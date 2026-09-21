export type CustomerFormState =
  | { status: "idle" }
  | { status: "invalid"; message: string }
  | { status: "failed"; message: string }
  | { status: "done" };

export const CUSTOMER_FORM_IDLE: CustomerFormState = { status: "idle" };

export type CustomerDeleteState =
  | { status: "idle" }
  | { status: "rejected"; name: string }
  | { status: "gone"; name: string }
  | { status: "failed"; message: string }
  | { status: "done" };

export const CUSTOMER_DELETE_IDLE: CustomerDeleteState = { status: "idle" };
