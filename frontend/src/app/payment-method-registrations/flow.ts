import "server-only";

import { createHash, randomBytes, timingSafeEqual } from "node:crypto";
import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { config } from "@/lib/config";
import type { PaymentMethodResponse, RegistrationFlow, RegistrationOutcome } from "./types";

export const CONSENT_DOCUMENT_VERSION = "2026-10-28";
const COOKIE_PATH = "/payment-method-registrations";
const FLOW_LIFETIME_SECONDS = 15 * 60;

function cookieName(token: string, kind: "flow" | "result") {
  return `me_card_${kind}_${createHash("sha256").update(token).digest("hex").slice(0, 16)}`;
}

const COOKIE_OPTIONS = {
  httpOnly: true,
  secure: process.env.NODE_ENV === "production",
  sameSite: "lax" as const,
  path: COOKIE_PATH,
};

export function isValidRegistrationToken(token: string) {
  return /^[A-Za-z0-9_-]{43}$/.test(token);
}

export async function beginRegistrationFlow(token: string, customerKey: string) {
  const nonce = randomBytes(24).toString("base64url");
  const flow: RegistrationFlow = {
    nonce,
    customerKey,
    consentDocumentVersion: CONSENT_DOCUMENT_VERSION,
    expiresAt: Date.now() + FLOW_LIFETIME_SECONDS * 1000,
  };
  const store = await cookies();
  store.set(cookieName(token, "flow"), JSON.stringify(flow), {
    ...COOKIE_OPTIONS,
    maxAge: FLOW_LIFETIME_SECONDS,
  });
  store.set(cookieName(token, "result"), "", { ...COOKIE_OPTIONS, maxAge: 0 });
  return nonce;
}

export async function readRegistrationFlow(token: string): Promise<RegistrationFlow | null> {
  try {
    const value = (await cookies()).get(cookieName(token, "flow"))?.value;
    if (!value) return null;
    const flow = JSON.parse(value) as Partial<RegistrationFlow>;
    if (
      typeof flow.nonce !== "string" ||
      typeof flow.customerKey !== "string" ||
      flow.consentDocumentVersion !== CONSENT_DOCUMENT_VERSION ||
      typeof flow.expiresAt !== "number" ||
      flow.expiresAt <= Date.now()
    ) return null;
    return flow as RegistrationFlow;
  } catch {
    return null;
  }
}

export function isMatchingState(flow: RegistrationFlow | null, state: string | null) {
  if (!flow || !state) return false;
  const expected = Buffer.from(flow.nonce);
  const actual = Buffer.from(state);
  return expected.length === actual.length && timingSafeEqual(expected, actual);
}

export async function readRegistrationOutcome(token: string): Promise<RegistrationOutcome | null> {
  try {
    const value = (await cookies()).get(cookieName(token, "result"))?.value;
    if (!value) return null;
    const outcome = JSON.parse(value) as RegistrationOutcome;
    if (!["success", "canceled", "invalid", "expired", "unavailable", "rejected"].includes(outcome.status)) return null;
    return outcome;
  } catch {
    return null;
  }
}

export async function finishRegistration(token: string, outcome: RegistrationOutcome) {
  const store = await cookies();
  const previous = await readRegistrationOutcome(token);
  if (previous?.status !== "success") {
    store.set(cookieName(token, "result"), JSON.stringify(outcome), {
      ...COOKIE_OPTIONS,
      maxAge: FLOW_LIFETIME_SECONDS,
    });
  }
  store.set(cookieName(token, "flow"), "", { ...COOKIE_OPTIONS, maxAge: 0 });
}

export function redirectToRegistrationResult(requestUrl: string, token: string) {
  const response = NextResponse.redirect(
    new URL(`/payment-method-registrations/${encodeURIComponent(token)}/complete`, requestUrl),
    303,
  );
  response.headers.set("Cache-Control", "no-store");
  response.headers.set("Referrer-Policy", "no-referrer");
  return response;
}

export async function createBillingKey(
  token: string,
  authKey: string,
  customerKey: string,
  consentDocumentVersion: string,
): Promise<{ ok: true; data: PaymentMethodResponse } | { ok: false; status: number }> {
  try {
    const response = await fetch(new URL(`/v1/payment-method-registrations/${encodeURIComponent(token)}/billing-keys`, config.apiBaseUrl), {
      method: "POST",
      headers: { Accept: "application/json", "Content-Type": "application/json" },
      body: JSON.stringify({ auth_key: authKey, customer_key: customerKey, consent_document_version: consentDocumentVersion }),
      cache: "no-store",
      redirect: "error",
      signal: AbortSignal.timeout(70_000),
    });
    if (!response.ok) return { ok: false, status: response.status };
    return { ok: true, data: await response.json() as PaymentMethodResponse };
  } catch {
    return { ok: false, status: 0 };
  }
}
