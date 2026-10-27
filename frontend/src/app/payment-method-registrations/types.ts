export type PaymentMethodRegistrationResponse = {
  customer_id: string;
  customer_name: string;
  organization_name: string;
  client_key: string;
  customer_key: string;
  mode: "test" | "live";
  expires_at: string;
};

export type PaymentMethodResponse = {
  customer_id: string;
  card_issuer_code: string;
  card_number: string;
  mode: "test" | "live";
  authenticated_at: string;
};

export type RegistrationFlow = {
  nonce: string;
  customerKey: string;
  consentDocumentVersion: string;
  expiresAt: number;
};

export type RegistrationOutcome = {
  status: "success" | "canceled" | "invalid" | "expired" | "unavailable" | "rejected";
  customerName?: string;
  organizationName?: string;
  mode?: "test" | "live";
  cardNumber?: string;
};
