import { NextResponse, type NextRequest } from "next/server";

export function proxy(request: NextRequest) {
  const path = request.nextUrl.pathname;
  const requestHeaders = new Headers(request.headers);
  requestHeaders.set("x-meterengine-current-path", request.nextUrl.pathname + request.nextUrl.search);
  requestHeaders.delete("x-meterengine-organization-id");
  const organizationId = request.nextUrl.searchParams.get("organization_id");
  if (organizationId) requestHeaders.set("x-meterengine-organization-id", organizationId);
  if (["/", "/login", "/signup", "/pending", "/health", "/favicon.ico"].includes(path) || path.startsWith("/_next/") || path.startsWith("/payment-method-registrations/") || path.startsWith("/organization-invitations/")) return NextResponse.next();
  if (!request.cookies.get("me_session")?.value) {
    const url = new URL("/login", request.url);
    url.searchParams.set("returnTo", path + request.nextUrl.search);
    return NextResponse.redirect(url);
  }
  return NextResponse.next({ request: { headers: requestHeaders } });
}

export const config = { matcher: ["/((?!_next/static|_next/image).*)"] };
