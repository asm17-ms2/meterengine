import { expect, test, type APIRequestContext, type BrowserContext, type Page } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { execFileSync } from "node:child_process";
import { chmodSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { CONSENT_DOCUMENTS, CONSENT_VERSION } from "../src/lib/auth/consents";

const API_URL = process.env.S1_API_URL ?? "http://127.0.0.1:38147";
const FRONTEND_URL = process.env.CUSTOMER_RETRY_FRONTEND_URL ?? "http://localhost:33147";
const PG_URL = "http://127.0.0.1:38447";
const OUTPUT = process.env.CUSTOMER_RETRY_EVIDENCE_DIR ?? "/private/tmp/meterengine-customer-retry-2026-10-27";
const PASSWORD = "local-customer-retry-e2e-password";
const OPERATOR_EMAIL = process.env.E2E_OPERATOR_EMAIL ?? "operator@fe-e2e.local";
const OPERATOR_PASSWORD = process.env.E2E_OPERATOR_PASSWORD ?? "local-operator-e2e-password";
const SECRET_GROUPS: Set<string>[] = [];
const RETRY_VERSIONS = new Set<string>();

type Fixture = {
  organizationId: string; headers: Record<string, string>; operatorHeaders: Record<string, string>;
  readHeaders: Record<string, string>; session: string; customers: Record<string, string>;
  invoices: Record<string, string>; invoiceNumbers: Record<string, string>; month: string;
};
type PublicInvoice = {
  invoice_number: string; total_amount: number;
  payment: { status: string; mode: string; amount: number; paid_amount: number | null; canceled_amount: number; outstanding_amount: number | null };
  payment_retry: { allowed: boolean; reason: string; version: string | null };
};
type ViewLink = { id: string; token: string; allow_payment_retry: boolean };

test.use({ trace: "off", screenshot: "off", video: "off" });
test.beforeAll(() => {
  mkdirSync(OUTPUT, { recursive: true, mode: 0o700 });
  chmodSync(OUTPUT, 0o700);
  for (const value of [API_URL, FRONTEND_URL, PG_URL]) {
    const url = new URL(value);
    if (url.protocol !== "http:" || !["localhost", "127.0.0.1", "[::1]"].includes(url.hostname)) throw new Error("고객 재시도 검증은 로컬 HTTP 서버에서만 실행합니다.");
  }
  const env = JSON.parse(readFileSync("/private/tmp/meterengine-orchestration-0696/backend-e2e-environment.json", "utf8"));
  if (env.TOSSPAYMENTS_BASE_URL !== PG_URL) throw new Error("결제 요청의 대상이 격리된 로컬 PG 대역이 아닙니다.");
});
test.afterAll(() => {
  const path = `${OUTPUT}/private-run-values.json`;
  writeFileSync(path, JSON.stringify({ values: [...new Set(SECRET_GROUPS.flatMap(group => [...group]))], retry_versions: [...RETRY_VERSIONS] }), { mode: 0o600 });
  chmodSync(path, 0o600);
});

function newSecrets() {
  const group = new Set([PASSWORD, OPERATOR_PASSWORD]);
  SECRET_GROUPS.push(group);
  return group;
}

function safeFailure(error: unknown, secrets: Set<string>) {
  let message = error instanceof Error ? error.message : "고객 재시도 검증을 마치지 못했습니다.";
  for (const secret of secrets) if (secret) message = message.split(secret).join("[redacted]");
  return new Error(message.replace(/me_(?:invoice|session|test|live)_[A-Za-z0-9_-]+/g, "[redacted token]"));
}
function sql(statement: string) {
  return execFileSync("docker", ["exec", "-i", "meterengine-0696-e2e", "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", "meterengine", "-d", "meterengine", "-At"], { input: statement, encoding: "utf8", stdio: ["pipe", "pipe", "pipe"] }).trim();
}
function uuid(value: string) {
  if (!/^[a-f\d-]{36}$/i.test(value)) throw new Error("검증 데이터의 UUID가 잘못됐습니다.");
  return value;
}
function result(name: string, value: unknown) { writeFileSync(`${OUTPUT}/${name}.json`, JSON.stringify(value, null, 2)); }
async function approvalCalls(request: APIRequestContext) { return Number((await (await request.get(`${PG_URL}/__state`)).json()).approval_calls); }
async function nextApproval(request: APIRequestContext, outcome: "done" | "rejected" | "response-lost") {
  expect((await request.post(`${PG_URL}/__control`, { data: { nextApproval: outcome } })).status()).toBe(200);
}

async function registerCard(request: APIRequestContext, data: Fixture, name: string, secrets: Set<string>) {
  const registration = await request.post(`${API_URL}/v1/customers/${data.customers[name]}/payment-method-registrations`, { headers: data.headers });
  expect(registration.status()).toBe(201);
  const issued = await registration.json();
  secrets.add(issued.token);
  const response = await request.post(`${API_URL}/v1/payment-method-registrations/${issued.token}/billing-keys`, { data: { auth_key: `mvp-auth-${randomUUID()}`, customer_key: data.customers[name], consent_document_version: CONSENT_VERSION } });
  expect(response.status()).toBe(201);
}

async function fixture(request: APIRequestContext, names: string[], secrets: Set<string>): Promise<Fixture> {
  const suffix = randomUUID();
  const email = `customer-retry-${suffix}@fe-e2e.local`;
  const month = new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit" }).format(new Date());
  const signup = await request.post(`${API_URL}/v1/signups`, { data: { organization_name: `고객 재시도 검증 ${suffix}`, name: "고객 재시도 담당자", email, password: PASSWORD, consents: CONSENT_DOCUMENTS.map(({ document }) => ({ document, document_version: CONSENT_VERSION })) } });
  expect(signup.status()).toBe(201);
  const organizationId = uuid((await signup.json()).organization.id);
  const operator = await request.post(`${API_URL}/v1/sessions`, { data: { email: OPERATOR_EMAIL, password: OPERATOR_PASSWORD } });
  expect(operator.status()).toBe(201);
  const operatorToken = (await operator.json()).token as string;
  secrets.add(operatorToken);
  const operatorHeaders = { Authorization: `Bearer ${operatorToken}` };
  expect((await request.put(`${API_URL}/v1/organizations/${organizationId}/status`, { headers: operatorHeaders, data: { status: "active" } })).status()).toBe(200);
  const sessionResponse = await request.post(`${API_URL}/v1/sessions`, { data: { email, password: PASSWORD } });
  expect(sessionResponse.status()).toBe(201);
  const session = (await sessionResponse.json()).token as string;
  secrets.add(session);
  const headers = { Authorization: `Bearer ${session}` };
  const readKeyResponse = await request.post(`${API_URL}/v1/api-keys`, { headers, data: { label: "고객 재시도 조회", mode: "test", scopes: ["read", "ingest"] } });
  expect(readKeyResponse.status()).toBe(201);
  const readKey = (await readKeyResponse.json()).secret as string;
  secrets.add(readKey);
  const readHeaders = { Authorization: `Bearer ${readKey}` };
  const secretKey = `test_sk_customer_retry_${suffix}`;
  secrets.add(secretKey);
  expect((await request.put(`${API_URL}/v1/toss-payments-credentials/test`, { headers, data: { client_key: `test_ck_customer_retry_${suffix}`, secret_key: secretKey } })).status()).toBe(200);
  const code = `customer-retry-${suffix}`;
  expect((await request.post(`${API_URL}/v1/billable-metrics`, { headers, data: { code, name: "모델 요청", event_type: code, aggregation: "sum", target_property: "units", dimension_properties: [] } })).status()).toBe(201);
  expect((await request.post(`${API_URL}/v1/billable-metrics/${code}/price-rates`, { headers, data: { dimension_values: {}, unit_price: 100, effective_from: month } })).status()).toBe(201);
  const data: Fixture = { organizationId, headers, operatorHeaders, readHeaders, session, customers: {}, invoices: {}, invoiceNumbers: {}, month };
  for (const name of names) {
    const response = await request.post(`${API_URL}/v1/customers`, { headers, data: { name: `재시도 고객 ${name}`, external_id: `customer-retry-${suffix}-${name}`, billing_email: "retry-private@fe-e2e.local" } });
    expect(response.status()).toBe(201);
    data.customers[name] = uuid((await response.json()).id);
    await registerCard(request, data, name, secrets);
  }
  for (const name of names) {
    expect((await request.post(`${API_URL}/v1/events`, { headers: readHeaders, data: { transaction_id: `customer-retry-${suffix}-${name}`, customer_id: data.customers[name], type: code, timestamp: new Date(Date.now() - 60_000).toISOString(), properties: { units: 10 } } })).status()).toBe(200);
  }
  expect((await request.post(`${API_URL}/v1/invoices?month=${month}`, { headers })).status()).toBe(200);
  const response = await request.get(`${API_URL}/v1/invoices?month=${month}`, { headers });
  expect(response.status()).toBe(200);
  const rows = (await response.json()).invoices as { id: string; customer_id: string; invoice_number: string; total_amount: number }[];
  for (const name of names) {
    const row = rows.find(value => value.customer_id === data.customers[name]);
    if (!row || row.total_amount !== 1100) throw new Error("고객 재시도 청구서를 만들지 못했습니다.");
    data.invoices[name] = uuid(row.id); data.invoiceNumbers[name] = row.invoice_number;
  }
  return data;
}

async function adminAttempt(request: APIRequestContext, data: Fixture, name: string, outcome: "done" | "rejected" | "response-lost", secrets: Set<string>) {
  await nextApproval(request, outcome);
  const response = await request.post(`${API_URL}/v1/invoices/${data.invoices[name]}/payment-attempts`, { headers: data.headers });
  expect(response.status()).toBe(201);
  const body = await response.json();
  if (body.payment_key) secrets.add(body.payment_key);
  expect(body.status).toBe(outcome === "rejected" ? "failed" : outcome === "response-lost" ? "pending" : "done");
  return body as { id: string; status: string };
}
function seedFailure(data: Fixture, name: string, code = "REJECT_CARD_PAYMENT", count = 1) {
  for (let i = 0; i < count; i++) sql(`INSERT INTO payment_attempt(id,organization_id,customer_id,invoice_id,mode,amount,order_name,status,failure_code,failure_message,requested_at,completed_at) VALUES('${randomUUID()}','${data.organizationId}','${data.customers[name]}','${data.invoices[name]}','test',1100,'로컬 재시도 검증','failed','${code}','로컬 PG 검증 실패',clock_timestamp()-interval '${count - i + 1} seconds',clock_timestamp());`);
}
async function mint(request: APIRequestContext, data: Fixture, name: string, secrets: Set<string>, allow?: boolean): Promise<ViewLink> {
  const response = await request.post(`${API_URL}/v1/invoices/${data.invoices[name]}/view-links`, { headers: data.headers, data: allow === undefined ? {} : { allow_payment_retry: allow } });
  expect(response.status()).toBe(201);
  const link = await response.json() as ViewLink;
  secrets.add(link.token);
  expect(link.allow_payment_retry).toBe(allow ?? false);
  return link;
}
async function lookup(request: APIRequestContext, token: string): Promise<PublicInvoice> {
  const response = await request.post(`${API_URL}/v1/public/invoice-links/lookup`, { data: { token } });
  expect(response.status()).toBe(200);
  const invoice = await response.json() as PublicInvoice;
  if (invoice.payment_retry.version) RETRY_VERSIONS.add(invoice.payment_retry.version);
  return invoice;
}
async function retry(request: APIRequestContext, link: ViewLink, version: string | null, extra: Record<string, unknown> = {}) {
  return request.post(`${API_URL}/v1/public/invoice-links/payment-retries`, { data: { token: link.token, retry_version: version, ...extra } });
}
function attempts(data: Fixture, name: string) { return Number(sql(`SELECT count(*) FROM payment_attempt WHERE organization_id='${data.organizationId}' AND invoice_id='${data.invoices[name]}' AND mode='test';`)); }
async function revoke(request: APIRequestContext, data: Fixture, name: string, link: ViewLink) { expect((await request.post(`${API_URL}/v1/invoices/${data.invoices[name]}/view-links/${link.id}/revoke`, { headers: data.headers })).status()).toBe(200); }
async function capture(page: Page, name: string) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: `${OUTPUT}/${name}.png`, fullPage: true });
}
function trackVersions(page: Page) {
  page.on("request", request => {
    if (!new URL(request.url()).pathname.endsWith("/invoice-links/payment-retries")) return;
    try {
      const body: unknown = JSON.parse(request.postData() ?? "null");
      if (body && typeof body === "object" && "retry_version" in body && typeof body.retry_version === "string") RETRY_VERSIONS.add(body.retry_version);
    } catch {}
  });
}
async function ready(page: Page, link: ViewLink) {
  await page.goto(`${FRONTEND_URL}/invoice-links#${link.token}`);
  await expect(page.getByRole("heading", { name: /청구서$/ }).first()).toBeVisible();
  await expect(page).toHaveURL(`${FRONTEND_URL}/invoice-links`);
}

test("조회 전용 권한을 보존하고 같은 실패의 중복 요청과 관리자 경합을 한 번만 결제한다", async ({ request }) => {
  const secrets = newSecrets();
  try {
    const data = await fixture(request, ["retry", "race", "other"], secrets);
    await adminAttempt(request, data, "retry", "rejected", secrets);
    await adminAttempt(request, data, "race", "rejected", secrets);
    await adminAttempt(request, data, "other", "rejected", secrets);
    const readonly = await mint(request, data, "retry", secrets);
    const allowed = await mint(request, data, "retry", secrets, true);
    const other = await mint(request, data, "other", secrets, true);
    expect((await lookup(request, readonly.token)).payment_retry.allowed).toBe(false);
    const before = await approvalCalls(request);
    expect((await retry(request, readonly, "0".repeat(64))).status()).toBe(409);
    expect((await request.post(`${API_URL}/v1/invoices/${data.invoices.retry}/view-links`, { headers: data.readHeaders, data: { allow_payment_retry: true } })).status()).toBe(403);
    expect((await request.post(`${API_URL}/v1/invoices/${data.invoices.retry}/view-links`, { data: { allow_payment_retry: true } })).status()).toBe(401);
    const invoice = await lookup(request, allowed.token);
    expect(invoice.payment_retry.allowed).toBe(true);
    expect(invoice.payment_retry.version).toMatch(/^[a-f\d]{64}$/);
    expect((await retry(request, other, invoice.payment_retry.version)).status()).toBe(409);
    const replies = await Promise.all([retry(request, allowed, invoice.payment_retry.version), retry(request, allowed, invoice.payment_retry.version)]);
    expect(replies.map(response => response.status()).sort()).toEqual([200, 409]);
    const paid = await lookup(request, allowed.token);
    expect(paid.payment).toMatchObject({ status: "paid", amount: 1100, paid_amount: 1100, outstanding_amount: 0 });
    expect(attempts(data, "retry")).toBe(2);
    expect(await approvalCalls(request) - before).toBe(1);
    expect((await retry(request, allowed, invoice.payment_retry.version)).status()).toBe(409);
    expect(await approvalCalls(request) - before).toBe(1);
    const race = await mint(request, data, "race", secrets, true);
    const raceInvoice = await lookup(request, race.token);
    const raceBefore = await approvalCalls(request);
    const contested = await Promise.all([retry(request, race, raceInvoice.payment_retry.version), request.post(`${API_URL}/v1/invoices/${data.invoices.race}/payment-attempts`, { headers: data.headers })]);
    expect(contested.filter(response => [200, 201].includes(response.status()))).toHaveLength(1);
    expect(contested.filter(response => response.status() === 409)).toHaveLength(1);
    expect(await approvalCalls(request) - raceBefore).toBe(1);
    expect(attempts(data, "race")).toBe(2);
    const audit = await request.get(`${API_URL}/v1/audit-logs?size=100`, { headers: data.headers });
    expect(audit.status()).toBe(200);
    const auditText = await audit.text();
    for (const secret of secrets) expect(auditText.includes(secret)).toBe(false);
    for (const version of RETRY_VERSIONS) expect(auditText.includes(version)).toBe(false);
    expect(Number(sql(`SELECT count(*) FROM audit_log WHERE organization_id='${data.organizationId}' AND actor_type='invoice_view_link' AND action='payment_attempt_created';`))).toBeGreaterThan(0);
    await revoke(request, data, "retry", readonly); await revoke(request, data, "retry", allowed); await revoke(request, data, "race", race); await revoke(request, data, "other", other);
    result("api-concurrency-result", { passed: true, default_read_only: true, read_key_issue_status: 403, anonymous_issue_status: 401, same_version_requests: replies.map(response => response.status()), same_version_provider_calls: 1, replay_additional_calls: 0, public_admin_race_provider_calls: 1, invoice_amount_krw: paid.payment.amount, audit_token_leak: false });
  } catch (error) { throw safeFailure(error, secrets); }
});

test("결제 상태와 카드, 모드, 만료 및 폐기 경계가 새 결제 실행을 막는다", async ({ request }) => {
  const secrets = newSecrets();
  try {
    const names = ["unpaid", "pending", "paid", "cancel", "missing", "stopped", "limit", "expired", "revoked", "mode"];
    const data = await fixture(request, names, secrets);
    sql(`INSERT INTO payment_attempt(id,organization_id,customer_id,invoice_id,mode,amount,order_name,status,requested_at) VALUES('${randomUUID()}','${data.organizationId}','${data.customers.pending}','${data.invoices.pending}','test',1100,'로컬 결과 확인 중','pending',clock_timestamp());`);
    await adminAttempt(request, data, "paid", "done", secrets);
    const canceledAttempt = await adminAttempt(request, data, "cancel", "done", secrets);
    const cancellation = await request.post(`${API_URL}/v1/payment-attempts/${canceledAttempt.id}/cancellations`, { headers: data.headers, data: { request_id: randomUUID(), reason: "고객 재시도 차단 검증" } });
    expect(cancellation.status()).toBe(201);
    for (const name of ["missing", "expired", "revoked", "mode"]) seedFailure(data, name);
    seedFailure(data, "stopped", "INVALID_STOPPED_CARD");
    seedFailure(data, "limit", "REJECT_CARD_PAYMENT", 3);
    expect((await request.delete(`${API_URL}/v1/customers/${data.customers.missing}/payment-method`, { headers: data.headers })).ok()).toBe(true);
    const links: Record<string, ViewLink> = {};
    for (const name of names) links[name] = await mint(request, data, name, secrets, true);
    const before = await approvalCalls(request);
    for (const name of ["unpaid", "pending", "paid", "cancel", "missing", "stopped", "limit"]) {
      const invoice = await lookup(request, links[name].token);
      expect(invoice.payment_retry.allowed, name).toBe(false);
      expect((await retry(request, links[name], invoice.payment_retry.version ?? "0".repeat(64))).status(), name).toBe(409);
      if (name === "cancel") expect(invoice.payment).toMatchObject({ status: "canceled", canceled_amount: 1100, outstanding_amount: 0 });
      if (name === "pending") expect(invoice.payment).toMatchObject({ paid_amount: null, outstanding_amount: null });
    }
    sql(`UPDATE invoice_view_link SET created_at=clock_timestamp()-interval '31 days',expires_at=clock_timestamp()-interval '1 second' WHERE id='${uuid(links.expired.id)}';`);
    await revoke(request, data, "revoked", links.revoked);
    for (const name of ["expired", "revoked"]) expect((await retry(request, links[name], "0".repeat(64))).status()).toBe(404);
    sql(`INSERT INTO payment_setting(organization_id,mode,updated_at) VALUES('${data.organizationId}','live',clock_timestamp()) ON CONFLICT(organization_id) DO UPDATE SET mode='live',updated_at=clock_timestamp();`);
    expect((await lookup(request, links.mode.token)).payment_retry.allowed).toBe(false);
    expect((await retry(request, links.mode, "0".repeat(64))).status()).toBe(409);
    sql(`UPDATE payment_setting SET mode='test',updated_at=clock_timestamp() WHERE organization_id='${data.organizationId}';`);
    expect(await approvalCalls(request)).toBe(before);
    await registerCard(request, data, "stopped", secrets);
    const recovered = await lookup(request, links.stopped.token);
    expect(recovered.payment_retry.allowed).toBe(true);
    expect((await retry(request, links.stopped, recovered.payment_retry.version)).status()).toBe(200);
    expect((await lookup(request, links.stopped.token)).payment.status).toBe("paid");
    expect(await approvalCalls(request) - before).toBe(1);
    expect((await request.put(`${API_URL}/v1/organizations/${data.organizationId}/status`, { headers: data.operatorHeaders, data: { status: "suspended" } })).status()).toBe(200);
    const suspendedBefore = await approvalCalls(request);
    expect((await retry(request, links.mode, "0".repeat(64))).status()).toBe(404);
    expect(await approvalCalls(request)).toBe(suspendedBefore);
    result("blocked-states-result", { passed: true, blocked_states: ["unpaid", "pending", "paid", "canceled", "missing_card", "stopped_card", "attempt_limit", "expired", "revoked", "mode_changed", "organization_suspended"], blocked_provider_calls: 0, refreshed_card_retry_succeeded: true, canceled_outstanding_krw: 0, pending_amounts_unknown: true });
  } catch (error) { throw safeFailure(error, secrets); }
});

test("실패 응답 재전송은 추가 결제를 만들지 않고 승인 응답 유실은 pending으로 남긴다", async ({ request }) => {
  const secrets = newSecrets();
  try {
    const data = await fixture(request, ["rejected", "lost"], secrets);
    for (const name of ["rejected", "lost"]) await adminAttempt(request, data, name, "rejected", secrets);
    const rejected = await mint(request, data, "rejected", secrets, true);
    const first = await lookup(request, rejected.token);
    const before = await approvalCalls(request);
    await nextApproval(request, "rejected");
    expect((await retry(request, rejected, first.payment_retry.version)).status()).toBe(200);
    const second = await lookup(request, rejected.token);
    expect(second.payment.status).toBe("failed");
    expect(second.payment_retry.allowed).toBe(true);
    expect(second.payment_retry.version).not.toBe(first.payment_retry.version);
    expect((await retry(request, rejected, first.payment_retry.version)).status()).toBe(409);
    expect(await approvalCalls(request) - before).toBe(1);
    await nextApproval(request, "rejected");
    expect((await retry(request, rejected, second.payment_retry.version)).status()).toBe(200);
    const exhausted = await lookup(request, rejected.token);
    expect(exhausted.payment_retry.allowed).toBe(false);
    expect(attempts(data, "rejected")).toBe(3);
    expect((await retry(request, rejected, second.payment_retry.version)).status()).toBe(409);
    expect(await approvalCalls(request) - before).toBe(2);
    const lost = await mint(request, data, "lost", secrets, true);
    const invoice = await lookup(request, lost.token);
    const lostBefore = await approvalCalls(request);
    await nextApproval(request, "response-lost");
    const response = await retry(request, lost, invoice.payment_retry.version);
    expect(response.status()).toBe(200);
    const pending = await response.json() as PublicInvoice;
    expect(pending.payment).toMatchObject({ status: "pending", paid_amount: null, outstanding_amount: null });
    expect(pending.payment_retry.allowed).toBe(false);
    expect((await retry(request, lost, invoice.payment_retry.version)).status()).toBe(409);
    expect(await approvalCalls(request) - lostBefore).toBe(1);
    expect(attempts(data, "lost")).toBe(2);
    result("retry-outcomes-result", { passed: true, failed_replay_additional_calls: 0, shared_attempt_limit: 3, exhausted_attempts: attempts(data, "rejected"), lost_provider_response_status: "pending", lost_provider_response_calls: 1, lost_provider_response_attempts: attempts(data, "lost") });
    await revoke(request, data, "rejected", rejected); await revoke(request, data, "lost", lost);
  } catch (error) { throw safeFailure(error, secrets); }
});

test("고객은 금액과 모드를 확인한 뒤에만 결제를 다시 시도하고 조회 전용 링크는 유지된다", async ({ browser, request }) => {
  const secrets = newSecrets();
  const contexts: BrowserContext[] = [];
  try {
    const data = await fixture(request, ["interactive"], secrets);
    await adminAttempt(request, data, "interactive", "rejected", secrets);
    const owner = await browser.newContext({ baseURL: FRONTEND_URL, viewport: { width: 1280, height: 900 } }); contexts.push(owner);
    await owner.addCookies([{ name: "me_session", value: data.session, domain: "localhost", path: "/", httpOnly: true, sameSite: "Lax" }]);
    const consolePage = await owner.newPage();
    await consolePage.goto(`/payments/${data.invoices.interactive}`);
    const section = consolePage.locator("section").filter({ has: consolePage.getByRole("heading", { name: "고객 조회 링크", exact: true }) });
    const checkbox = section.getByRole("checkbox", { name: "고객의 실패 결제 재시도 허용", exact: true });
    await expect(checkbox).not.toBeChecked();
    await section.getByRole("button", { name: "고객 조회 링크 발급", exact: true }).click();
    const readonlyUrl = await section.getByRole("textbox", { name: "전달할 고객 조회 링크", exact: true }).inputValue();
    const readonlyToken = new URL(readonlyUrl).hash.slice(1); secrets.add(readonlyToken);
    await checkbox.check();
    await section.getByRole("button", { name: "고객 조회 링크 발급", exact: true }).click();
    await expect(section.getByRole("textbox", { name: "전달할 고객 조회 링크", exact: true })).not.toHaveValue(readonlyUrl);
    const allowedUrl = await section.getByRole("textbox", { name: "전달할 고객 조회 링크", exact: true }).inputValue();
    const allowedToken = new URL(allowedUrl).hash.slice(1); secrets.add(allowedToken);
    const customer = await browser.newContext({ baseURL: FRONTEND_URL, viewport: { width: 1280, height: 900 } }); contexts.push(customer);
    const page = await customer.newPage();
    trackVersions(page);
    const urls: string[] = []; page.on("request", message => urls.push(message.url()));
    await page.goto(readonlyUrl);
    await expect(page.getByText(data.invoiceNumbers.interactive, { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "결제 다시 시도", exact: true })).toHaveCount(0);
    await expect(page.getByText("결제가 완료되지 않았습니다. 아래에서 금액을 확인한 뒤 등록된 카드로 다시 시도할 수 있습니다.", { exact: true })).toHaveCount(0);
    await page.goto(allowedUrl);
    const start = page.getByRole("button", { name: "결제 다시 시도", exact: true });
    await expect(start).toBeVisible();
    await expect(page.getByText("결제가 완료되지 않았습니다. 아래에서 금액을 확인한 뒤 등록된 카드로 다시 시도할 수 있습니다.", { exact: true })).toBeVisible();
    expect((await start.boundingBox())!.height).toBeGreaterThanOrEqual(44);
    const before = await approvalCalls(request);
    await start.click();
    const confirm = page.getByRole("button", { name: "1,100원 테스트 결제 다시 시도", exact: true });
    await expect(confirm).toBeVisible();
    expect(await approvalCalls(request)).toBe(before);
    await capture(page, "customer-confirm-desktop");
    await page.getByRole("button", { name: "돌아가기", exact: true }).click();
    expect(await approvalCalls(request)).toBe(before);
    await page.setViewportSize({ width: 390, height: 844 });
    await start.click();
    await expect(confirm).toBeVisible();
    await capture(page, "customer-confirm-mobile");
    await confirm.focus(); await page.keyboard.press("Enter");
    await expect(page.getByText("납부 완료", { exact: true })).toBeVisible();
    expect(await approvalCalls(request) - before).toBe(1);
    await expect(page.getByRole("button", { name: "결제 다시 시도", exact: true })).toHaveCount(0);
    await expect(page).toHaveURL(`${FRONTEND_URL}/invoice-links`);
    expect(await customer.cookies()).toHaveLength(0);
    const html = await page.content();
    for (const secret of secrets) { expect(html.includes(secret)).toBe(false); expect(urls.some(url => url.includes(secret))).toBe(false); }
    expect(urls.filter(url => new URL(url).origin !== FRONTEND_URL)).toHaveLength(0);
    await capture(page, "customer-paid-mobile");
    await page.setViewportSize({ width: 1280, height: 900 }); await capture(page, "customer-paid-desktop");
    const links = await request.get(`${API_URL}/v1/invoices/${data.invoices.interactive}/view-links`, { headers: data.headers });
    const rows = (await links.json()).invoice_view_links as ViewLink[];
    for (const link of rows) await revoke(request, data, "interactive", link);
    result("browser-confirm-result", { passed: true, invoice_number: data.invoiceNumbers.interactive, default_checkbox_unchecked: true, read_only_no_retry_action: true, confirmation_before_provider_call: true, confirmation_cancel_calls: 0, keyboard_confirm_provider_calls: 1, confirmed_amount_krw: 1100, mode: "test", customer_session_required: false, token_in_url_or_html: false, document_horizontal_overflow: false });
  } catch (error) { throw safeFailure(error, secrets); }
  finally { for (const context of contexts) await context.close(); }
});

test("고객 화면의 응답 유실은 재결제 대신 상태 조회로 복구하고 쓰기 출처를 검사한다", async ({ browser, request }) => {
  const secrets = newSecrets();
  let context: BrowserContext | undefined;
  let releaseLateResponse: () => void = () => {};
  try {
    const data = await fixture(request, ["interrupted", "pending", "other"], secrets);
    for (const name of ["interrupted", "pending", "other"]) await adminAttempt(request, data, name, "rejected", secrets);
    const link = await mint(request, data, "interrupted", secrets, true);
    const invoice = await lookup(request, link.token);
    const blockedBefore = await approvalCalls(request);
    expect((await request.post(`${FRONTEND_URL}/invoice-links/payment-retries`, { headers: { Origin: "https://attacker.example", "Content-Type": "application/json" }, data: { token: link.token, retry_version: invoice.payment_retry.version } })).status()).toBe(403);
    for (const extra of [{ amount: 1 }, { customer_id: data.customers.pending }, { test_code: "REJECT_CARD_PAYMENT" }]) {
      expect((await request.post(`${FRONTEND_URL}/invoice-links/payment-retries`, { headers: { Origin: FRONTEND_URL }, data: { token: link.token, retry_version: invoice.payment_retry.version, ...extra } })).status()).toBe(400);
    }
    expect((await request.post(`${FRONTEND_URL}/invoice-links/payment-retries`, { headers: { Origin: FRONTEND_URL }, data: { token: link.token, retry_version: invoice.payment_retry.version, padding: "x".repeat(2048) } })).status()).toBe(413);
    expect(await approvalCalls(request)).toBe(blockedBefore);
    context = await browser.newContext({ baseURL: FRONTEND_URL, viewport: { width: 390, height: 844 } });
    const page = await context.newPage();
    trackVersions(page);
    let submissions = 0;
    await page.route("**/invoice-links/payment-retries", async route => {
      submissions++;
      const response = await route.fetch();
      expect(response.status()).toBe(200);
      await route.fulfill({ status: 503, contentType: "application/json", body: JSON.stringify({ message: "로컬 검증용 응답 유실" }) });
    });
    await ready(page, link);
    await page.getByRole("button", { name: "결제 다시 시도", exact: true }).click();
    const before = await approvalCalls(request);
    await page.getByRole("button", { name: "1,100원 테스트 결제 다시 시도", exact: true }).click();
    await expect(page.getByText(/결제 요청 결과를 확인하지 못했습니다/)).toBeVisible();
    await expect(page.getByRole("button", { name: "결제 다시 시도", exact: true })).toHaveCount(0);
    await expect(page.getByText("결제가 완료되지 않았습니다. 아래에서 금액을 확인한 뒤 등록된 카드로 다시 시도할 수 있습니다.", { exact: true })).toHaveCount(0);
    expect(submissions).toBe(1);
    expect(await approvalCalls(request) - before).toBe(1);
    await capture(page, "customer-result-unknown-mobile");
    await page.getByRole("button", { name: "결제 상태 새로 조회", exact: true }).first().click();
    await expect(page.getByText("납부 완료", { exact: true })).toBeVisible();
    expect(submissions).toBe(1); expect(await approvalCalls(request) - before).toBe(1);
    await page.unroute("**/invoice-links/payment-retries");
    const pending = await mint(request, data, "pending", secrets, true);
    const other = await mint(request, data, "other", secrets);
    await ready(page, pending);
    await nextApproval(request, "response-lost");
    let isUpstreamHandled = false;
    let isLateResponseReleased = false;
    let wasLateResponseCanceled = false;
    const heldResponse = new Promise<void>(resolve => { releaseLateResponse = resolve; });
    await page.route("**/invoice-links/payment-retries", async route => {
      const response = await route.fetch();
      expect(response.status()).toBe(200);
      isUpstreamHandled = true;
      await heldResponse;
      try { await route.fulfill({ response }); } catch { wasLateResponseCanceled = true; }
      finally { isLateResponseReleased = true; }
    });
    const pendingBefore = await approvalCalls(request);
    await page.getByRole("button", { name: "결제 다시 시도", exact: true }).click();
    await page.getByRole("button", { name: "1,100원 테스트 결제 다시 시도", exact: true }).click();
    await expect.poll(() => isUpstreamHandled).toBe(true);
    await ready(page, other);
    await expect(page.getByText(data.invoiceNumbers.other, { exact: true })).toBeVisible();
    releaseLateResponse(); await expect.poll(() => isLateResponseReleased).toBe(true);
    await page.evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))));
    await expect(page.getByText(data.invoiceNumbers.other, { exact: true })).toBeVisible();
    await expect(page.getByText(data.invoiceNumbers.pending, { exact: true })).toHaveCount(0);
    await expect(page.getByText("결제 실패", { exact: true })).toBeVisible();
    expect(await approvalCalls(request) - pendingBefore).toBe(1);
    await capture(page, "customer-other-link-after-late-response-mobile");
    await page.unroute("**/invoice-links/payment-retries");
    await ready(page, pending);
    await expect(page.getByText("결제 확인 중", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "결제 다시 시도", exact: true })).toHaveCount(0);
    await capture(page, "customer-pending-mobile");
    await revoke(request, data, "interrupted", link); await revoke(request, data, "pending", pending); await revoke(request, data, "other", other);
    result("browser-recovery-result", { passed: true, cross_origin_status: 403, frontend_response_lost_submissions: submissions, frontend_response_lost_provider_calls: 1, recovered_by_lookup: true, automatic_repayment: false, provider_response_lost_rendered_pending: true, late_response_did_not_replace_other_invoice: true, late_response_canceled_by_navigation: wasLateResponseCanceled, late_response_provider_calls: 1 });
  } catch (error) { throw safeFailure(error, secrets); }
  finally { releaseLateResponse(); await context?.close(); }
});
