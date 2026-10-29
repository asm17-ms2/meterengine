import { appendFile, mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { parseWebhookReceiptLine, type WebhookReceipt } from "./webhook-receipt.ts";
import type { WebhookScope } from "./webhook-target.ts";

async function bindLegacy(directory: string, scope: WebhookScope): Promise<void> {
  const file = path.join(directory, "webhooks-legacy-source.json");
  try { await writeFile(file, JSON.stringify({ version: 1, source: scope.id }) + "\n", { flag: "wx", mode: 0o600 }); }
  catch (error) { if ((error as NodeJS.ErrnoException).code !== "EEXIST") throw error; }
  const binding: unknown = JSON.parse(await readFile(file, "utf8"));
  if (typeof binding !== "object" || binding === null || !("source" in binding) || binding.source !== scope.id) throw new Error("기존 수신 기록의 연결 대상이 다릅니다. 원래 대상 설정을 사용하세요.");
}

async function storePath(directory: string, scope: WebhookScope): Promise<string> {
  if (!/^[a-f0-9]{64}$/.test(scope.id) || scope.isLegacy && scope.environment !== "legacy") throw new Error("수신 기록의 연결 범위를 확인하세요.");
  await mkdir(directory, { recursive: true, mode: 0o700 });
  if (scope.isLegacy) { await bindLegacy(directory, scope); return path.join(directory, "webhooks.jsonl"); }
  return path.join(directory, "webhook-sources", `${scope.id}.jsonl`);
}

export async function appendScopedWebhookReceipt(directory: string, scope: WebhookScope, receipt: WebhookReceipt): Promise<void> {
  const file = await storePath(directory, scope);
  await mkdir(path.dirname(file), { recursive: true, mode: 0o700 });
  await appendFile(file, `${JSON.stringify(receipt)}\n`, { encoding: "utf8", mode: 0o600 });
}

export async function listScopedWebhookReceipts(directory: string, scope: WebhookScope, limit = 100): Promise<WebhookReceipt[]> {
  let contents: string;
  try { contents = await readFile(await storePath(directory, scope), "utf8"); }
  catch (error) { if ((error as NodeJS.ErrnoException).code === "ENOENT") return []; throw error; }
  const lines = contents.split("\n");
  const receipts: WebhookReceipt[] = [];
  for (let index = lines.length - 1; index >= 0 && receipts.length < limit; index -= 1) {
    const receipt = parseWebhookReceiptLine(lines[index]);
    if (receipt !== null) receipts.push(receipt);
  }
  return receipts;
}
