import { cookies } from "next/headers";
import type { NextResponse } from "next/server";
import { assertRequest, jsonResponse, readJson, safely, SESSION_COOKIE } from "../../../lib/http.ts";
import { getRuntime } from "../../../lib/runtime.ts";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function POST(request: Request): Promise<NextResponse> {
  return safely(async () => {
    const service = getRuntime();
    assertRequest(request, service.config, true);
    const account = service.requireAccount((await cookies()).get(SESSION_COOKIE)?.value);
    await readJson(request);
    return jsonResponse(await service.registerPaymentMethod(account));
  });
}
