import { signWebhookPayload, verifyWebhookSignature, WebhookVerificationError } from "@meterengine/sdk";

export type WebhookReceipt = {
  received_at: string;
  webhook_id: string | null;
  type: string | null;
  verified: boolean;
  reason: string | null;
  payload: string;
};

export type ReceiveWebhookInput = { payload: string; headers: Headers; secret: string | undefined; environment?: "legacy" | "test" | "live"; now?: Date };

export type ReceiveWebhookResult = { status: 200 | 400; receipt: WebhookReceipt };

export const SECRET_NOT_CONFIGURED = "secret_not_configured";

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function typeOf(body: unknown): string | null {
  return isRecord(body) && typeof body.type === "string" ? body.type : null;
}

function readType(payload: string): string | null {
  try {
    return typeOf(JSON.parse(payload));
  } catch {
    return null;
  }
}

export function isWebhookSecretUsable(secret: string | undefined): secret is string {
  if (secret === undefined) {
    return false;
  }
  try {
    signWebhookPayload({ id: "", timestamp: 0, payload: "", secret });
    return true;
  } catch {
    return false;
  }
}

export function receiveWebhook(input: ReceiveWebhookInput): ReceiveWebhookResult {
  const now = input.now ?? new Date();
  const received = { received_at: now.toISOString(), webhook_id: input.headers.get("webhook-id"), payload: input.payload };
  const rejected = (reason: string): ReceiveWebhookResult => ({
    status: 400,
    receipt: { ...received, type: readType(input.payload), verified: false, reason },
  });
  if (!isWebhookSecretUsable(input.secret)) {
    return rejected(SECRET_NOT_CONFIGURED);
  }
  try {
    const event: unknown = verifyWebhookSignature({ payload: input.payload, headers: input.headers, secret: input.secret, now });
    const environment = isRecord(event) ? event.environment : undefined;
    if (input.environment !== undefined && (input.environment === "legacy" ? environment !== undefined && environment !== "legacy" : environment !== input.environment)) return rejected("environment_mismatch");
    return { status: 200, receipt: { ...received, type: typeOf(event), verified: true, reason: null } };
  } catch (error) {
    if (error instanceof WebhookVerificationError) {
      return rejected(error.reason);
    }
    throw error;
  }
}

function isNullableString(value: unknown): value is string | null {
  return value === null || typeof value === "string";
}

export function parseWebhookReceiptLine(line: string): WebhookReceipt | null {
  let parsed: unknown;
  try {
    parsed = JSON.parse(line);
  } catch {
    return null;
  }
  if (
    !isRecord(parsed) ||
    typeof parsed.received_at !== "string" ||
    Number.isNaN(Date.parse(parsed.received_at)) ||
    !isNullableString(parsed.webhook_id) ||
    !isNullableString(parsed.type) ||
    typeof parsed.verified !== "boolean" ||
    !isNullableString(parsed.reason) ||
    typeof parsed.payload !== "string"
  ) {
    return null;
  }
  return {
    received_at: parsed.received_at,
    webhook_id: parsed.webhook_id,
    type: parsed.type,
    verified: parsed.verified,
    reason: parsed.reason,
    payload: parsed.payload,
  };
}
