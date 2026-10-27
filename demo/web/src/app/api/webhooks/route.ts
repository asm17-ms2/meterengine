import { handleWebhookRequest } from "@/lib/webhook-handler";
import { appendWebhookReceipt } from "@/lib/webhook-store";

export async function POST(request: Request): Promise<Response> {
  return handleWebhookRequest(request, process.env, appendWebhookReceipt);
}
