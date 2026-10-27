import { connection } from "next/server";

import { WebhookList } from "@/components/WebhookList";
import { isWebhookSecretUsable, type WebhookReceipt } from "@/lib/webhook-receipt";
import { listWebhookReceipts } from "@/lib/webhook-store";

const WEBHOOK_RECEIVE_URL = "http://127.0.0.1:3100/api/webhooks";

export default async function WebhooksPage() {
  await connection();
  const hasSecret = isWebhookSecretUsable(process.env.METERENGINE_WEBHOOK_SECRET);
  let receipts: WebhookReceipt[];
  try {
    receipts = await listWebhookReceipts();
  } catch (error) {
    const loadError = error instanceof Error ? error.message : String(error);
    return <WebhookList receiveUrl={WEBHOOK_RECEIVE_URL} hasSecret={hasSecret} receipts={[]} loadError={loadError} />;
  }
  return <WebhookList receiveUrl={WEBHOOK_RECEIVE_URL} hasSecret={hasSecret} receipts={receipts} />;
}
