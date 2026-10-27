import { randomUUID } from "node:crypto";

import { signWebhookPayload } from "@meterengine/sdk";

const DEFAULT_WEBHOOK_URL = "http://127.0.0.1:3100/api/webhooks";
const OLD_OFFSET_SECONDS = 600;

const args = process.argv.slice(2);
const isTampered = args.includes("--tamper");
const isOld = args.includes("--old");
const url = args.find((arg) => !arg.startsWith("--")) ?? process.env.METERENGINE_WEBHOOK_URL ?? DEFAULT_WEBHOOK_URL;
const secret = process.env.METERENGINE_WEBHOOK_SECRET;

if (secret === undefined || secret === "") {
  console.error("METERENGINE_WEBHOOK_SECRET을 설정하세요");
  process.exit(1);
}

const id = `msg_${randomUUID()}`;
const timestamp = Math.floor(Date.now() / 1000) - (isOld ? OLD_OFFSET_SECONDS : 0);
const payload = JSON.stringify({
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
    "webhook-id": id,
    "webhook-timestamp": String(timestamp),
    "webhook-signature": signature,
  },
  body,
});
console.log(`${response.status} ${await response.text()}`);
