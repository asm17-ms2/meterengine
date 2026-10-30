import { NextResponse } from "next/server";
import type { ApiErrorBody } from "./contracts.ts";
import { isServiceError, ServiceError } from "./service-error.ts";

export { assertRequest, readJson } from "./request-validation.ts";

export const SESSION_COOKIE = "lumen_session";
export function jsonResponse(body: unknown, status = 200): NextResponse {
  return NextResponse.json(body, { status, headers: { "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff" } });
}

export async function safely(work: () => Promise<NextResponse> | NextResponse): Promise<NextResponse> {
  try { return await work(); }
  catch (error) {
    const safe = isServiceError(error) ? error : new ServiceError(503, "service_unavailable", "서비스를 준비하지 못했습니다. 잠시 후 다시 시도하세요.", true);
    const body: ApiErrorBody = { error: { code: safe.code, message: safe.message, retryable: safe.isRetryable } };
    return jsonResponse(body, safe.status);
  }
}
