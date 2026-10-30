import type { NextRequest } from "next/server";
import { requestApi } from "@/lib/api/transport";
import { createBillingKey, finishRegistration, isMatchingState, isValidRegistrationToken, readRegistrationFlow, readRegistrationOutcome, redirectToRegistrationResult } from "../../flow";
import type { PaymentMethodRegistrationResponse } from "../../types";

export async function GET(request: NextRequest, { params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  if (!isValidRegistrationToken(token)) return new Response(null, { status: 404 });
  const previous = await readRegistrationOutcome(token);
  if (previous?.status === "success") return redirectToRegistrationResult(request.url, token);
  const flow = await readRegistrationFlow(token);
  const authKey = request.nextUrl.searchParams.get("authKey");
  const customerKey = request.nextUrl.searchParams.get("customerKey");
  if (!isMatchingState(flow, request.nextUrl.searchParams.get("state")) || !authKey || authKey.length > 300 || /[\u0000-\u001f]/.test(authKey) || customerKey !== flow?.customerKey) {
    await finishRegistration(token, { status: "invalid" });
    return redirectToRegistrationResult(request.url, token);
  }
  const registration = await requestApi<PaymentMethodRegistrationResponse>(`/v1/payment-method-registrations/${encodeURIComponent(token)}`);
  if (!registration.ok) {
    await finishRegistration(token, { status: registration.error.status === 404 ? "expired" : "unavailable" });
    return redirectToRegistrationResult(request.url, token);
  }
  if (registration.data.customer_key !== customerKey || !flow) {
    await finishRegistration(token, { status: "invalid" });
    return redirectToRegistrationResult(request.url, token);
  }
  const result = await createBillingKey(token, authKey, customerKey, flow.consentDocumentVersion);
  await finishRegistration(token, result.ok ? {
    status: "success",
    customerName: registration.data.customer_name,
    organizationName: registration.data.organization_name,
    mode: result.data.mode,
    cardNumber: result.data.card_number,
  } : { status: result.status === 404 ? "expired" : result.status === 400 ? "rejected" : "unavailable" });
  return redirectToRegistrationResult(request.url, token);
}
