import { requestApi } from "@/lib/api/transport";
import { isValidInvoiceViewToken, type PublicInvoice } from "@/lib/invoice-view-links";
import { isAllowedPublicInvoiceRequest } from "@/lib/public-invoice-request";

const RESPONSE_HEADERS = { "Cache-Control": "no-store, private", "Referrer-Policy": "no-referrer", "X-Robots-Tag": "noindex, nofollow, noarchive" };

function failure(status: number) {
  return Response.json({ message: status === 404 ? "사용할 수 없는 조회 링크입니다." : "청구서를 불러오지 못했습니다." }, { status, headers: RESPONSE_HEADERS });
}

export async function POST(request: Request) {
  if (!isAllowedPublicInvoiceRequest(request, process.env.METERENGINE_PUBLIC_ORIGIN)) return failure(403);
  if (request.headers.get("content-type")?.split(";")[0].trim() !== "application/json") return failure(400);
  const reader = request.body?.getReader();
  if (!reader) return failure(404);
  let body = "";
  let size = 0;
  let parsed: unknown;
  const decoder = new TextDecoder();
  try {
    for (;;) {
      const part = await reader.read();
      if (part.done) break;
      size += part.value.byteLength;
      if (size > 1024) { await reader.cancel(); return failure(413); }
      body += decoder.decode(part.value, { stream: true });
    }
    body += decoder.decode();
    parsed = JSON.parse(body);
  } catch { return failure(404); }
  if (!parsed || typeof parsed !== "object" || !("token" in parsed) || !isValidInvoiceViewToken(parsed.token)) return failure(404);
  try {
    const result = await requestApi<PublicInvoice>("/v1/public/invoice-links/lookup", { method: "POST", body: { token: parsed.token } });
    if (!result.ok) return failure([400, 404].includes(result.error.status) ? 404 : 503);
    return Response.json(result.data, { headers: RESPONSE_HEADERS });
  } catch { return failure(503); }
}
