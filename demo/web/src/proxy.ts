import { NextResponse, type NextRequest } from "next/server";

import { isLocalHost } from "@/lib/local-host";

const WEBHOOK_PATH = "/api/webhooks";

export function proxy(request: NextRequest): NextResponse {
  if (request.nextUrl.pathname !== WEBHOOK_PATH && !isLocalHost(request.headers.get("host"))) {
    return NextResponse.json({ error: "host" }, { status: 403 });
  }
  return NextResponse.next();
}
