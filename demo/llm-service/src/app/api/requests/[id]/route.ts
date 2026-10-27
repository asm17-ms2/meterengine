import { cookies } from "next/headers";
import type { NextResponse } from "next/server";
import { assertRequest, jsonResponse, safely, SESSION_COOKIE } from "../../../../lib/http.ts";
import { getRuntime } from "../../../../lib/runtime.ts";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(request: Request, context: { params: Promise<{ id: string }> }): Promise<NextResponse> {
  return safely(async () => {
    const service = getRuntime();
    assertRequest(request, service.config);
    const account = service.requireAccount((await cookies()).get(SESSION_COOKIE)?.value);
    return jsonResponse(service.getRequest(account, (await context.params).id));
  });
}
