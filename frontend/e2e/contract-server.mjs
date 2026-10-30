import { createServer } from "node:http";

const organizations = [
  { id: "11111111-1111-4111-8111-111111111111", name: "계약 테스트 도입사", status: "active" },
  { id: "22222222-2222-4222-8222-222222222222", name: "다른 도입사", status: "active" },
  { id: "33333333-3333-4333-8333-333333333333", name: "정지된 도입사", status: "suspended" },
];
let sessions = new Map();
let requests = [];
let outage = false;
let customers = [];
let failures = {};
let bulk = false;
let apiKeys = [];
let unresolvedInvoicePayments = [];
const WEBHOOK_ENDPOINT_ID = "99999999-9999-4999-8999-999999999999";
const HEALTHY_WEBHOOK_ENDPOINT_ID = "99999999-9999-4999-8999-999999999998";
const bulkId = (prefix, index) => `${prefix}${String(index).padStart(12, "0")}`;
const expiresAt = () => new Date(Date.now() + 3_600_000).toISOString();
const minutesAgo = minutes => new Date(Date.now() - minutes * 60_000).toISOString();
const deliveryPage = (url, deliveries) => ({ status: 200, body: { total: deliveries.length, deliveries: deliveries.slice(0, Number(url.searchParams.get("size") ?? 20)) } });
const failure = (status, code, message) => ({ status, body: { code, message } });

const server = createServer(async (req, res) => {
  let raw = "";
  for await (const chunk of req) raw += chunk;
  const body = raw ? JSON.parse(raw) : {};
  const url = new URL(req.url, "http://127.0.0.1");
  const path = url.pathname;
  const token = req.headers.authorization?.replace(/^Bearer /, "");
  const session = sessions.get(token);
  let result;
  if (path === "/health") result = { status: 200, body: {} };
  else if (path === "/__control" && req.method === "POST") {
    if (body.reset) { sessions = new Map(); requests = []; outage = false; customers = []; failures = {}; bulk = false; apiKeys = []; }
    if (typeof body.bulk === "boolean") bulk = body.bulk;
    if (body.reset) unresolvedInvoicePayments = [];
    if (Array.isArray(body.unresolvedInvoicePayments)) unresolvedInvoicePayments = body.unresolvedInvoicePayments;
    if (body.fail) failures = { ...failures, ...body.fail };
    if (body.revoke) sessions.delete(body.revoke);
    if (body.demote) { const entry = sessions.get(body.demote); if (entry) entry.user_account.role = "member"; }
    if (body.suspend) { const entry = sessions.get(body.suspend); if (entry) entry.organization = organizations[2]; }
    if (typeof body.outage === "boolean") outage = body.outage;
    result = { status: 200, body: {} };
  } else if (path === "/__requests") result = { status: 200, body: requests };
  else {
    requests.push({ path: req.url, method: req.method, hasAuthorization: Boolean(req.headers.authorization), hasOrganizationHeader: Boolean(req.headers["x-organization-id"]), consents: body.consents, description: body.description, clientIp: req.headers["x-meterengine-client-ip"] });
    if (outage) result = failure(503, "service_unavailable", "서버를 잠시 사용할 수 없습니다.");
    else if (failures[path]) result = failure(failures[path], failures[path] === 403 ? "permission_denied" : "internal_error", failures[path] === 403 ? "접근 권한이 없습니다." : "서버에서 요청을 처리하지 못했습니다.");
    else if (path === "/v1/signups" && req.method === "POST") result = { status: 201, body: { organization: { ...organizations[0], status: "pending" }, user_account: { id: "member", email: body.email, name: body.name, role: "member" } } };
    else if (path === "/v1/sessions" && req.method === "POST") {
      if (body.email === "pending@contract.local") result = failure(403, "organization_not_active", "도입사가 활성 상태가 아닙니다.");
      else if (!(body.email === "invited@contract.local" && body.password === "invited-password") && (!["member@contract.local", "operator@contract.local"].includes(body.email) || body.password !== "contract-password")) result = failure(401, "invalid_credentials", "인증 정보가 올바르지 않습니다.");
      else {
        const role = body.email.startsWith("operator") ? "operator" : "member";
        const created = { user_account: { id: "44444444-4444-4444-8444-444444444444", email: body.email, name: role === "operator" ? "테스트 운영자" : "테스트 사용자", role }, organization: role === "operator" ? null : organizations[0], organization_role: role === "operator" ? null : "owner", scopes: role === "operator" ? [] : ["admin"], organizations: role === "operator" ? [] : organizations, expires_at: expiresAt() };
        const issued = `contract-${Date.now()}-${sessions.size}`;
        sessions.set(issued, created);
        result = { status: 201, body: { token: issued, user_account: created.user_account, organization: created.organization, organization_role: created.organization_role, scopes: created.scopes, expires_at: created.expires_at } };
      }
    } else if (/^\/v1\/organization-invitations\/me_invite_[A-Za-z0-9]{32}$/.test(path) && req.method === "GET") result = { status: 200, body: { organization_name: organizations[0].name, email: path.includes("Existing") ? "existing@contract.local" : "invited@contract.local", expires_at: expiresAt(), account_exists: path.includes("Existing"), scopes: ["read"] } };
    else if (/^\/v1\/organization-invitations\/me_invite_[A-Za-z0-9]{32}\/accept$/.test(path) && req.method === "POST") {
      if (req.headers.authorization && !session) result = failure(401, "invalid_credentials", "인증 정보가 올바르지 않습니다.");
      else result = { status: 201, body: { user_account_id: "88888888-8888-4888-8888-888888888888", organization_id: organizations[0].id } };
    } else if (!session) result = failure(401, "invalid_credentials", "인증 정보가 올바르지 않습니다.");
    else if (path === "/v1/sessions/current" && req.method === "GET") result = { status: 200, body: session };
    else if (path === "/v1/sessions/current" && req.method === "DELETE") { sessions.delete(token); result = { status: 204 }; }
    else if (path === "/v1/sessions/current/organization" && req.method === "PUT") {
      const org = session.organizations.find(item => item.id === body.organization_id);
      if (!org) result = failure(403, "permission_denied", "접근 권한이 없습니다.");
      else { session.organization = org; result = { status: 200, body: session }; }
    } else if (path.startsWith("/v1/organizations") || path.startsWith("/v1/user-accounts")) {
      if (session.user_account.role !== "operator") result = failure(403, "permission_denied", "접근 권한이 없습니다.");
      else if (path === "/v1/organizations") result = { status: 200, body: { organizations: bulk ? Array.from({ length: 30 }, (_, index) => ({ id: bulkId("66666666-6666-4666-8666-", index), name: `대량 도입사 ${index}`, status: "active", created_at: new Date().toISOString() })) : [] } };
      else if (path === "/v1/user-accounts") result = { status: 200, body: { user_accounts: [{ ...session.user_account, organizations: [], created_at: new Date().toISOString() }, { id: "55555555-5555-4555-8555-555555555555", email: "other@contract.local", name: "다른 사용자", role: "member", organizations: [], created_at: new Date().toISOString() }].concat(bulk ? Array.from({ length: 30 }, (_, index) => ({ id: bulkId("77777777-7777-4777-8777-", index), email: `bulk-${index}@contract.local`, name: `대량 사용자 ${index}`, role: "member", organizations: [], created_at: new Date().toISOString() })) : []) } };
      else result = { status: 200, body: {} };
    } else if (!session.organization || session.organization.status !== "active") result = failure(403, "organization_not_active", "도입사가 활성 상태가 아닙니다.");
    else if (path === "/v1/customers/search") {
      const q = (url.searchParams.get("q") ?? "").toLowerCase();
      const filtered = customers.filter(customer => !q || `${customer.name} ${customer.external_id ?? ""} ${customer.billing_email ?? ""}`.toLowerCase().includes(q));
      const page = Math.max(1, Number(url.searchParams.get("page") ?? 1));
      const size = Number(url.searchParams.get("size") ?? 25);
      result = { status: 200, body: { items: filtered.slice((page - 1) * size, page * size), total: filtered.length, total_unfiltered: customers.length, page, size, summary: { customer_count: filtered.length } } };
    } else if (path === "/v1/customers/options") result = { status: 200, body: { items: customers.map(customer => ({ value: customer.id, label: customer.name, archived: false })), total: customers.length, total_unfiltered: customers.length, page: 1, size: 200, summary: {} } };
    else if (path === "/v1/billable-metrics/search" || path === "/v1/billable-metrics/options") result = { status: 200, body: { items: [], total: 0, total_unfiltered: 0, page: 1, size: 20, summary: {} } };
    else if (path === "/v1/customers") {
      if (req.method === "POST") { const customer = { id: crypto.randomUUID(), name: body.name, external_id: body.external_id ?? null, version: 0, billing_email: body.billing_email, created_at: new Date().toISOString() }; customers.push(customer); result = { status: 201, body: customer }; }
      else result = { status: 200, body: { customers } };
    } else if (path === "/v1/dashboard") result = { status: 200, body: { month: url.searchParams.get("month") ?? "2026-10", calculated_at: new Date().toISOString(), customer_count: customers.length, billable_metric_count: 0, active_api_key_count: 0, event_count: 0, supplier_configured: false, payment_configured: false, payment_mode: "test", draft_supply_amount: 0, finalized_total_amount: 0, collected_amount: 0, canceled_amount: 0, outstanding_amount: 0, failed_payment_count: 0, unpriced_usage_count: 0, negative_usage_count: 0, invoice_payments: [], unresolved_invoice_payments: unresolvedInvoicePayments, draft_invoice: { customers: [] } } };
    else if (path === "/v1/billable-metrics") result = { status: 200, body: { billable_metrics: [] } };
    else if (path === "/v1/organization/invitations" && req.method === "POST") result = { status: 201, body: { id: crypto.randomUUID(), email: body.email, scopes: body.scopes, token: "me_invite_ContractIssued000000000000000001" } };
    else if (path === "/v1/api-keys" && req.method === "GET") result = { status: 200, body: { api_keys: apiKeys } };
    else if (path === "/v1/api-keys" && req.method === "POST") {
      const key = { id: crypto.randomUUID(), label: body.label, mode: body.mode, scopes: body.scopes, last_four: "0000", expires_at: null, revoked_at: null, rate_limit_per_minute: body.rate_limit_per_minute ?? null, status: "active" };
      apiKeys.push(key);
      result = { status: 201, body: { ...key, secret: "me_live_ContractSecretValue0000000000" } };
    }
    else if (path === "/v1/webhook-endpoints" && req.method === "GET") result = { status: 200, body: { endpoints: [
      { id: WEBHOOK_ENDPOINT_ID, url: "https://hooks.contract.local/meterengine", event_types: ["payment_failed", "webhook_test"], enabled: true, created_at: new Date().toISOString(), archived_at: null, latest_attempt_failed: true },
      { id: HEALTHY_WEBHOOK_ENDPOINT_ID, url: "https://hooks.contract.local/healthy", event_types: ["payment_succeeded"], enabled: true, created_at: new Date().toISOString(), archived_at: null, latest_attempt_failed: false },
    ] } };
    else if (path === `/v1/webhook-endpoints/${HEALTHY_WEBHOOK_ENDPOINT_ID}/deliveries` && req.method === "GET") result = deliveryPage(url, [
      { id: "99999999-9999-4999-8999-0000000000b0", status: "pending", type: "payment_succeeded", attempt_count: 1, response_status: null, failure_reason: null, next_attempt_at: expiresAt(), created_at: new Date().toISOString(), attempts: [] },
      { id: "99999999-9999-4999-8999-0000000000b1", status: "done", type: "payment_succeeded", attempt_count: 1, response_status: 200, failure_reason: null, next_attempt_at: null, created_at: new Date().toISOString(), attempts: [{ attempted_at: minutesAgo(2), response_status: 200, failure_reason: null, duration_ms: 30 }] },
    ]);
    else if (path === "/v1/audit-logs" && req.method === "GET") result = { status: 200, body: { page: 0, size: 20, total: 12, audit_logs: Array.from({ length: 12 }, (_, index) => ({ id: bulkId("aaaaaaaa-aaaa-4aaa-8aaa-", index), occurred_at: new Date(Date.UTC(2026, 9, 1, 0, index)).toISOString(), organization_id: organizations[0].id, actor_type: "user_account", actor_id: "44444444-4444-4444-8444-444444444444", action: "api_request_completed", target_type: "http_request", target_id: "", detail: { method: "GET", route: "/v1/customers", status: index === 0 ? 403 : 200, duration_ms: 3 } })) } };
    else if (path === `/v1/webhook-endpoints/${WEBHOOK_ENDPOINT_ID}` && req.method === "GET") result = { status: 200, body: { id: WEBHOOK_ENDPOINT_ID, url: "https://hooks.contract.local/meterengine", event_types: ["payment_failed", "webhook_test"], enabled: true, description: "운영 알림 수신기", created_at: new Date().toISOString(), archived_at: null } };
    else if (path === `/v1/webhook-endpoints/${WEBHOOK_ENDPOINT_ID}` && req.method === "PUT") result = { status: 200, body: { id: WEBHOOK_ENDPOINT_ID, url: body.url, event_types: body.event_types, enabled: body.enabled, description: body.description ?? null, created_at: new Date().toISOString(), archived_at: null } };
    else if (path === `/v1/webhook-endpoints/${WEBHOOK_ENDPOINT_ID}/deliveries` && req.method === "GET") result = deliveryPage(url, [
      { id: "99999999-9999-4999-8999-0000000000a0", status: "pending", type: "payment_succeeded", attempt_count: 0, response_status: null, failure_reason: null, next_attempt_at: new Date().toISOString(), created_at: new Date().toISOString(), attempts: [] },
      { id: "99999999-9999-4999-8999-0000000000a1", status: "pending", type: "payment_failed", attempt_count: 3, response_status: null, failure_reason: "connection_error", next_attempt_at: expiresAt(), created_at: new Date().toISOString(), attempts: [minutesAgo(30), minutesAgo(10), minutesAgo(1)].map(attempted_at => ({ attempted_at, response_status: null, failure_reason: "connection_error", duration_ms: 12 })) },
      { id: "99999999-9999-4999-8999-0000000000a2", status: "failed", type: "webhook_test", attempt_count: 1, response_status: null, failure_reason: "blocked_address", next_attempt_at: null, created_at: new Date().toISOString(), attempts: [{ attempted_at: minutesAgo(40), response_status: null, failure_reason: "blocked_address", duration_ms: 1 }] },
    ]);
    else result = failure(404, "not_found", "계약 테스트에서 다루지 않는 경로입니다.");
  }
  res.writeHead(result.status, { "Content-Type": "application/json" });
  res.end(result.body === undefined ? undefined : JSON.stringify(result.body));
});
server.listen(38148, "127.0.0.1");
for (const signal of ["SIGINT", "SIGTERM"]) process.on(signal, () => server.close());
