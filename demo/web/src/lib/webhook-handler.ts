import type { TargetEnv } from "./config.ts";
import { receiveWebhook, type WebhookReceipt } from "./webhook-receipt.ts";
import { selectWebhookTarget, type WebhookScope } from "./webhook-target.ts";

const MAX_WEBHOOK_BYTES = 256 * 1024;

function isBrowserRequest(headers: Headers): boolean {
  const site = headers.get("sec-fetch-site");
  return headers.has("origin") || (site !== null && site !== "none");
}

async function readLimitedText(request: Request): Promise<string | null> {
  if (Number(request.headers.get("content-length")) > MAX_WEBHOOK_BYTES) return null;
  if (!request.body) return "";
  const reader = request.body.getReader();
  const chunks: Uint8Array[] = [];
  let size = 0;
  for (let part = await reader.read(); !part.done; part = await reader.read()) {
    size += part.value.byteLength;
    if (size > MAX_WEBHOOK_BYTES) { await reader.cancel(); return null; }
    chunks.push(part.value);
  }
  return Buffer.concat(chunks).toString("utf8");
}

export async function handleWebhookRequest(request: Request, env: TargetEnv, append: (scope: WebhookScope, receipt: WebhookReceipt) => Promise<void>, now?: Date): Promise<Response> {
  if (isBrowserRequest(request.headers)) return Response.json({ reason: "browser_request" }, { status: 403 });
  const payload = await readLimitedText(request);
  if (payload === null) return Response.json({ reason: "payload_too_large" }, { status: 413 });
  const selected = selectWebhookTarget(new URL(request.url).searchParams.getAll("target"), env);
  if (!selected.ok) return Response.json({ reason: selected.reason }, { status: 400 });
  const { scope, secret } = selected.value;
  const { status, receipt } = receiveWebhook({ payload, headers: request.headers, secret, environment: scope.environment, now });
  try { await append(scope, receipt); }
  catch { return Response.json({ reason: "receipt_store_unavailable" }, { status: 503 }); }
  return Response.json(status === 200 ? {} : { reason: receipt.reason }, { status });
}
