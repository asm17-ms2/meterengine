export type AccessScope = "read" | "ingest" | "admin" | "payments";
export type OrganizationRole = "owner" | "admin" | "member";

export const ORGANIZATION_ROLE_LABELS: Record<OrganizationRole, string> = { owner: "대표관리자", admin: "관리자", member: "일반 사용자" };

export const ACCESS_SCOPE_OPTIONS = [
  { value: "read", label: "조회 (read): 고객, 사용량과 청구 내역" },
  { value: "ingest", label: "수집 (ingest): 고객 등록과 사용량 이벤트 전송" },
  { value: "admin", label: "전체 관리 (admin): 조회와 수집을 포함한 데이터 변경. 결제 요청과 취소는 빠져요" },
  { value: "payments", label: "결제 (payments): 등록 카드로 결제 요청과 결제 취소" },
];

export const ACCESS_SCOPE_LABELS: Record<AccessScope, string> = { read: "조회", ingest: "수집", admin: "전체 관리", payments: "결제" };

export function scopeLabels(scopes: readonly string[]): string[] {
  const known = (["read", "ingest", "admin", "payments"] as const).filter(scope => scopes.includes(scope)).map(scope => ACCESS_SCOPE_LABELS[scope]);
  return [...known, ...scopes.filter(scope => !isAccessScope(scope))];
}

export function isAccessScope(scope: unknown): scope is AccessScope {
  return scope === "read" || scope === "ingest" || scope === "admin" || scope === "payments";
}

const OWNER_OPERATION_NAMES = ["createMember", "updateMemberRole", "updateMemberScopes", "archiveMember", "createOrganizationInvitation", "revokeOrganizationInvitation"] as const;
const ADMIN_OPERATION_NAMES = [
  "updateCustomer", "deleteCustomer", "restoreCustomer", "archivePaymentMethod",
  "archiveTossPaymentsCredential", "restoreBillableMetric", "restorePriceRate", "restorePricePlan",
  "restoreWebhookEndpoint", "createApiKey", "updateApiKeyScopes", "deleteApiKey", "createWebhookEndpoint",
  "deleteWebhookEndpoint", "updateWebhookEndpoint", "testWebhookEndpoint", "retryWebhookDelivery",
  "createPricePlan", "deletePricePlan", "createPriceRate", "deletePriceRate", "updateSupplier",
  "updatePaymentSetting", "updateTossPaymentsCredential", "createPaymentMethodRegistration",
  "finalizeInvoices", "createLiveEnvironmentApplication",
] as const;
const PAYMENT_OPERATION_NAMES = ["createPaymentAttempt", "createPaymentCancellation"] as const;
export const WORKSPACE_OPERATIONS: readonly string[] = [...OWNER_OPERATION_NAMES, ...ADMIN_OPERATION_NAMES, ...PAYMENT_OPERATION_NAMES, "createCustomer"];
export type WorkspaceOperation = (typeof OWNER_OPERATION_NAMES)[number] | (typeof ADMIN_OPERATION_NAMES)[number] | (typeof PAYMENT_OPERATION_NAMES)[number] | "createCustomer";
const OWNER_OPERATIONS = new Set<string>(OWNER_OPERATION_NAMES);
const ADMIN_OPERATIONS = new Set<string>(ADMIN_OPERATION_NAMES);
const PAYMENT_OPERATIONS = new Set<string>(PAYMENT_OPERATION_NAMES);

export function isWorkspaceOperation(value: unknown): value is WorkspaceOperation {
  return typeof value === "string" && WORKSPACE_OPERATIONS.includes(value);
}

export function hasScope(scopes: readonly string[] | null | undefined, scope: AccessScope): boolean {
  return isAccessScope(scope) && Boolean(scopes?.includes(scope) || (scope !== "payments" && scopes?.includes("admin")));
}

export function canPerformWorkspaceOperation(scopes: readonly string[] | null | undefined, role: string | null | undefined, operation: string): boolean {
  if (OWNER_OPERATIONS.has(operation)) return role === "owner" && hasScope(scopes, "admin");
  if (operation === "createCustomer") return hasScope(scopes, "ingest");
  if (PAYMENT_OPERATIONS.has(operation)) return hasScope(scopes, "payments");
  return ADMIN_OPERATIONS.has(operation) && hasScope(scopes, "admin");
}

export function tenantRequestScope(path: string, method: string = "GET"): AccessScope {
  if (method === "GET") return "read";
  const pathname = path.split("?")[0];
  if (method === "POST" && ["/v1/customers", "/v1/events", "/v1/event-batches"].includes(pathname)) return "ingest";
  if (method === "POST" && /^\/v1\/(invoices\/[^/]+\/payment-attempts|payment-attempts\/[^/]+\/cancellations)$/.test(pathname)) return "payments";
  return "admin";
}

export function scopeSummary(scopes: readonly string[] | null | undefined): string {
  const labels = (hasScope(scopes, "admin") ? [ACCESS_SCOPE_LABELS.admin] : [hasScope(scopes, "read") ? ACCESS_SCOPE_LABELS.read : "", hasScope(scopes, "ingest") ? ACCESS_SCOPE_LABELS.ingest : ""])
    .concat(hasScope(scopes, "payments") ? [ACCESS_SCOPE_LABELS.payments] : []).filter(Boolean);
  return labels.length ? labels.join(", ") : "권한 없음";
}
