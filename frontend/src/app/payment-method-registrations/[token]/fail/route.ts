import type { NextRequest } from "next/server";
import { finishRegistration, isMatchingState, isValidRegistrationToken, readRegistrationFlow, redirectToRegistrationResult } from "../../flow";

export async function GET(request: NextRequest, { params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  if (!isValidRegistrationToken(token)) return new Response(null, { status: 404 });
  const flow = await readRegistrationFlow(token);
  const isValidState = isMatchingState(flow, request.nextUrl.searchParams.get("state"));
  const isCanceled = request.nextUrl.searchParams.get("code") === "PAY_PROCESS_CANCELED";
  await finishRegistration(token, { status: !isValidState ? "invalid" : isCanceled ? "canceled" : "rejected" });
  return redirectToRegistrationResult(request.url, token);
}
