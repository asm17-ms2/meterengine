import { requestApi } from "@/lib/api/transport";
import { isValidInvoiceRetryVersion } from "@/lib/invoice-payment-retry";
import { isValidInvoiceViewToken, type PublicInvoiceResponse } from "@/lib/invoice-view-links";
import { isAllowedPublicInvoiceRequest } from "@/lib/public-invoice-request";

const RESPONSE_HEADERS = { "Cache-Control": "no-store, private", "Referrer-Policy": "no-referrer", "X-Robots-Tag": "noindex, nofollow, noarchive" };

function failure(status: number) {
  const message = status === 404 ? "사용할 수 없는 조회 링크입니다."
    : status === 409 ? "결제 상태가 변경되었거나 지금은 다시 시도할 수 없습니다. 결제 상태를 새로 조회해 주세요."
      : "결제 요청 결과를 확인하지 못했습니다. 다시 결제하지 말고 결제 상태를 새로 조회해 주세요.";
  return Response.json({ message }, { status, headers: RESPONSE_HEADERS });
}

export async function POST(request: Request) {
  if (!isAllowedPublicInvoiceRequest(request, process.env.METERENGINE_PUBLIC_ORIGIN)) return failure(403);
  if (request.headers.get("content-type")?.split(";")[0].trim() !== "application/json") return failure(400);
  const reader = request.body?.getReader();
  if (!reader) return failure(404);
  const decoder = new TextDecoder();
  let body = "";
  let size = 0;
  let parsed: unknown;
  try {
    for (;;) {
      const part = await reader.read();
      if (part.done) break;
      size += part.value.byteLength;
      if (size > 1024) { await reader.cancel(); return failure(413); }
      body += decoder.decode(part.value, { stream: true });
    }
    parsed = JSON.parse(body + decoder.decode());
  } catch { return failure(404); }
  if (!parsed || typeof parsed !== "object" || !("token" in parsed) || !isValidInvoiceViewToken(parsed.token)) return failure(404);
  if (!("retry_version" in parsed) || !isValidInvoiceRetryVersion(parsed.retry_version)
      || Object.keys(parsed).some(key => !["token", "retry_version"].includes(key))) return failure(400);
  try {
    const result = await requestApi<PublicInvoiceResponse>("/v1/public/invoice-links/payment-retries", {
      method: "POST", body: { token: parsed.token, retry_version: parsed.retry_version }, timeoutMs: 120_000,
    });
    if (!result.ok) return failure([400, 404, 409].includes(result.error.status) ? result.error.status : 503);
    return Response.json(result.data, { headers: RESPONSE_HEADERS });
  } catch { return failure(503); }
}
