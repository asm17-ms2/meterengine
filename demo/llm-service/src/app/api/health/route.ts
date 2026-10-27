import { readConfig } from "../../../lib/config.ts";
import { jsonResponse } from "../../../lib/http.ts";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export function GET() {
  try { return jsonResponse({ status: "ok", configured: readConfig().isConfigured, provider: "local-demo" }); }
  catch { return jsonResponse({ status: "unavailable", configured: false }, 503); }
}
