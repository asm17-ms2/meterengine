import { NextResponse, type NextRequest } from "next/server";
import { environmentReadyPath, isWorkspaceEnvironment } from "@/lib/environment";

export function proxy(request: NextRequest) {
  const path = request.nextUrl.pathname;
  const requestHeaders = new Headers(request.headers);
  for (const name of ["x-meterengine-current-path", "x-meterengine-environment", "x-meterengine-organization-id"]) requestHeaders.delete(name);
  requestHeaders.set("x-meterengine-current-path", path + request.nextUrl.search);
  if (["/", "/login", "/signup", "/pending", "/health", "/favicon.ico", "/icon.svg", "/apple-icon.png", "/brand/meterengine-mark.svg", "/brand/meterengine-mark.png", "/invoice-links", "/invoice-links/lookup", "/invoice-links/payment-retries"].includes(path) || path.startsWith("/_next/") || path.startsWith("/payment-method-registrations/") || path.startsWith("/organization-invitations/")) return NextResponse.next({ request: { headers: requestHeaders } });
  if (["environment", "organization_id"].some(key => request.nextUrl.searchParams.getAll(key).length > 1)) return new NextResponse("업무 범위는 하나만 지정하세요.", { status: 400 });
  const environment = request.nextUrl.searchParams.get("environment");
  if (environment && environment !== "legacy" && !isWorkspaceEnvironment(environment)) return new NextResponse("업무 환경을 확인하세요.", { status: 400 });
  if (request.method === "GET" && (environmentReadyPath(path) || /^\/exports\/[^/]+$/.test(path)) && !isWorkspaceEnvironment(environment)) {
    const url = request.nextUrl.clone();
    url.searchParams.set("environment", "test");
    return NextResponse.redirect(url);
  }
  const organizationId = request.nextUrl.searchParams.get("organization_id");
  if (organizationId) requestHeaders.set("x-meterengine-organization-id", organizationId);
  if (isWorkspaceEnvironment(environment)) requestHeaders.set("x-meterengine-environment", environment);
  if (!request.cookies.get("me_session")?.value && !request.headers.has("next-action")) {
    const url = new URL("/login", request.url);
    url.searchParams.set("returnTo", path + request.nextUrl.search);
    return NextResponse.redirect(url);
  }
  return NextResponse.next({ request: { headers: requestHeaders } });
}

export const config = { matcher: ["/((?!_next/static|_next/image).*)"] };
