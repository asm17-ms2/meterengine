"use server";

import { revalidatePath } from "next/cache";
import { headers } from "next/headers";
import { readEnvironment } from "@/lib/environment";
import { readMonth } from "@/lib/month";
import { workspaceHref } from "@/lib/workspace-href";
import { serverSend, toDisplayMessage } from "@/lib/api/client";
import { requireTenantSession } from "@/lib/auth/session";
import { config } from "@/lib/config";
import { canPerformWorkspaceOperation } from "@/lib/auth/permissions";
import { errorMessageForCode, InputFieldError, readBusinessRegistrationNumber, readWholeNumber, staleCancellationMessage, successMessage, unconfirmedMessage, existingCustomerMessage } from "@/app/(console)/workspace-action-feedback";

export type WorkspaceActionState = { message?: string; fieldErrors?: Record<string, string>; isSuccess?: boolean; secret?: string; registrationPath?: string; registrationLabel?: string; isUnconfirmed?: boolean; isConfirmedFailure?: boolean; nextPath?: string };

// 보관한 뒤에는 성공 안내를 보인 다음 그 보관 목록으로 옮긴다.
const ARCHIVED_LIST_PATHS: Partial<Record<string, string>> = { deleteCustomer: "/customers?status=archived", archiveMember: "/members?status=archived", deleteWebhookEndpoint: "/integrations?status=archived" };

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
  const environment = readEnvironment(text(data, "environment"));
  if (environment !== readEnvironment((await headers()).get("x-meterengine-environment"))) return { message: "열어 둔 화면과 요청의 업무 환경이 다릅니다. 환경을 확인한 뒤 다시 시도하세요." };

  let path: string;
  let method: "POST" | "PUT" | "PATCH" | "DELETE" = "POST";
  let body: unknown;
  const operation = text(data, "operation");
  if (environment !== "legacy" && ["updatePaymentSetting", "updateTossPaymentsCredential", "archiveTossPaymentsCredential", "createApiKey"].includes(operation) && text(data, "mode") !== environment) return { message: "현재 업무 환경과 다른 환경의 키나 결제 설정을 변경할 수 없습니다. 업무 환경을 확인한 뒤 다시 시도하세요." };
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
      case "createCustomer": path = "/v1/customers"; body = { name: text(data, "name"), billing_email: optional(data, "billing_email"), external_id: optional(data, "external_id") }; break;
      case "updateCustomer": path = `/v1/customers/${identifier(data)}`; method = "PATCH"; body = { name: text(data, "name"), billing_email: optional(data, "billing_email") ?? null }; break;
      case "archivePaymentMethod": path = `/v1/customers/${identifier(data)}/payment-method`; method = "DELETE"; break;
      case "archiveTossPaymentsCredential": path = `/v1/toss-payments-credentials/${identifier(data, "mode")}`; method = "DELETE"; break;
      case "restoreCustomer": path = `/v1/customers/${identifier(data)}/restorations`; break;
      case "restoreBillableMetric": path = `/v1/billable-metrics/${identifier(data, "code")}/restorations`; break;
      case "restorePriceRate": path = `/v1/billable-metrics/${identifier(data, "code")}/price-rates/${identifier(data)}/restorations`; break;
      case "restorePricePlan": path = `/v1/price-plans/${identifier(data)}/restorations`; break;
      case "restoreWebhookEndpoint": path = `/v1/webhook-endpoints/${identifier(data)}/restorations`; break;
      case "deleteCustomer": path = `/v1/customers/${identifier(data)}`; method = "DELETE"; break;
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
      case "updateWebhookEndpoint": path = `/v1/webhook-endpoints/${identifier(data)}`; method = "PUT"; body = { url: text(data, "url"), event_types: data.getAll("event_types"), enabled: text(data, "enabled") === "true", description: optional(data, "description")?.replace(/\r\n/g, "\n") ?? null }; break;
      case "testWebhookEndpoint": path = `/v1/webhook-endpoints/${identifier(data)}/test-events`; break;
      case "retryWebhookDelivery": path = `/v1/webhook-endpoints/${identifier(data)}/deliveries/${identifier(data, "delivery_id")}/retries`; break;
      case "createPricePlan":
        path = "/v1/price-plans";
        body = { customer_id: optional(data, "customer_id"), effective_from: text(data, "effective_from"), effective_until: optional(data, "effective_until"), base_fee: amount(data, "base_fee"), minimum_amount: amount(data, "minimum_amount") };
        break;
      case "deletePricePlan": path = `/v1/price-plans/${identifier(data)}`; method = "DELETE"; break;
      case "createPriceRate":
        path = `/v1/billable-metrics/${identifier(data, "code")}/price-rates`;
        body = { customer_id: optional(data, "customer_id"), effective_from: text(data, "effective_from"), effective_until: optional(data, "effective_until"), dimension_values: JSON.parse(text(data, "dimension_values") || "{}"), model: text(data, "model"), ...(text(data, "model") === "per_unit" ? { unit_price: text(data, "unit_price") } : { tiers: JSON.parse(text(data, "tiers")) }) };
        break;
      case "deletePriceRate": path = `/v1/billable-metrics/${identifier(data, "code")}/price-rates/${identifier(data)}`; method = "DELETE"; break;
      case "updateSupplier": path = "/v1/organization/supplier"; method = "PUT"; body = { business_registration_number: readBusinessRegistrationNumber(text(data, "business_registration_number"), "business_registration_number"), representative_name: text(data, "representative_name"), business_address: text(data, "business_address") }; break;
      case "updatePaymentSetting": path = "/v1/payment-setting"; method = "PUT"; body = { mode: text(data, "mode") }; break;
      case "updateTossPaymentsCredential": path = `/v1/toss-payments-credentials/${identifier(data, "mode")}`; method = "PUT"; body = { client_key: text(data, "client_key"), secret_key: text(data, "secret_key") }; break;
      case "createPaymentMethodRegistration": path = `/v1/customers/${identifier(data)}/payment-method-registrations`; break;
      case "createPaymentAttempt": path = `/v1/invoices/${identifier(data)}/payment-attempts`; break;
      case "createPaymentCancellation": path = `/v1/payment-attempts/${identifier(data)}/cancellations`; body = { reason: text(data, "reason"), amount: text(data, "amount") ? amount(data, "amount") : undefined }; break;
      case "finalizeInvoices": path = `/v1/invoices?month=${encodeURIComponent(text(data, "month"))}`; break;
      default: return { message: "수행할 작업을 확인하지 못했습니다." };
    }
  } catch (error) {
    if (error instanceof InputFieldError) return { message: "입력한 내용을 확인하세요.", fieldErrors: { [error.field]: error.message } };
    return { message: "입력한 값을 처리하지 못했습니다. 입력값을 확인한 뒤 다시 시도하세요." };
  }
  if (operation === "updateCustomer" && !/^\d+$/.test(text(data, "version"))) return { message: "고객의 최신 정보를 다시 조회한 뒤 수정하세요." };
  const isPaymentRequest = ["createPaymentAttempt", "createPaymentCancellation", "updateTossPaymentsCredential"].includes(operation);
  const result = await serverSend<Record<string, unknown> | undefined>(config.apiBaseUrl, path, {
    method, body, environment,
    timeoutMs: isPaymentRequest ? 75_000 : undefined,
    ifMatch: operation === "updateCustomer" ? `"${text(data, "version")}"` : undefined,
    idempotencyKey: ["createPaymentCancellation", "createCustomer"].includes(operation) ? text(data, "request_id") : undefined,
    isEmptyResponse: operation === "retryWebhookDelivery",
  });
  if (!result.ok) {
    if (isPaymentRequest && (result.error.status === 0 || result.error.code === "malformed_response" || result.error.code === "payment_result_unconfirmed" || result.error.code === "payment_cancellation_in_progress"))
      return { isUnconfirmed: true, message: unconfirmedMessage(operation) };
    const staleCancellation = staleCancellationMessage(operation, result.error.code);
    if (staleCancellation) return { isUnconfirmed: true, message: staleCancellation };
    return { message: result.error.errors?.length ? "입력한 내용을 확인하세요." : errorMessageForCode(result.error.code) ?? toDisplayMessage(result.error), fieldErrors: result.error.errors ? Object.fromEntries(result.error.errors.map(error => [error.field, error.message])) : undefined };
  }
  revalidatePath("/", "layout");
  const archivedListPath = ARCHIVED_LIST_PATHS[operation];
  if (archivedListPath) {
    const archivedListScope = new URLSearchParams({ organization_id: session.organization!.id, environment });
    if (operation === "deleteCustomer") archivedListScope.set("month", readMonth(text(data, "month")));
    return { isSuccess: true, message: successMessage(operation), nextPath: workspaceHref(archivedListPath, archivedListScope) };
  }
  const response = result.data;
  if (operation === "createCustomer") {
    const existing = existingCustomerMessage({ externalId: text(data, "external_id"), name: text(data, "name"), billingEmail: text(data, "billing_email") }, response);
    if (existing) return { isConfirmedFailure: true, message: existing };
  }
  if (response?.status === "failed") return { isConfirmedFailure: true, message: String(response.failure_message ?? "요청이 거절됐습니다. 실패 사유를 확인하세요.") };
  if (response?.status === "pending") return { isUnconfirmed: true, message: "요청을 접수했습니다. 결과를 확인 중이므로 다시 청구하지 말고 상태를 새로고침하세요." };
  return {
    isSuccess: true, message: operation === "testWebhookEndpoint" ? "시험 통보를 발송 대기에 추가했습니다. 발송 내역에서 결과를 확인하세요." : successMessage(operation),
    secret: typeof response?.secret === "string" ? response.secret : undefined,
    registrationPath: typeof response?.token === "string" ? `${operation === "createOrganizationInvitation" ? "/organization-invitations/" : "/payment-method-registrations/"}${encodeURIComponent(response.token)}` : undefined,
    registrationLabel: operation === "createOrganizationInvitation" ? "초대 링크" : undefined,
  };
}
