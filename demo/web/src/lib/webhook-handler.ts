import type { TargetEnv } from "./config.ts";
import { receiveWebhook, type WebhookReceipt } from "./webhook-receipt.ts";
import { selectWebhookTarget, type WebhookScope } from "./webhook-target.ts";

export async function handleWebhookRequest(request: Request, env: TargetEnv, append: (scope: WebhookScope, receipt: WebhookReceipt) => Promise<void>, now?: Date): Promise<Response> {
  const selected = selectWebhookTarget(new URL(request.url).searchParams.getAll("target"), env);
  if (!selected.ok) return Response.json({ reason: selected.reason }, { status: 400 });
  const { scope, secret } = selected.value;
  const { status, receipt } = receiveWebhook({ payload: await request.text(), headers: request.headers, secret, environment: scope.environment, now });
  try { await append(scope, receipt); }
  catch { return Response.json({ reason: "receipt_store_unavailable" }, { status: 503 }); }
  return Response.json(status === 200 ? {} : { reason: receipt.reason }, { status });
}
