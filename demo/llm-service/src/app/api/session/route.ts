import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { readConfig } from "../../../lib/config.ts";
import { PROVIDER_INFO } from "../../../lib/contracts.ts";
import { assertRequest, jsonResponse, readJson, safely, SESSION_COOKIE } from "../../../lib/http.ts";
import { getRuntime } from "../../../lib/runtime.ts";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(request: Request): Promise<NextResponse> {
  return safely(async () => {
    const config = readConfig();
    assertRequest(request, config);
    if (!config.isConfigured) return jsonResponse({ configured: false, user: null, accounts: [], provider: PROVIDER_INFO });
    return jsonResponse(getRuntime().session((await cookies()).get(SESSION_COOKIE)?.value));
  });
}

export async function POST(request: Request): Promise<NextResponse> {
  return safely(async () => {
    const service = getRuntime();
    assertRequest(request, service.config, true);
    const body = await readJson(request);
    const token = service.login(typeof body === "object" && body !== null ? (body as Record<string, unknown>).accountId : undefined);
    const oldToken = (await cookies()).get(SESSION_COOKIE)?.value;
    if (oldToken) service.logout(oldToken);
    const response = jsonResponse(service.session(token));
    response.cookies.set(SESSION_COOKIE, token, { httpOnly: true, sameSite: "lax", secure: service.config.appOrigin.startsWith("https:"), path: "/", maxAge: 8 * 60 * 60 });
    return response;
  });
}

export async function DELETE(request: Request): Promise<NextResponse> {
  return safely(async () => {
    const service = getRuntime();
    assertRequest(request, service.config, true);
    service.logout((await cookies()).get(SESSION_COOKIE)?.value);
    const response = new NextResponse(null, { status: 204, headers: { "Cache-Control": "no-store" } });
    response.cookies.set(SESSION_COOKIE, "", { httpOnly: true, sameSite: "lax", secure: service.config.appOrigin.startsWith("https:"), path: "/", maxAge: 0 });
    return response;
  });
}
