"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { workspaceHref } from "@/lib/workspace-href";
import { serverSend, toDisplayMessage } from "@/lib/api/client";
import { requireTenantSession } from "@/lib/auth/session";
import { config } from "@/lib/config";
import { canPerformWorkspaceOperation } from "@/lib/auth/permissions";
import { InputFieldError, readWholeNumber, successMessage } from "@/app/(console)/workspace-action-feedback";

export type WorkspaceActionState = { message?: string; fieldErrors?: Record<string, string>; isSuccess?: boolean; secret?: string; registrationPath?: string; registrationLabel?: string; isUnconfirmed?: boolean; isConfirmedFailure?: boolean };

function text(data: FormData, key: string) {
  const value = data.get(key);
  return typeof value === "string" ? value.trim() : "";
}

function optional(data: FormData, key: string) { return text(data, key) || undefined; }
function identifier(data: FormData, key = "id") { return encodeURIComponent(text(data, key)); }
function amount(data: FormData, key: string, { min = 0, max = Number.MAX_SAFE_INTEGER, message = "0 이상 9,007,199,254,740,991 이하의 정수로 입력하세요." } = {}) {
  return readWholeNumber(text(data, key), key, { min, max, message });
}

export async function workspaceAction(_previous: WorkspaceActionState, data: FormData): Promise<WorkspaceActionState> {
  const session = await requireTenantSession();

  let path: string;
  let method: "POST" | "PUT" | "PATCH" | "DELETE" = "POST";
  let body: unknown;
  const operation = text(data, "operation");
  if (!canPerformWorkspaceOperation(session.scopes, session.organization_role, operation)) return { message: "이 작업을 수행할 권한이 없습니다. 대표관리자에게 권한을 요청하세요." };
  if (!text(data, "organization_id") || text(data, "organization_id") !== session.organization?.id) return { message: "열어 둔 화면의 도입사가 바뀌었습니다. 현재 도입사를 확인한 뒤 다시 시도하세요." };
  try {
    switch (operation) {
      case "createMember": path = "/v1/organization/members"; body = { email: text(data, "email"), name: text(data, "name"), password: typeof data.get("password") === "string" ? data.get("password") : "", scopes: data.getAll("scopes") }; break;
      case "updateMemberRole": path = `/v1/organization/members/${identifier(data)}/role`; method = "PUT"; body = { organization_role: text(data, "organization_role") }; break;
      case "updateMemberScopes": path = `/v1/organization/members/${identifier(data)}/scopes`; method = "PUT"; body = { scopes: data.getAll("scopes") }; break;
      case "archiveMember": path = `/v1/organization/members/${identifier(data)}`; method = "DELETE"; break;
      case "createOrganizationInvitation": path = "/v1/organization/invitations"; body = { email: text(data, "email"), scopes: data.getAll("scopes") }; break;
      case "revokeOrganizationInvitation": path = `/v1/organization/invitations/${identifier(data)}`; method = "DELETE"; break;
      case "restoreWebhookEndpoint": path = `/v1/webhook-endpoints/${identifier(data)}/restorations`; break;
      case "createApiKey":
        path = "/v1/api-keys";
        body = { label: text(data, "label"), mode: text(data, "mode"), scopes: data.getAll("scopes"), replaced_api_key_id: optional(data, "replaced_api_key_id"), rate_limit_per_minute: text(data, "rate_limit_per_minute") ? amount(data, "rate_limit_per_minute", { min: 1, max: 2147483647, message: "분당 요청 한도는 1 이상 2,147,483,647 이하의 정수로 입력하세요." }) : null };
        break;
      case "deleteApiKey": path = `/v1/api-keys/${identifier(data)}`; method = "DELETE"; break;
      case "updateApiKeyScopes": path = `/v1/api-keys/${identifier(data)}/scopes`; method = "PUT"; body = { scopes: data.getAll("scopes") }; break;
      case "createWebhookEndpoint":
        path = "/v1/webhook-endpoints";
        body = { url: text(data, "url"), event_types: data.getAll("event_types") };
        break;
      case "deleteWebhookEndpoint": path = `/v1/webhook-endpoints/${identifier(data)}`; method = "DELETE"; break;
      case "updateWebhookEndpoint": path = `/v1/webhook-endpoints/${identifier(data)}`; method = "PUT"; body = { url: text(data, "url"), event_types: data.getAll("event_types"), enabled: text(data, "enabled") === "true" }; break;
      case "testWebhookEndpoint": path = `/v1/webhook-endpoints/${identifier(data)}/test-events`; break;
      case "retryWebhookDelivery": path = `/v1/webhook-endpoints/${identifier(data)}/deliveries/${identifier(data, "delivery_id")}/retries`; break;
      default: return { message: "수행할 작업을 확인하지 못했습니다." };
    }
  } catch (error) {
    if (error instanceof InputFieldError) return { message: "입력한 내용을 확인하세요.", fieldErrors: { [error.field]: error.message } };
    return { message: "입력한 값을 처리하지 못했습니다. 입력값을 확인한 뒤 다시 시도하세요." };
  }
  if (false && !/^\d+$/.test(text(data, "version"))) return { message: "고객의 최신 정보를 다시 조회한 뒤 수정하세요." };
  const isPaymentRequest = false;
  const result = await serverSend<Record<string, unknown> | undefined>(config.apiBaseUrl, path, {
    method, body,
    timeoutMs: isPaymentRequest ? 75_000 : undefined,
    ifMatch: false ? `"${text(data, "version")}"` : undefined,
    idempotencyKey: ["createPaymentCancellation", "createCustomer"].includes(operation) ? text(data, "request_id") : undefined,
    isEmptyResponse: operation === "retryWebhookDelivery",
  });
  if (!result.ok) {
    if (isPaymentRequest && (result.error.status === 0 || result.error.code === "malformed_response" || result.error.code === "payment_result_unconfirmed" || result.error.code === "payment_cancellation_in_progress"))
      return { isUnconfirmed: true, message: "처리 결과를 확인하지 못했습니다. 중복 요청을 피하려면 결제와 취소 내역을 먼저 새로 조회하세요." };
    return { message: result.error.errors?.length ? "입력한 내용을 확인하세요." : toDisplayMessage(result.error), fieldErrors: result.error.errors ? Object.fromEntries(result.error.errors.map(error => [error.field, error.message])) : undefined };
  }
  revalidatePath("/", "layout");
  const redirectScope = new URLSearchParams({ organization_id: session.organization!.id });
  if (false) redirect(workspaceHref("/customers?status=archived", redirectScope));
  if (operation === "archiveMember") redirect(workspaceHref("/members?status=archived", redirectScope));
  if (operation === "deleteWebhookEndpoint") redirect(workspaceHref("/integrations?status=archived", redirectScope));
  const response = result.data;
  if (response?.status === "failed") return { isConfirmedFailure: true, message: String(response.failure_message ?? "요청이 거절됐습니다. 실패 사유를 확인하세요.") };
  if (response?.status === "pending") return { isUnconfirmed: true, message: "요청을 접수했습니다. 결과를 확인 중이므로 다시 청구하지 말고 상태를 새로고침하세요." };
  return {
    isSuccess: true, message: operation === "testWebhookEndpoint" ? "시험 통보를 발송 대기에 추가했습니다. 발송 내역에서 결과를 확인하세요." : successMessage(operation),
    secret: typeof response?.secret === "string" ? response.secret : undefined,
    registrationPath: typeof response?.token === "string" ? `${operation === "createOrganizationInvitation" ? "/organization-invitations/" : "/payment-method-registrations/"}${encodeURIComponent(response.token)}` : undefined,
    registrationLabel: operation === "createOrganizationInvitation" ? "초대 링크" : undefined,
  };
}
