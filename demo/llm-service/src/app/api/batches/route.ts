import { cookies } from "next/headers";
import type { NextResponse } from "next/server";
import { assertRequest, jsonResponse, readJson, safely, SESSION_COOKIE } from "../../../lib/http.ts";
import { getRuntime } from "../../../lib/runtime.ts";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// 20건 x 2,000자 한글이 들어오는 크기다.
const BATCH_BODY_BYTES = 262144;

export async function POST(request: Request): Promise<NextResponse> {
  return safely(async () => {
    const service = getRuntime();
    assertRequest(request, service.config, true);
    const account = service.requireAccount((await cookies()).get(SESSION_COOKIE)?.value);
    return jsonResponse(await service.generateBatch(account, await readJson(request, BATCH_BODY_BYTES)));
  });
}
