import { receiveWebhook } from "@/lib/webhook-receipt";
import { appendWebhookReceipt } from "@/lib/webhook-store";

export async function POST(request: Request): Promise<Response> {
  const payload = await request.text();
  const { status, receipt } = receiveWebhook({
    payload,
    headers: request.headers,
    secret: process.env.METERENGINE_WEBHOOK_SECRET,
  });
  await appendWebhookReceipt(receipt);
  return Response.json(status === 200 ? {} : { reason: receipt.reason }, { status });
}
