import "server-only";
import path from "node:path";
import { appendScopedWebhookReceipt, listScopedWebhookReceipts } from "./webhook-file-store.ts";
import type { WebhookReceipt } from "./webhook-receipt.ts";
import type { WebhookScope } from "./webhook-target.ts";

function dataDirectory(): string { return path.join(process.cwd(), ".data"); }

export function appendWebhookReceipt(scope: WebhookScope, receipt: WebhookReceipt): Promise<void> {
  return appendScopedWebhookReceipt(dataDirectory(), scope, receipt);
}

export function listWebhookReceipts(scope: WebhookScope, limit = 100): Promise<WebhookReceipt[]> {
  return listScopedWebhookReceipts(dataDirectory(), scope, limit);
}
