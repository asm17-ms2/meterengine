import { randomUUID } from "node:crypto";
import { createServer } from "node:http";

const INVOICE_ID = "11111111-1111-4111-8111-111111111111";
const ATTEMPT_ID = "22222222-2222-4222-8222-222222222222";
const ENDPOINT_ID = "33333333-3333-4333-8333-333333333333";
const DELIVERY_ID = "44444444-4444-4444-8444-444444444444";
const ZERO_CUSTOMER_ID = "12121212-1212-4212-8212-121212121212";
const NOW = "2026-10-26T12:00:00+09:00";
const organization = { id: "55555555-5555-4555-8555-555555555555", name: "워크스페이스 계약 테스트", status: "active" };
const otherOrganization = { id: "88888888-8888-4888-8888-888888888888", name: "전환된 도입사 B", status: "active" };
const session = {
  user_account: { id: "66666666-6666-4666-8666-666666666666", email: "workspace@contract.local", name: "계약 테스트 사용자", role: "member" },
  organization,
  organization_role: "owner",
  scopes: ["admin"],
  organizations: [organization],
  expires_at: "2099-01-01T00:00:00Z",
};
const invoice = { id: INVOICE_ID, customer_id: "77777777-7777-4777-8777-777777777777", customer_name: "계약 고객", month: "2026-10", supply_amount: 10_000, tax_amount: 1_000, total_amount: 11_000, finalized_at: NOW, lines: [] };
const attempt = { id: ATTEMPT_ID, invoice_id: INVOICE_ID, status: "done", amount: 11_000, mode: "test", failure_message: null, requested_at: NOW };
const delay = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds));
const initialState = () => ({
  scenario: "normal", cancellationCalls: [], cancellations: [], cancellationEffects: 0,
  cancellationCompletedAfterMs: null, idempotentResults: new Map(), lostResponseSent: false, rejectionSent: false,
  retryCalls: 0, deliveryStatus: "failed", keyCalls: [], apiKeys: [],
  activeOrganization: organization, initialSessionReturned: false, customerSearchReturned: false, customerQueries: [], invoiceListCalls: [], invoiceSearchCalls: 0,
});
let state = initialState();

function send(response, status, body) {
  response.writeHead(status, body === undefined ? {} : { "Content-Type": "application/json" });
  response.end(body === undefined ? undefined : JSON.stringify(body));
}

function error(response, status, code, message) { send(response, status, { code, message }); }

const server = createServer(async (request, response) => {
  try {
    let raw = "";
    for await (const chunk of request) raw += chunk;
    const body = raw ? JSON.parse(raw) : {};
    const path = new URL(request.url, "http://127.0.0.1").pathname;
    const current = state;
    if (path === "/health") return send(response, 200, { kind: "isolated-workspace-contract" });
    if (path === "/__control" && request.method === "POST") {
      if (body.reset) state = initialState();
      if (typeof body.scenario === "string") state.scenario = body.scenario;
      return send(response, 200, {});
    }
    if (path === "/__state") return send(response, 200, {
      kind: "isolated-workspace-contract",
      cancellationCalls: current.cancellationCalls,
      cancellationEffects: current.cancellationEffects,
      cancellationCompletedAfterMs: current.cancellationCompletedAfterMs,
      cancellationCount: current.cancellations.length,
      retryCalls: current.retryCalls,
      deliveryStatus: current.deliveryStatus,
      keyCalls: current.keyCalls,
      keyCount: current.apiKeys.length,
      activeOrganizationId: current.activeOrganization.id,
      initialSessionReturned: current.initialSessionReturned,
      customerQueries: current.customerQueries,
      invoiceListCalls: current.invoiceListCalls,
      invoiceSearchCalls: current.invoiceSearchCalls,
    });
    if (request.headers.authorization !== "Bearer workspace-contract-session") return error(response, 401, "invalid_credentials", "로그인이 필요합니다.");
    if (path === "/v1/sessions/current") {
      if (!["organization-switch-after-session", "organization-switch-after-search"].includes(current.scenario)) return send(response, 200, session);
      const snapshot = { ...session, organization: current.activeOrganization, organizations: [organization, otherOrganization] };
      if (!current.initialSessionReturned) {
        current.initialSessionReturned = true;
        if (current.scenario === "organization-switch-after-session") current.activeOrganization = otherOrganization;
      }
      return send(response, 200, snapshot);
    }
    if (path === "/v1/customers/search") {
      const expectedOrganizationId = request.headers["x-meterengine-organization-id"] ?? null;
      current.customerQueries.push({ expectedOrganizationId, activeOrganizationId: current.activeOrganization.id });
      if (expectedOrganizationId && expectedOrganizationId !== current.activeOrganization.id) return error(response, 403, "permission_denied", "도입사 선택이 바뀌어 조회를 중단했습니다.");
      if (current.scenario === "organization-switch-after-search" && !current.customerSearchReturned) {
        current.customerSearchReturned = true;
        const items = Array.from({ length: 20 }, (_, index) => ({ id: `90000000-0000-4000-8000-${String(index).padStart(12, "0")}`, name: `처음 도입사 A 고객 ${index}`, external_id: null, billing_email: null, archived_at: null, created_at: NOW, version: 0 }));
        current.activeOrganization = otherOrganization;
        return send(response, 200, { items, total: 21, total_unfiltered: 21, page: 1, size: 20, summary: {} });
      }
      const items = ["organization-switch-after-session", "organization-switch-after-search"].includes(current.scenario) ? [{ id: "99999999-9999-4999-8999-999999999999", name: "다른 도입사 B의 비공개 고객", external_id: "organization-b-private", billing_email: null, archived_at: null, created_at: NOW, version: 0 }] : [];
      return send(response, 200, { items, total: items.length, total_unfiltered: items.length, page: 1, size: 20, summary: {} });
    }
    if (current.scenario === "finalized-month-zero-customer") {
      const url = new URL(request.url, "http://127.0.0.1");
      if (path === "/v1/customers/options") return send(response, 200, { items: [{ value: ZERO_CUSTOMER_ID, label: "인보이스 없는 0원 고객", archived: false }], total: 1, total_unfiltered: 1, page: 1, size: 200, summary: {} });
      if (path === "/v1/usage/months") return send(response, 200, { months: ["2026-10"] });
      if (path === "/v1/invoices/draft/search") return send(response, 200, {
        items: [{ customer_id: ZERO_CUSTOMER_ID, customer_name: "인보이스 없는 0원 고객", amount: 0, lines: [], unpriced_usages: [], negative_usages: [] }],
        total: 1, total_unfiltered: 1, page: 1, size: 20,
        summary: { month: "2026-10", calculated_at: NOW, amount: 0, customer_count: 1, billable_customer_count: 0, line_count: 0, unpriced_count: 0, negative_count: 0, month_amount: 0, month_customer_count: 1, month_billable_customer_count: 0, month_unpriced_count: 0, month_negative_count: 0, month_line_count: 0, finalization_scope: "month" },
      });
      if (path === "/v1/invoices/search") {
        current.invoiceSearchCalls++;
        return error(response, 503, "wrong_contract", "달 전체 조회에는 페이지 없는 인보이스 계약을 사용하세요.");
      }
      if (path === "/v1/invoices") {
        current.invoiceListCalls.push(Object.fromEntries(url.searchParams));
        const selectedCustomer = url.searchParams.get("customer_id");
        const invoices = selectedCustomer === ZERO_CUSTOMER_ID ? [] : Array.from({ length: 101 }, (_, index) => ({ ...invoice, id: `13131313-1313-4313-8313-${String(index).padStart(12, "0")}`, customer_name: `다른 고객의 확정 청구 ${index}` }));
        return send(response, 200, { invoices });
      }
    }
    if (path === "/v1/invoice-payments") {
      const canceledAmount = current.cancellations.filter(cancellation => cancellation.status === "done").reduce((total, cancellation) => total + cancellation.amount, 0);
      return send(response, 200, { invoice_payments: [{ invoice_id: INVOICE_ID, status: canceledAmount === attempt.amount ? "canceled" : canceledAmount > 0 ? "partially_canceled" : "paid", mode: "test", amount: attempt.amount, canceled_amount: canceledAmount, attempt_count: 1, last_failure_code: null, next_retry_at: null, completed_at: NOW }] });
    }
    if (path === `/v1/invoices/${INVOICE_ID}`) return send(response, 200, invoice);
    if (path === `/v1/invoices/${INVOICE_ID}/payment-attempts`) return send(response, 200, { payment_attempts: [attempt] });
    if (path === `/v1/payment-attempts/${ATTEMPT_ID}/cancellations`) {
      if (request.method === "GET") return send(response, 200, { payment_cancellations: current.cancellations });
      const startedAt = Date.now();
      const idempotencyKey = request.headers["idempotency-key"] ?? null;
      current.cancellationCalls.push({ idempotencyKey, amount: body.amount, reason: body.reason });
      if (!idempotencyKey) return error(response, 400, "validation_error", "취소 요청 ID가 필요합니다.");
      const original = current.idempotentResults.get(idempotencyKey);
      if (original) {
        if (original.amount !== body.amount || original.reason !== body.reason) return error(response, 409, "idempotency_key_conflict", "같은 요청 ID의 금액과 사유를 바꿀 수 없습니다.");
        return send(response, 201, original.result);
      }
      if (current.scenario === "rejected-cancellation-first" && !current.rejectionSent) {
        current.rejectionSent = true;
        const result = { id: randomUUID(), payment_attempt_id: ATTEMPT_ID, mode: "test", status: "failed", amount: body.amount, reason: body.reason, failure_message: "수신 서버가 취소를 거절했습니다.", requested_at: NOW, completed_at: NOW };
        current.cancellations.push(result);
        current.idempotentResults.set(idempotencyKey, { amount: body.amount, reason: body.reason, result });
        return send(response, 201, result);
      }
      if (current.scenario === "delayed-cancellation") await delay(6_000);
      const result = { id: randomUUID(), payment_attempt_id: ATTEMPT_ID, mode: "test", status: "done", amount: body.amount ?? 11_000, reason: body.reason, requested_at: NOW, completed_at: NOW };
      current.cancellationEffects++;
      current.cancellations.push(result);
      current.idempotentResults.set(idempotencyKey, { amount: body.amount, reason: body.reason, result });
      current.cancellationCompletedAfterMs = Date.now() - startedAt;
      if (current.scenario === "lost-cancellation-response" && !current.lostResponseSent) {
        current.lostResponseSent = true;
        response.destroy();
        return;
      }
      return send(response, 201, result);
    }
    if (path === "/v1/api-keys") {
      if (request.method === "GET") return send(response, 200, { api_keys: current.apiKeys });
      current.keyCalls.push({ label: body.label, rateLimitPerMinute: body.rate_limit_per_minute });
      if (body.rate_limit_per_minute !== null && (!Number.isInteger(body.rate_limit_per_minute) || body.rate_limit_per_minute <= 0)) return error(response, 400, "validation_error", "분당 요청 한도는 양수여야 합니다.");
      if (current.scenario === "delayed-key") await delay(3_000);
      const key = { id: randomUUID(), label: body.label, mode: body.mode, scopes: body.scopes, last_four: "test", expires_at: null, revoked_at: null, rate_limit_per_minute: body.rate_limit_per_minute };
      current.apiKeys.push(key);
      return send(response, 201, { ...key, secret: "me_test_disposable_contract_value" });
    }
    if (path === "/v1/webhook-endpoints") return send(response, 200, { endpoints: [] });
    if (path === `/v1/webhook-endpoints/${ENDPOINT_ID}`) return send(response, 200, { id: ENDPOINT_ID, url: "https://receiver.contract.local/webhook", enabled: true, event_types: ["payment_succeeded"], description: null, archived_at: null });
    if (path === `/v1/webhook-endpoints/${ENDPOINT_ID}/deliveries`) return send(response, 200, {
      page: 0, size: 20, total: 1,
      deliveries: [{ id: DELIVERY_ID, type: "payment_succeeded", status: current.deliveryStatus, attempt_count: current.deliveryStatus === "failed" ? 6 : 0, response_status: 503, failure_reason: current.deliveryStatus === "failed" ? "receiver_unavailable" : null, next_attempt_at: NOW }],
    });
    if (path === `/v1/webhook-endpoints/${ENDPOINT_ID}/deliveries/${DELIVERY_ID}/retries` && request.method === "POST") {
      current.retryCalls++;
      current.deliveryStatus = "pending";
      return send(response, 202);
    }
    return error(response, 404, "not_found", "계약 테스트가 다루지 않는 경로입니다.");
  } catch {
    if (!response.destroyed && !response.headersSent) error(response, 500, "fixture_error", "계약 서버의 요청 처리를 확인하세요.");
  }
});

server.listen(38150, "127.0.0.1");
for (const signal of ["SIGINT", "SIGTERM"]) process.on(signal, () => server.close());
