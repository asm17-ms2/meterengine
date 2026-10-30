export type AccessScope = "read" | "ingest" | "admin";
export type OrganizationRole = "owner" | "admin" | "member";

export const ORGANIZATION_ROLE_LABELS: Record<OrganizationRole, string> = { owner: "대표관리자", admin: "관리자", member: "일반 사용자" };

export const ACCESS_SCOPE_OPTIONS = [
  { value: "read", label: "조회 (read): 고객, 사용량과 청구 내역" },
  { value: "ingest", label: "수집 (ingest): 고객 등록과 사용량 이벤트 전송" },
  { value: "admin", label: "전체 관리 (admin): 조회와 수집을 포함한 데이터 변경" },
];

export const ACCESS_SCOPE_LABELS: Record<AccessScope, string> = { read: "조회", ingest: "수집", admin: "전체 관리" };

export function scopeLabels(scopes: readonly string[]): string[] {
  const known = (["read", "ingest", "admin"] as const).filter(scope => scopes.includes(scope)).map(scope => ACCESS_SCOPE_LABELS[scope]);
  return [...known, ...scopes.filter(scope => !isAccessScope(scope))];
}

export function isAccessScope(scope: unknown): scope is AccessScope {
  return scope === "read" || scope === "ingest" || scope === "admin";
}

const OWNER_OPERATION_NAMES = ["createMember", "updateMemberRole", "updateMemberScopes", "archiveMember", "createOrganizationInvitation", "revokeOrganizationInvitation"] as const;
const ADMIN_OPERATION_NAMES = [
  "updateCustomer", "deleteCustomer", "restoreCustomer", "archivePaymentMethod",
  "archiveTossPaymentsCredential", "restoreBillableMetric", "restorePriceRate", "restorePricePlan",
  "restoreWebhookEndpoint", "createApiKey", "updateApiKeyScopes", "deleteApiKey", "createWebhookEndpoint",
  "deleteWebhookEndpoint", "updateWebhookEndpoint", "testWebhookEndpoint", "retryWebhookDelivery",
  "createPricePlan", "deletePricePlan", "createPriceRate", "deletePriceRate", "updateSupplier",
  "updatePaymentSetting", "updateTossPaymentsCredential", "createPaymentMethodRegistration",
  "createPaymentAttempt", "createPaymentCancellation", "finalizeInvoices",
] as const;
export const WORKSPACE_OPERATIONS: readonly string[] = [...OWNER_OPERATION_NAMES, ...ADMIN_OPERATION_NAMES, "createCustomer"];
export type WorkspaceOperation = (typeof OWNER_OPERATION_NAMES)[number] | (typeof ADMIN_OPERATION_NAMES)[number] | "createCustomer";
const OWNER_OPERATIONS = new Set<string>(OWNER_OPERATION_NAMES);
const ADMIN_OPERATIONS = new Set<string>(ADMIN_OPERATION_NAMES);

export function isWorkspaceOperation(value: unknown): value is WorkspaceOperation {
  return typeof value === "string" && WORKSPACE_OPERATIONS.includes(value);
}

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
  if (hasScope(scopes, "admin")) return ACCESS_SCOPE_LABELS.admin;
  const labels = [hasScope(scopes, "read") ? ACCESS_SCOPE_LABELS.read : "", hasScope(scopes, "ingest") ? ACCESS_SCOPE_LABELS.ingest : ""].filter(Boolean);
  return labels.length ? labels.join(", ") : "권한 없음";
}
