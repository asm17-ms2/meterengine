import { NextResponse, type NextRequest } from "next/server";

export function proxy(request: NextRequest) {
  const path = request.nextUrl.pathname;
  const requestHeaders = new Headers(request.headers);
  requestHeaders.set("x-meterengine-current-path", request.nextUrl.pathname + request.nextUrl.search);
  requestHeaders.delete("x-meterengine-environment");
  requestHeaders.delete("x-meterengine-organization-id");
  const organizationId = request.nextUrl.searchParams.get("organization_id");
  if (organizationId) requestHeaders.set("x-meterengine-organization-id", organizationId);
  if (["environment", "organization_id"].some(key => request.nextUrl.searchParams.getAll(key).length > 1)) return new NextResponse("업무 범위는 하나만 지정하세요.", { status: 400 });
  const environment = request.nextUrl.searchParams.get("environment");
  if (environment && !["legacy", "test", "live"].includes(environment)) return new NextResponse("업무 환경을 확인하세요.", { status: 400 });
  requestHeaders.set("x-meterengine-environment", environment ?? "legacy");
  if (["/", "/login", "/signup", "/pending", "/health", "/favicon.ico", "/invoice-links", "/invoice-links/lookup", "/invoice-links/payment-retries"].includes(path) || path.startsWith("/_next/") || path.startsWith("/payment-method-registrations/") || path.startsWith("/organization-invitations/")) return NextResponse.next();
  if (!request.cookies.get("me_session")?.value && !request.headers.has("next-action")) {
    const url = new URL("/login", request.url);
    url.searchParams.set("returnTo", path + request.nextUrl.search);
    return NextResponse.redirect(url);
  }
  return NextResponse.next({ request: { headers: requestHeaders } });
}

export const config = { matcher: ["/((?!_next/static|_next/image).*)"] };
