import { expect, test, type APIRequestContext, type Browser, type BrowserContext, type Page } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { execFileSync } from "node:child_process";
import { chmodSync, existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { CONSENT_DOCUMENTS, CONSENT_VERSION } from "../src/lib/auth/consents";

const API_URL = process.env.S1_API_URL ?? "http://127.0.0.1:38147";
const FRONTEND_URL = process.env.PERMISSIONS_FRONTEND_URL ?? "http://localhost:33147";
const OUTPUT = process.env.PERMISSIONS_EVIDENCE_DIR ?? "/private/tmp/meterengine-permissions-2026-10-27";
const PASSWORD = "local-permissions-e2e-password";
const CHANGED_PASSWORD = "local-permissions-e2e-own-password";
const OPERATOR_EMAIL = process.env.E2E_OPERATOR_EMAIL ?? "operator@fe-e2e.local";
const OPERATOR_PASSWORD = process.env.E2E_OPERATOR_PASSWORD ?? "local-operator-e2e-password";
const SECRET_GROUPS: Set<string>[] = [];
const CONSENTS = CONSENT_DOCUMENTS.map(({ document }) => ({ document, document_version: CONSENT_VERSION }));
// 직접 생성한 계정은 첫 동의에서 비밀번호를 바꾸므로 다음 로그인은 바뀐 비밀번호로 한다.
const CONSENTED = new Set<string>();
const READ = /^조회 \(read\):/;
const INGEST = /^수집 \(ingest\):/;
const ADMIN = /^전체 관리 \(admin\):/;
let previousCopyPrompt: string | undefined;

type Headers = Record<string, string>;
type Session = { token: string; scopes: string[]; organization_role: string | null; consent_required: boolean; user_account: { id: string; email: string; role: string }; expires_at: string };
type Member = { user_account_id: string; email: string; name: string; organization_role: string; scopes: string[]; status: string };
type User = Member & { session: Session; headers: Headers };
type Fixture = { organizationId: string; organizationName: string; owner: Session; headers: Headers; operator: Session; operatorHeaders: Headers; customerId: string; customerName: string; metricCode: string; invoiceId: string; month: string };
type Key = { id: string; secret: string; scopes: string[]; mode: string; expires_at: string | null; rate_limit_per_minute: number | null };

test.use({ trace: "off", screenshot: "off", video: "off" });
test.beforeAll(() => {
  mkdirSync(OUTPUT, { recursive: true, mode: 0o700 });
  chmodSync(OUTPUT, 0o700);
  previousCopyPrompt = process.env.PLAYWRIGHT_NO_COPY_PROMPT;
  process.env.PLAYWRIGHT_NO_COPY_PROMPT = "1";
  for (const value of [API_URL, FRONTEND_URL]) {
    const url = new URL(value);
    if (url.protocol !== "http:" || !["127.0.0.1", "localhost", "[::1]"].includes(url.hostname)) throw new Error("권한 검증은 로컬 HTTP 서버에서만 실행합니다.");
  }
  const env = JSON.parse(readFileSync("/private/tmp/meterengine-orchestration-0696/backend-e2e-environment.json", "utf8"));
  if (env.TOSSPAYMENTS_BASE_URL !== "http://127.0.0.1:38447") throw new Error("격리된 PG 대역 설정을 확인하지 못했습니다.");
});
test.afterAll(() => {
  const path = `${OUTPUT}/private-values.json`;
  const previous = existsSync(path) ? JSON.parse(readFileSync(path, "utf8")).values as string[] : [];
  writeFileSync(path, JSON.stringify({ values: [...new Set([...previous, ...SECRET_GROUPS.flatMap(group => [...group])])] }), { mode: 0o600 });
  chmodSync(path, 0o600);
  if (previousCopyPrompt === undefined) delete process.env.PLAYWRIGHT_NO_COPY_PROMPT;
  else process.env.PLAYWRIGHT_NO_COPY_PROMPT = previousCopyPrompt;
});

function newSecrets() { const values = new Set([PASSWORD, CHANGED_PASSWORD, OPERATOR_PASSWORD]); SECRET_GROUPS.push(values); return values; }
function safeFailure(error: unknown, secrets: Set<string>) {
  let message = error instanceof Error ? error.stack ?? error.message : "권한 E2E 검증을 마치지 못했습니다.";
  for (const secret of secrets) if (secret) message = message.split(secret).join("[redacted]");
  return new Error(message.replace(/me_(?:invite|session|invoice|test|live)_[A-Za-z0-9_-]+/g, "[redacted token]"));
}
function result(name: string, value: unknown) { writeFileSync(`${OUTPUT}/${name}.json`, JSON.stringify(value, null, 2)); }
function authorization(token: string): Headers { return { Authorization: `Bearer ${token}` }; }
function uuid(value: string) { if (!/^[a-f\d-]{36}$/i.test(value)) throw new Error("검증 UUID가 잘못됐습니다."); return value; }
function sql(statement: string) {
  return execFileSync("docker", ["exec", "-i", "meterengine-0696-e2e", "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", "meterengine", "-d", "meterengine", "-At"], { input: statement, encoding: "utf8", stdio: ["pipe", "pipe", "pipe"] }).trim();
}
async function current(request: APIRequestContext, headers: Headers) {
  const response = await request.get(`${API_URL}/v1/sessions/current`, { headers });
  expect(response.status()).toBe(200);
  return await response.json() as Session;
}
async function login(request: APIRequestContext, email: string, secrets: Set<string>, password = PASSWORD): Promise<Session> {
  const response = await request.post(`${API_URL}/v1/sessions`, { data: { email, password: CONSENTED.has(email) ? CHANGED_PASSWORD : password } });
  expect(response.status()).toBe(201);
  const session = await response.json() as Session;
  secrets.add(session.token);
  if (session.consent_required) {
    const accepted = await request.post(`${API_URL}/v1/sessions/current/consents`, { headers: authorization(session.token), data: { consents: CONSENTS, new_password: CHANGED_PASSWORD } });
    expect(accepted.status()).toBe(200);
    CONSENTED.add(email);
    return { ...await accepted.json(), token: session.token } as Session;
  }
  return session;
}
async function fixture(request: APIRequestContext, secrets: Set<string>): Promise<Fixture> {
  const suffix = randomUUID();
  const email = `permissions-owner-${suffix}@fe-e2e.local`;
  const organizationName = `권한 검증 ${suffix}`;
  const signup = await request.post(`${API_URL}/v1/signups`, { data: { organization_name: organizationName, name: "권한 대표관리자", email, password: PASSWORD, consents: CONSENTS } });
  expect(signup.status()).toBe(201);
  const organizationId = uuid((await signup.json()).organization.id);
  const operator = await login(request, OPERATOR_EMAIL, secrets, OPERATOR_PASSWORD);
  const operatorHeaders = authorization(operator.token);
  expect((await request.put(`${API_URL}/v1/organizations/${organizationId}/status`, { headers: operatorHeaders, data: { status: "active" } })).status()).toBe(200);
  const owner = await login(request, email, secrets);
  expect(owner.organization_role).toBe("owner"); expect(owner.scopes).toContain("admin");
  const headers = authorization(owner.token);
  const customerName = "권한별 업무 확인 고객";
  const customer = await request.post(`${API_URL}/v1/customers`, { headers, data: { name: customerName, external_id: `permissions-${suffix}` } });
  expect(customer.status()).toBe(201);
  const customerId = uuid((await customer.json()).id);
  const metricCode = `permissions-${suffix}`;
  const month = new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit" }).format(new Date());
  expect((await request.post(`${API_URL}/v1/billable-metrics`, { headers, data: { code: metricCode, name: "권한 확인 사용량", event_type: metricCode, aggregation: "sum", target_property: "units", dimension_properties: [] } })).status()).toBe(201);
  expect((await request.post(`${API_URL}/v1/billable-metrics/${metricCode}/price-rates`, { headers, data: { effective_from: month, dimension_values: {}, model: "per_unit", unit_price: "100" } })).status()).toBe(201);
  expect((await request.post(`${API_URL}/v1/events`, { headers, data: { transaction_id: randomUUID(), customer_id: customerId, type: metricCode, timestamp: new Date(Date.now() - 60_000).toISOString(), properties: { units: 10 } } })).status()).toBe(200);
  expect((await request.post(`${API_URL}/v1/invoices?month=${month}`, { headers })).status()).toBe(200);
  const invoices = await request.get(`${API_URL}/v1/invoices?month=${month}`, { headers });
  expect(invoices.status()).toBe(200);
  const invoiceId = uuid((await invoices.json()).invoices[0].id);
  return { organizationId, organizationName, owner, headers, operator, operatorHeaders, customerId, customerName, metricCode, month, invoiceId };
}
async function member(request: APIRequestContext, data: Fixture, secrets: Set<string>, name: string, scopes?: string[]): Promise<User> {
  const email = `permissions-${name}-${randomUUID()}@fe-e2e.local`;
  const response = await request.post(`${API_URL}/v1/organization/members`, { headers: data.headers, data: { email, name: `권한 사용자 ${name}`, password: PASSWORD, ...(scopes === undefined ? {} : { scopes }) } });
  expect(response.status()).toBe(201);
  const created = await response.json() as Member;
  const session = await login(request, email, secrets);
  expect(session.scopes).toEqual(scopes ?? ["read"]);
  return { ...created, session, headers: authorization(session.token) };
}
async function updateScopes(request: APIRequestContext, data: Fixture, id: string, scopes: string[]) {
  const response = await request.put(`${API_URL}/v1/organization/members/${id}/scopes`, { headers: data.headers, data: { scopes } });
  expect(response.status()).toBe(200);
  return await response.json() as Member;
}
async function key(request: APIRequestContext, data: Fixture, secrets: Set<string>, scopes: string[], label = "권한 검증 서버 키"): Promise<Key> {
  const response = await request.post(`${API_URL}/v1/api-keys`, { headers: data.headers, data: { label, mode: "test", scopes, rate_limit_per_minute: 1000 } });
  expect(response.status()).toBe(201);
  const created = await response.json() as Key;
  secrets.add(created.secret); return created;
}
async function browserPage(browser: Browser, session: Session, width = 1280) {
  const context = await browser.newContext({ baseURL: FRONTEND_URL, viewport: { width, height: 900 } });
  await context.addCookies([{ name: "me_session", value: session.token, url: FRONTEND_URL, httpOnly: true, sameSite: "Lax", secure: false }]);
  return { context, page: await context.newPage() };
}
async function capture(page: Page, name: string) {
  await page.evaluate(() => window.scrollTo(0, 0));
  const dimensions = await page.evaluate(() => ({ viewport: innerWidth, document: document.documentElement.scrollWidth }));
  expect(dimensions.document).toBeLessThanOrEqual(dimensions.viewport);
  await page.screenshot({ path: `${OUTPUT}/${name}.png`, fullPage: true, animations: "disabled" });
  const dialog = page.getByRole("dialog");
  if (await dialog.count() === 1) await dialog.screenshot({ path: `${OUTPUT}/${name}-dialog.png`, animations: "disabled" });
  return dimensions;
}

test("사용자별 조회와 수집, 관리 권한을 같은 세션에서 즉시 적용하고 상향을 차단한다", async ({ request }) => {
  const secrets = newSecrets();
  try {
    const data = await fixture(request, secrets);
    const reader = await member(request, data, secrets, "reader");
    expect(reader.organization_role).toBe("member");
    for (const path of ["/v1/customers", "/v1/usage", "/v1/invoices", "/v1/invoice-payments", "/v1/billable-metrics", "/v1/organization/members", "/v1/api-keys"]) expect((await request.get(`${API_URL}${path}`, { headers: reader.headers })).status()).toBe(200);
    for (const status of ["active", "archived", "all"]) expect((await request.get(`${API_URL}/v1/customers?status=${status}`, { headers: reader.headers })).status()).toBe(200);
    expect((await request.post(`${API_URL}/v1/customers`, { headers: reader.headers, data: { name: "차단 고객" } })).status()).toBe(403);
    expect((await request.put(`${API_URL}/v1/customers/${data.customerId}`, { headers: reader.headers, data: { name: "차단 수정" } })).status()).toBe(403);
    expect((await request.delete(`${API_URL}/v1/customers/${data.customerId}`, { headers: reader.headers })).status()).toBe(403);
    expect((await request.post(`${API_URL}/v1/invoices/${data.invoiceId}/payment-attempts`, { headers: reader.headers })).status()).toBe(403);
    expect((await request.put(`${API_URL}/v1/organization/members/${reader.user_account_id}/scopes`, { headers: reader.headers, data: { scopes: ["admin"] } })).status()).toBe(403);
    expect((await request.put(`${API_URL}/v1/user-accounts/${reader.user_account_id}/role`, { headers: reader.headers, data: { role: "operator" } })).status()).toBe(403);
    for (const scopes of [[], ["operator"], ["read", null]]) expect((await request.put(`${API_URL}/v1/organization/members/${reader.user_account_id}/scopes`, { headers: data.headers, data: { scopes } })).status()).toBe(400);
    expect((await request.put(`${API_URL}/v1/organization/members/${data.owner.user_account.id}/scopes`, { headers: data.headers, data: { scopes: ["read"] } })).status()).toBe(409);
    expect((await request.put(`${API_URL}/v1/organization/members/${data.owner.user_account.id}/role`, { headers: data.headers, data: { organization_role: "admin" } })).status()).toBe(409);
    expect((await request.delete(`${API_URL}/v1/organization/members/${data.owner.user_account.id}`, { headers: data.headers })).status()).toBe(409);
    const writer = await member(request, data, secrets, "ingest", ["ingest"]);
    expect((await request.get(`${API_URL}/v1/customers`, { headers: writer.headers })).status()).toBe(403);
    const created = await request.post(`${API_URL}/v1/customers`, { headers: writer.headers, data: { name: "수집 권한 등록", external_id: randomUUID() } });
    expect(created.status()).toBe(201);
    const customerId = uuid((await created.json()).id);
    const event = { transaction_id: randomUUID(), customer_id: customerId, type: data.metricCode, timestamp: new Date().toISOString(), properties: { units: 1 } };
    expect((await request.post(`${API_URL}/v1/events`, { headers: writer.headers, data: event })).status()).toBe(200);
    expect((await request.post(`${API_URL}/v1/event-batches`, { headers: writer.headers, data: { events: [{ ...event, transaction_id: randomUUID() }] } })).status()).toBe(200);
    expect((await request.put(`${API_URL}/v1/customers/${customerId}`, { headers: writer.headers, data: { name: "수집 권한 변경 시도" } })).status()).toBe(403);
    expect((await request.get(`${API_URL}/v1/organization/members`, { headers: writer.headers })).status()).toBe(403);
    await updateScopes(request, data, reader.user_account_id, ["read", "ingest"]);
    expect((await current(request, reader.headers)).scopes).toEqual(["read", "ingest"]);
    expect((await request.post(`${API_URL}/v1/customers`, { headers: reader.headers, data: { name: "조합 권한 등록", external_id: randomUUID() } })).status()).toBe(201);
    await updateScopes(request, data, reader.user_account_id, ["admin"]);
    expect((await current(request, reader.headers)).organization_role).toBe("admin");
    expect((await request.put(`${API_URL}/v1/customers/${customerId}`, { headers: reader.headers, data: { name: "관리 권한 수정" } })).status()).toBe(200);
    expect((await request.post(`${API_URL}/v1/organization/members`, { headers: reader.headers, data: { email: `blocked-${randomUUID()}@fe-e2e.local`, name: "차단 계정", password: PASSWORD } })).status()).toBe(403);
    await updateScopes(request, data, reader.user_account_id, ["read"]);
    expect((await current(request, reader.headers)).scopes).toEqual(["read"]);
    expect((await request.put(`${API_URL}/v1/customers/${customerId}`, { headers: reader.headers, data: { name: "옛 세션 관리 재사용" } })).status()).toBe(403);
    result("user-api-result", { passed: true, new_user_default: ["read"], read_write_status: 403, ingest_customer_and_events: true, ingest_read_status: 403, same_session_permission_refresh: true, owner_protected: true, self_elevation_status: 403 });
  } catch (error) { throw safeFailure(error, secrets); }
});

test("초대 권한과 소속별 권한을 보존하고 보관 복원과 플랫폼 역할을 분리한다", async ({ request }) => {
  const secrets = newSecrets();
  try {
    const first = await fixture(request, secrets); const second = await fixture(request, secrets);
    const reader = await member(request, first, secrets, "multi");
    expect((await request.put(`${API_URL}/v1/organization/members/${reader.user_account_id}/scopes`, { headers: second.headers, data: { scopes: ["admin"] } })).status()).toBe(404);
    expect((await request.post(`${API_URL}/v1/organizations/${second.organizationId}/members`, { headers: first.operatorHeaders, data: { user_account_id: reader.user_account_id, scopes: ["admin"] } })).status()).toBe(201);
    expect((await request.put(`${API_URL}/v1/sessions/current/organization`, { headers: reader.headers, data: { organization_id: second.organizationId } })).status()).toBe(200);
    expect((await current(request, reader.headers)).scopes).toContain("admin");
    expect((await request.post(`${API_URL}/v1/customers`, { headers: reader.headers, data: { name: "다른 도입사 관리", external_id: randomUUID() } })).status()).toBe(201);
    expect((await request.put(`${API_URL}/v1/sessions/current/organization`, { headers: reader.headers, data: { organization_id: first.organizationId } })).status()).toBe(200);
    expect((await current(request, reader.headers)).scopes).toEqual(["read"]);
    const invitedEmail = `permissions-invite-${randomUUID()}@fe-e2e.local`;
    const invitation = await request.post(`${API_URL}/v1/organization/invitations`, { headers: first.headers, data: { email: invitedEmail } });
    expect(invitation.status()).toBe(201);
    const issued = await invitation.json(); secrets.add(issued.token);
    const preview = await request.get(`${API_URL}/v1/organization-invitations/${issued.token}`);
    expect(preview.status()).toBe(200);
    const accepted = await request.post(`${API_URL}/v1/organization-invitations/${issued.token}/accept`, { data: { name: "초대 조회 사용자", password: PASSWORD, consents: CONSENTS, scopes: ["admin"], organization_role: "owner" } });
    expect(accepted.status()).toBe(201);
    const invited = await login(request, invitedEmail, secrets);
    expect(invited.scopes).toEqual(["read"]); expect(invited.organization_role).toBe("member");
    expect((await request.delete(`${API_URL}/v1/organization/members/${reader.user_account_id}`, { headers: first.headers })).status()).toBe(204);
    const restoreInvitation = await request.post(`${API_URL}/v1/organization/invitations`, { headers: first.headers, data: { email: reader.email, scopes: ["read"] } });
    expect(restoreInvitation.status()).toBe(201);
    const restore = await restoreInvitation.json(); secrets.add(restore.token);
    const restoreSession = await login(request, reader.email, secrets);
    expect((await request.post(`${API_URL}/v1/organization-invitations/${restore.token}/accept`, { headers: authorization(restoreSession.token), data: {} })).status()).toBe(201);
    expect((await request.put(`${API_URL}/v1/sessions/current/organization`, { headers: authorization(restoreSession.token), data: { organization_id: first.organizationId } })).status()).toBe(200);
    expect((await current(request, authorization(restoreSession.token))).scopes).toEqual(["read"]);
    result("invitation-membership-result", { passed: true, multi_organization_permissions_differ: true, invitation_body_did_not_elevate: true, archived_membership_restored_read_only: true, cross_organization_status: 404 });
  } catch (error) { throw safeFailure(error, secrets); }
});

test("같은 API 키의 권한만 바꾸고 읽기와 수집, 키 관리 금지를 즉시 적용한다", async ({ request }) => {
  const secrets = newSecrets();
  try {
    const data = await fixture(request, secrets); const other = await fixture(request, secrets);
    const reader = await member(request, data, secrets, "key-reader");
    const issued = await key(request, data, secrets, ["admin"]);
    const headers = authorization(issued.secret);
    const fingerprint = () => sql(`SELECT md5((to_jsonb(api_key)-'scopes')::text) FROM api_key WHERE id='${uuid(issued.id)}';`);
    const before = fingerprint();
    expect((await request.get(`${API_URL}/v1/api-keys`, { headers })).status()).toBe(403);
    expect((await request.put(`${API_URL}/v1/api-keys/${issued.id}/scopes`, { headers, data: { scopes: ["read"] } })).status()).toBe(403);
    expect((await request.put(`${API_URL}/v1/api-keys/${issued.id}/scopes`, { headers: reader.headers, data: { scopes: ["admin"] } })).status()).toBe(403);
    expect((await request.put(`${API_URL}/v1/api-keys/${issued.id}/scopes`, { headers: other.headers, data: { scopes: ["read"] } })).status()).toBe(404);
    const updated = await request.put(`${API_URL}/v1/api-keys/${issued.id}/scopes`, { headers: data.headers, data: { scopes: ["read", "read"] } });
    expect(updated.status()).toBe(200); expect((await updated.json()).scopes).toEqual(["read"]);
    expect((await updated.text()).includes(issued.secret)).toBe(false); expect(fingerprint()).toBe(before);
    expect((await request.get(`${API_URL}/v1/customers`, { headers })).status()).toBe(200);
    expect((await request.post(`${API_URL}/v1/customers`, { headers, data: { name: "읽기 키 등록 시도" } })).status()).toBe(403);
    expect((await request.put(`${API_URL}/v1/api-keys/${issued.id}/scopes`, { headers: data.headers, data: { scopes: ["ingest"] } })).status()).toBe(200);
    const created = await request.post(`${API_URL}/v1/customers`, { headers, data: { name: "수집 키 고객", external_id: randomUUID() } });
    expect(created.status()).toBe(201);
    const customerId = uuid((await created.json()).id);
    expect((await request.post(`${API_URL}/v1/event-batches`, { headers, data: { events: [{ transaction_id: randomUUID(), customer_id: customerId, type: data.metricCode, timestamp: new Date().toISOString(), properties: { units: 1 } }] } })).status()).toBe(200);
    expect((await request.get(`${API_URL}/v1/customers`, { headers })).status()).toBe(403);
    expect((await request.put(`${API_URL}/v1/api-keys/${issued.id}/scopes`, { headers: data.headers, data: { scopes: ["read", "ingest"] } })).status()).toBe(200);
    expect((await request.get(`${API_URL}/v1/customers`, { headers })).status()).toBe(200);
    expect((await request.delete(`${API_URL}/v1/customers/${customerId}`, { headers })).status()).toBe(403);
    for (const scopes of [[], ["owner"], ["read", null]]) expect((await request.put(`${API_URL}/v1/api-keys/${issued.id}/scopes`, { headers: data.headers, data: { scopes } })).status()).toBe(400);
    expect((await request.delete(`${API_URL}/v1/api-keys/${issued.id}`, { headers: data.headers })).status()).toBe(204);
    expect((await request.put(`${API_URL}/v1/api-keys/${issued.id}/scopes`, { headers: data.headers, data: { scopes: ["admin"] } })).status()).toBe(409);
    const expired = await key(request, data, secrets, ["read"], "만료 권한 검증");
    sql(`UPDATE api_key SET created_at=clock_timestamp()-interval '2 days',expires_at=clock_timestamp()-interval '1 day' WHERE id='${uuid(expired.id)}';`);
    expect((await request.put(`${API_URL}/v1/api-keys/${expired.id}/scopes`, { headers: data.headers, data: { scopes: ["admin"] } })).status()).toBe(409);
    const audit = await request.get(`${API_URL}/v1/audit-logs?size=100&action=api_key_scopes_updated`, { headers: data.headers });
    expect(audit.status()).toBe(200); const auditText = await audit.text();
    for (const secret of secrets) expect(auditText.includes(secret)).toBe(false);
    expect(Number(sql(`SELECT count(*) FROM audit_log WHERE organization_id='${data.organizationId}' AND action='api_key_scopes_updated' AND actor_type='user_account';`))).toBeGreaterThanOrEqual(3);
    result("api-key-result", { passed: true, raw_key_and_other_metadata_preserved: true, same_key_permissions_immediate: true, key_management_with_api_key_status: 403, read_session_management_status: 403, revoked_and_expired_update_status: 409, audit_secret_leak: false });
  } catch (error) { throw safeFailure(error, secrets); }
});

test("대표관리자가 화면에서 사용자와 키 권한을 정하고 조회 전용은 읽기 화면만 사용한다", async ({ browser, request }) => {
  const secrets = newSecrets(); const contexts: BrowserContext[] = [];
  try {
    const data = await fixture(request, secrets);
    const owner = await browserPage(browser, data.owner); contexts.push(owner.context);
    await owner.page.goto("/members");
    await owner.page.getByRole("button", { name: "계정 직접 생성", exact: true }).click();
    const dialog = owner.page.getByRole("dialog");
    await expect(dialog.getByRole("checkbox", { name: READ })).toBeChecked();
    await expect(dialog.getByRole("checkbox", { name: INGEST })).not.toBeChecked();
    await expect(dialog.getByRole("checkbox", { name: ADMIN })).not.toBeChecked();
    await dialog.getByRole("checkbox", { name: INGEST }).focus(); await owner.page.keyboard.press("Space");
    await expect(dialog.getByRole("checkbox", { name: INGEST })).toBeChecked();
    await owner.page.keyboard.press("Space"); await expect(dialog.getByRole("checkbox", { name: INGEST })).not.toBeChecked();
    await capture(owner.page, "owner-create-read-default-desktop");
    const email = `permissions-ui-${randomUUID()}@fe-e2e.local`;
    await dialog.getByLabel(/^사용자 이메일/).fill(email); await dialog.getByLabel(/^이름/).fill("화면 조회 사용자"); await dialog.getByLabel(/^초기 비밀번호/).fill(PASSWORD);
    await dialog.getByRole("button", { name: "사용자 계정 생성", exact: true }).click();
    await expect(dialog).toHaveCount(0);
    const list = await request.get(`${API_URL}/v1/organization/members`, { headers: data.headers });
    const user = (await list.json()).members.find((value: Member) => value.email === email) as Member;
    expect(user.scopes).toEqual(["read"]);
    const session = await login(request, email, secrets);
    const reader = await browserPage(browser, session); contexts.push(reader.context);
    for (const path of ["/overview", "/customers", `/customers/${data.customerId}?month=${data.month}`, "/billable-metrics", "/pricing", "/events", "/usage", "/billing", "/invoices", "/payments", `/payments/${data.invoiceId}`, "/integrations", "/settings", "/members", "/audit"]) {
      await reader.page.goto(path);
      await expect(reader.page.locator(".workspace-scope")).toHaveText("조회 전용");
      await expect(reader.page.getByRole("button", { name: "권한 새로 조회", exact: true })).toBeVisible();
      for (const name of ["고객 등록", "고객 정보 수정", "고객 보관", "API 키 발급", "키 권한 변경", "고객 조회 링크 발급", "등록 카드로 결제", "결제 다시 시도", "계정 직접 생성", "초대 링크 만들기"]) await expect(reader.page.getByRole("button", { name, exact: true })).toHaveCount(0);
      await expect(reader.page).not.toHaveURL(/\/login/);
    }
    await reader.page.goto("/customers?status=all");
    await reader.page.getByLabel("이름 또는 외부 ID로 찾기", { exact: true }).fill(data.customerName);
    await reader.page.getByRole("button", { name: "조건 적용", exact: true }).click();
    await expect(reader.page.getByRole("link", { name: data.customerName, exact: true })).toBeVisible();
    await capture(reader.page, "read-only-customers-desktop");
    await reader.page.setViewportSize({ width: 390, height: 844 }); await capture(reader.page, "read-only-customers-mobile");
    const row = owner.page.getByRole("row").filter({ hasText: email });
    await row.getByRole("button", { name: "권한 변경", exact: true }).click();
    await dialog.getByRole("checkbox", { name: INGEST }).check();
    await owner.page.setViewportSize({ width: 390, height: 844 }); await capture(owner.page, "owner-member-scopes-mobile"); await owner.page.setViewportSize({ width: 1280, height: 900 });
    await dialog.getByRole("button", { name: "권한 저장", exact: true }).click(); await expect(dialog).toHaveCount(0);
    await reader.page.setViewportSize({ width: 1280, height: 900 }); await reader.page.goto("/customers");
    await reader.page.getByRole("button", { name: "고객 등록", exact: true }).click();
    await reader.page.getByRole("dialog").getByLabel(/^고객명/).fill("조회 수집 조합 화면 고객");
    await reader.page.getByRole("dialog").getByRole("button", { name: "고객 저장", exact: true }).click(); await expect(reader.page.getByRole("dialog")).toHaveCount(0);
    await reader.page.getByRole("link", { name: "조회 수집 조합 화면 고객", exact: true }).click();
    await expect(reader.page.getByRole("button", { name: "고객 정보 수정", exact: true })).toHaveCount(0);
    const issued = await key(request, data, secrets, ["admin"], "화면 권한 변경 키");
    const keyFingerprint = sql(`SELECT md5((to_jsonb(api_key)-'scopes')::text) FROM api_key WHERE id='${uuid(issued.id)}';`);
    await owner.page.goto("/integrations");
    const keyRow = owner.page.getByRole("row").filter({ hasText: "화면 권한 변경 키" });
    await keyRow.getByRole("button", { name: "키 권한 변경", exact: true }).click();
    await dialog.getByRole("checkbox", { name: ADMIN }).uncheck(); await dialog.getByRole("checkbox", { name: READ }).focus(); await owner.page.keyboard.press("Space");
    await expect(dialog.getByRole("checkbox", { name: READ })).toBeChecked();
    await capture(owner.page, "owner-api-key-scopes-desktop");
    await dialog.getByRole("button", { name: "키 권한 저장", exact: true }).click(); await expect(dialog).toHaveCount(0);
    expect(sql(`SELECT md5((to_jsonb(api_key)-'scopes')::text) FROM api_key WHERE id='${uuid(issued.id)}';`)).toBe(keyFingerprint);
    expect((await request.get(`${API_URL}/v1/customers`, { headers: authorization(issued.secret) })).status()).toBe(200);
    expect((await request.post(`${API_URL}/v1/customers`, { headers: authorization(issued.secret), data: { name: "화면에서축소한키" } })).status()).toBe(403);
    await capture(owner.page, "owner-api-key-scopes-saved-desktop");
    result("browser-scopes-result", { passed: true, new_user_defaults_read: true, readonly_business_pages: 15, readonly_mutation_controls_hidden: true, readonly_filters_work: true, member_scopes_edited: true, read_ingest_can_create_but_not_modify: true, key_scope_edit_uses_same_key: true, key_scope_edit_preserves_metadata: true, desktop_width: 1280, mobile_width: 390 });
  } catch (error) { throw safeFailure(error, secrets); }
  finally { await Promise.all(contexts.map(context => context.close())); }
});

test("열린 관리 폼도 같은 세션의 권한 축소와 조작된 Server Action을 우회하지 못한다", async ({ browser, request }) => {
  const secrets = newSecrets(); let context: BrowserContext | undefined;
  try {
    const data = await fixture(request, secrets); const admin = await member(request, data, secrets, "stale-form", ["admin"]);
    const view = await browserPage(browser, admin.session); context = view.context;
    await view.page.goto("/customers"); await view.page.getByRole("button", { name: "고객 등록", exact: true }).click();
    const dialog = view.page.getByRole("dialog");
    await dialog.getByLabel(/^고객명/).fill("조작된 폼으로 변경");
    await dialog.locator("form").evaluate((form, id) => {
      const operation = form.querySelector<HTMLInputElement>('input[name="operation"]');
      if (operation) operation.value = "updateCustomer";
      const target = document.createElement("input"); target.type = "hidden"; target.name = "id"; target.value = id; form.append(target);
    }, data.customerId);
    await updateScopes(request, data, admin.user_account_id, ["read"]);
    await dialog.getByRole("button", { name: "고객 저장", exact: true }).click();
    await expect(dialog.getByText("이 작업을 수행할 권한이 없습니다. 대표관리자에게 권한을 요청하세요.", { exact: true })).toBeVisible();
    const customer = await request.get(`${API_URL}/v1/customers/${data.customerId}`, { headers: data.headers });
    expect(customer.status()).toBe(200); expect((await customer.json()).name).toBe(data.customerName);
    await capture(view.page, "stale-form-permission-denied-desktop");
    await view.page.reload(); await expect(view.page.getByRole("button", { name: "고객 등록", exact: true })).toHaveCount(0);
    expect((await context.cookies()).find(cookie => cookie.name === "me_session")?.value === admin.session.token).toBe(true);
    result("stale-action-result", { passed: true, same_cookie_preserved: true, stale_management_form_denied: true, tampered_operation_denied: true, target_customer_unchanged: true });
  } catch (error) { throw safeFailure(error, secrets); }
  finally { await context?.close(); }
});

test("수집 전용과 플랫폼 운영자의 조회 소속이 화면과 실제 API에서 독립적으로 동작한다", async ({ browser, request }) => {
  const secrets = newSecrets(); const contexts: BrowserContext[] = [];
  try {
    const data = await fixture(request, secrets); const ingest = await member(request, data, secrets, "ingest-ui", ["ingest"]);
    const view = await browserPage(browser, ingest.session, 390); contexts.push(view.context);
    for (const path of ["/overview", "/customers", "/pricing", "/payments"]) {
      await view.page.goto(path);
      await expect(view.page.getByRole("heading", { name: "업무 조회 권한이 없습니다", exact: true })).toBeVisible();
      await expect(view.page.getByRole("link", { name: "다른 도입사 선택", exact: true })).toBeVisible();
      await expect(view.page.getByRole("button", { name: "로그아웃", exact: true })).toBeVisible();
      await expect(view.page).not.toHaveURL(/\/login/);
    }
    await capture(view.page, "ingest-only-mobile");
    const operatorEmail = `permissions-operator-${randomUUID()}@fe-e2e.local`;
    const created = await request.post(`${API_URL}/v1/user-accounts`, { headers: data.operatorHeaders, data: { email: operatorEmail, name: "조회 소속 플랫폼 운영자", password: PASSWORD } });
    expect(created.status()).toBe(201); const operatorId = uuid((await created.json()).id);
    const operator = await login(request, operatorEmail, secrets);
    const platform = await browserPage(browser, operator); contexts.push(platform.context);
    await platform.page.goto("/admin/users");
    const card = platform.page.locator("article.organization-card").filter({ hasText: operatorEmail });
    await card.getByRole("button", { name: "도입사 소속 연결", exact: true }).click();
    const dialog = platform.page.getByRole("dialog");
    await expect(dialog.getByRole("checkbox", { name: READ })).toBeChecked();
    await dialog.getByRole("combobox").selectOption(data.organizationId);
    await capture(platform.page, "platform-attach-read-default-desktop");
    await dialog.getByRole("button", { name: "소속 연결", exact: true }).click();
    await expect.poll(async () => (await (await request.get(`${API_URL}/v1/organization/members`, { headers: data.headers })).json()).members.some((item: Member) => item.user_account_id === operatorId)).toBe(true);
    const switched = await request.put(`${API_URL}/v1/sessions/current/organization`, { headers: authorization(operator.token), data: { organization_id: data.organizationId } });
    expect(switched.status()).toBe(200); expect((await switched.json()).scopes).toEqual(["read"]);
    await platform.page.goto("/customers"); await expect(platform.page.getByRole("button", { name: "고객 등록", exact: true })).toHaveCount(0);
    await expect(platform.page.locator(".workspace-scope")).toHaveText("조회 전용");
    await expect(platform.page.getByRole("button", { name: "권한 새로 조회", exact: true })).toBeVisible();
    await platform.page.goto("/admin/users"); await expect(platform.page.getByRole("heading", { name: "사용자와 플랫폼 권한", exact: true })).toBeVisible();
    expect((await request.get(`${API_URL}/v1/organizations`, { headers: authorization(operator.token) })).status()).toBe(200);
    expect((await request.post(`${API_URL}/v1/customers`, { headers: authorization(operator.token), data: { name: "플랫폼 역할 우회 시도" } })).status()).toBe(403);
    result("ingest-operator-result", { passed: true, ingest_session_and_logout_available: true, ingest_no_login_redirect_loop: true, platform_attach_defaults_read: true, operator_platform_read_allowed: true, operator_tenant_write_denied: true, mobile_width: 390 });
  } catch (error) { throw safeFailure(error, secrets); }
  finally { await Promise.all(contexts.map(context => context.close())); }
});
