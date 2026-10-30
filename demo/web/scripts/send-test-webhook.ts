import { randomUUID } from "node:crypto";

import { signWebhookPayload } from "@meterengine/sdk";
import { isWebhookSecretUsable } from "../src/lib/webhook-receipt.ts";
import { selectWebhookTarget } from "../src/lib/webhook-target.ts";

const DEFAULT_WEBHOOK_URL = "http://127.0.0.1:3100/api/webhooks";
const OLD_OFFSET_SECONDS = 600;

const args = process.argv.slice(2);
const isTampered = args.includes("--tamper");
const isOld = args.includes("--old");
const url = new URL(args.find((arg) => !arg.startsWith("--")) ?? process.env.METERENGINE_WEBHOOK_URL ?? DEFAULT_WEBHOOK_URL);
const target = args.find(arg => arg.startsWith("--target="))?.slice("--target=".length);
if (target) url.searchParams.set("target", target);
const selected = selectWebhookTarget(url.searchParams.getAll("target"), process.env);
if (!selected.ok) {
  console.error("--target=대상이름 또는 수신 URL의 target으로 설정된 대상을 하나 선택하세요");
  process.exit(1);
}
const { secret, scope } = selected.value;

if (!isWebhookSecretUsable(secret)) {
  console.error("선택한 대상의 웹훅 서명 비밀값을 설정하세요");
  process.exit(1);
}

const id = `msg_${randomUUID()}`;
const timestamp = Math.floor(Date.now() / 1000) - (isOld ? OLD_OFFSET_SECONDS : 0);
const payload = JSON.stringify({
  environment: scope.environment,
  type: "webhook_test",
  timestamp: new Date(timestamp * 1000).toISOString(),
  data: { sent_by: "send-test-webhook" },
});
const signature = signWebhookPayload({ id, timestamp, payload, secret });
const body = isTampered ? payload.replace("webhook_test", "webhook_tampered") : payload;

const response = await fetch(url, {
  method: "POST",
  headers: {
    "content-type": "application/json",
    "user-agent": "MeterEngine-Webhooks/1.0",
    "webhook-id": id,
    "webhook-timestamp": String(timestamp),
    "webhook-signature": signature,
  },
  body,
});
console.log(`${response.status} ${await response.text()}`);
