import { NextResponse, type NextRequest } from "next/server";
import { withForwardedClientAddress } from "@/lib/client-address";
import { environmentReadyPath, isAllowedEnvironment, isClosedLegacyEnvironment, readEnvironment, showsLegacyEnvironment } from "@/lib/environment";

export function proxy(request: NextRequest) {
  const path = request.nextUrl.pathname;
  const forwardedHeaders = withForwardedClientAddress(request.headers);
  const requestHeaders = new Headers(forwardedHeaders);
  requestHeaders.set("x-meterengine-current-path", request.nextUrl.pathname + request.nextUrl.search);
  requestHeaders.delete("x-meterengine-environment");
  requestHeaders.delete("x-meterengine-organization-id");
  const organizationId = request.nextUrl.searchParams.get("organization_id");
  if (organizationId) requestHeaders.set("x-meterengine-organization-id", organizationId);
  if (["environment", "organization_id"].some(key => request.nextUrl.searchParams.getAll(key).length > 1)) return new NextResponse("업무 범위는 하나만 지정하세요.", { status: 400 });
  const environment = request.nextUrl.searchParams.get("environment");
  const isLegacyShown = showsLegacyEnvironment(process.env.METERENGINE_SHOW_LEGACY_ENVIRONMENT);
  if (environment && !isAllowedEnvironment(environment, isLegacyShown)) {
    // 꺼진 legacy 주소로 연 업무 화면만 같은 화면의 테스트 환경으로 보낸다. 서버 액션과 그 밖의 요청은 다른 환경에서 처리되지 않게 거절한다.
    if (isClosedLegacyEnvironment(environment, isLegacyShown) && (request.method === "GET" || request.method === "HEAD") && !request.headers.has("next-action") && environmentReadyPath(path)) {
      const url = request.nextUrl.clone();
      url.searchParams.delete("environment");
      return NextResponse.redirect(url);
    }
    return new NextResponse("업무 환경을 확인하세요.", { status: 400 });
  }
  requestHeaders.set("x-meterengine-environment", readEnvironment(environment));
  if (["/", "/login", "/signup", "/terms", "/privacy", "/health", "/favicon.ico", "/icon.svg", "/apple-icon.png", "/brand/meterengine-mark.svg", "/brand/meterengine-mark.png", "/invoice-links", "/invoice-links/lookup", "/invoice-links/payment-retries", "/email-verifications"].includes(path) || path.startsWith("/_next/") || path.startsWith("/payment-method-registrations/") || path.startsWith("/organization-invitations/") || path.startsWith("/email-verifications/")) return NextResponse.next({ request: { headers: forwardedHeaders } });
  if (!request.cookies.get("me_session")?.value && !request.headers.has("next-action")) {
    const url = new URL("/login", request.url);
    url.searchParams.set("returnTo", path + request.nextUrl.search);
    return NextResponse.redirect(url);
  }
  return NextResponse.next({ request: { headers: requestHeaders } });
}

export const config = { matcher: ["/((?!_next/static|_next/image).*)"] };
