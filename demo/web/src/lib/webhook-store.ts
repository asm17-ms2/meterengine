import "server-only";

import { appendFile, mkdir, readFile } from "node:fs/promises";
import path from "node:path";

import { parseWebhookReceiptLine, type WebhookReceipt } from "@/lib/webhook-receipt";

function storePath(): string {
  return path.join(process.cwd(), ".data", "webhooks.jsonl");
}

export async function appendWebhookReceipt(receipt: WebhookReceipt): Promise<void> {
  const file = storePath();
  await mkdir(path.dirname(file), { recursive: true });
  await appendFile(file, `${JSON.stringify(receipt)}\n`, "utf8");
}

async function readStore(): Promise<string> {
  try {
    return await readFile(storePath(), "utf8");
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === "ENOENT") {
      return "";
    }
    throw error;
  }
}

export async function listWebhookReceipts(limit = 100): Promise<WebhookReceipt[]> {
  const lines = (await readStore()).split("\n");
  const receipts: WebhookReceipt[] = [];
  for (let index = lines.length - 1; index >= 0 && receipts.length < limit; index -= 1) {
    const receipt = parseWebhookReceiptLine(lines[index]);
    if (receipt !== null) {
      receipts.push(receipt);
    }
  }
  return receipts;
}
