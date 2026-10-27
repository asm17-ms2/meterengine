import { createHmac, timingSafeEqual } from "node:crypto";
import { WebhookVerificationError } from "./errors.ts";

const SECRET_PREFIX = "whsec_";
const SIGNATURE_VERSION_PREFIX = "v1,";
const DEFAULT_TOLERANCE_SECONDS = 300;
const UNIX_SECONDS_PATTERN = /^\d+$/;

export interface WebhookEvent {
  environment?: "legacy" | "test" | "live";
  type: string;
  timestamp: string;
  data: unknown;
}

export interface SignWebhookPayloadInput {
  id: string;
  timestamp: number;
  payload: string;
  secret: string;
}

export interface VerifyWebhookSignatureInput {
  payload: string;
  headers: Headers | Record<string, string | undefined>;
  secret: string;
  toleranceSeconds?: number;
  now?: Date;
}

export function signWebhookPayload(input: SignWebhookPayloadInput): string {
  const key = decodeSecret(input.secret);
  return SIGNATURE_VERSION_PREFIX + computeSignature(key, input.id, input.timestamp, input.payload);
}

export function verifyWebhookSignature(input: VerifyWebhookSignatureInput): WebhookEvent {
  const key = decodeSecret(input.secret);
  const readHeader = headerReader(input.headers);
  const id = readHeader("webhook-id");
  const timestampHeader = readHeader("webhook-timestamp");
  const signatureHeader = readHeader("webhook-signature");
  if (!id || !timestampHeader || !signatureHeader) {
    throw new WebhookVerificationError("missing_header");
  }

  if (!UNIX_SECONDS_PATTERN.test(timestampHeader)) {
    throw new WebhookVerificationError("invalid_timestamp");
  }
  const timestamp = Number(timestampHeader);
  const nowSeconds = Math.floor((input.now ?? new Date()).getTime() / 1000);
  const toleranceSeconds = input.toleranceSeconds ?? DEFAULT_TOLERANCE_SECONDS;
  if (!(Math.abs(nowSeconds - timestamp) <= toleranceSeconds)) {
    throw new WebhookVerificationError("timestamp_out_of_range");
  }

  const expected = Buffer.from(computeSignature(key, id, timestamp, input.payload));
  const hasMatch = signatureHeader
    .split(" ")
    .filter((candidate) => candidate.startsWith(SIGNATURE_VERSION_PREFIX))
    .some((candidate) => signaturesEqual(expected, Buffer.from(candidate.slice(SIGNATURE_VERSION_PREFIX.length))));
  if (!hasMatch) {
    throw new WebhookVerificationError("signature_mismatch");
  }

  try {
    return JSON.parse(input.payload) as WebhookEvent;
  } catch {
    throw new WebhookVerificationError("invalid_payload");
  }
}

function decodeSecret(secret: string): Buffer {
  const encoded = secret.startsWith(SECRET_PREFIX) ? secret.slice(SECRET_PREFIX.length) : secret;
  const key = Buffer.from(encoded, "base64");
  if (key.length === 0) {
    throw new Error("웹훅 비밀값이 비어 있거나 base64로 디코드한 결과가 비어 있습니다");
  }
  return key;
}

function computeSignature(key: Buffer, id: string, timestamp: number, payload: string): string {
  return createHmac("sha256", key).update(`${id}.${timestamp}.${payload}`).digest("base64");
}

function signaturesEqual(expected: Buffer, actual: Buffer): boolean {
  return expected.length === actual.length && timingSafeEqual(expected, actual);
}

function headerReader(headers: Headers | Record<string, string | undefined>): (name: string) => string | undefined {
  if (headers instanceof Headers) {
    return (name) => headers.get(name) ?? undefined;
  }
  const normalized = new Map(Object.entries(headers).map(([name, value]) => [name.toLowerCase(), value]));
  return (name) => normalized.get(name);
}
