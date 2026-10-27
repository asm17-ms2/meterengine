export type AccessScope = "read" | "ingest" | "admin";
export type OrganizationRole = "owner" | "admin" | "member";

export const ACCESS_SCOPE_OPTIONS = [
  { value: "read", label: "조회 (read): 고객, 사용량과 청구 내역" },
  { value: "ingest", label: "수집 (ingest): 고객 등록과 사용량 이벤트 전송" },
  { value: "admin", label: "전체 관리 (admin): 조회와 수집을 포함한 데이터 변경" },
];

export function isAccessScope(scope: unknown): scope is AccessScope {
  return scope === "read" || scope === "ingest" || scope === "admin";
}

const OWNER_OPERATIONS = new Set(["createMember", "updateMemberRole", "updateMemberScopes", "archiveMember", "createOrganizationInvitation", "revokeOrganizationInvitation"]);
const ADMIN_OPERATIONS = new Set([
  "updateCustomer", "deleteCustomer", "restoreCustomer", "archivePaymentMethod",
  "archiveTossPaymentsCredential", "restoreBillableMetric", "restorePriceRate", "restorePricePlan",
  "restoreWebhookEndpoint", "createApiKey", "updateApiKeyScopes", "deleteApiKey", "createWebhookEndpoint",
  "deleteWebhookEndpoint", "updateWebhookEndpoint", "testWebhookEndpoint", "retryWebhookDelivery",
  "createPricePlan", "deletePricePlan", "createPriceRate", "deletePriceRate", "updateSupplier",
  "updatePaymentSetting", "updateTossPaymentsCredential", "createPaymentMethodRegistration",
  "createPaymentAttempt", "createPaymentCancellation", "finalizeInvoices",
]);

export function hasScope(scopes: readonly string[] | null | undefined, scope: AccessScope): boolean {
  return isAccessScope(scope) && Boolean(scopes?.includes("admin") || scopes?.includes(scope));
}

export function canPerformWorkspaceOperation(scopes: readonly string[] | null | undefined, role: string | null | undefined, operation: string): boolean {
  if (OWNER_OPERATIONS.has(operation)) return role === "owner" && hasScope(scopes, "admin");
  if (operation === "createCustomer") return hasScope(scopes, "ingest");
  return ADMIN_OPERATIONS.has(operation) && hasScope(scopes, "admin");
}

export function tenantRequestScope(path: string, method: string = "GET"): AccessScope {
  if (method === "GET") return "read";
  if (method === "POST" && ["/v1/customers", "/v1/events", "/v1/event-batches"].includes(path.split("?")[0])) return "ingest";
  return "admin";
}

export function scopeSummary(scopes: readonly string[] | null | undefined): string {
  if (hasScope(scopes, "admin")) return "전체 관리";
  const labels = [hasScope(scopes, "read") ? "조회" : "", hasScope(scopes, "ingest") ? "수집" : ""].filter(Boolean);
  return labels.length ? labels.join(", ") : "권한 없음";
}
