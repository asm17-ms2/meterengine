import { expect, test, type APIRequestContext, type BrowserContext, type Page } from "@playwright/test";
import { createHash, randomBytes, randomUUID } from "node:crypto";
import { execFileSync } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";
import { CONSENT_DOCUMENTS, CONSENT_VERSION } from "../src/lib/auth/consents";

const API_URL = process.env.S1_API_URL ?? "http://127.0.0.1:38147";
const FRONTEND_URL = process.env.INVOICE_LINK_FRONTEND_URL ?? "http://localhost:33147";
const OUTPUT = process.env.INVOICE_LINK_EVIDENCE_DIR ?? "/private/tmp/meterengine-invoice-link-2026-10-27";
const PASSWORD = "local-invoice-link-e2e-password";
const OPERATOR_EMAIL = process.env.E2E_OPERATOR_EMAIL ?? "operator@fe-e2e.local";
const OPERATOR_PASSWORD = process.env.E2E_OPERATOR_PASSWORD ?? "local-operator-e2e-password";
const DB_CONTAINER = process.env.E2E_DB_CONTAINER ?? "meterengine-0696-e2e";

test.use({ trace: "off", screenshot: "off", video: "off" });

let previousCopyPrompt: string | undefined;
test.beforeAll(() => {
  previousCopyPrompt = process.env.PLAYWRIGHT_NO_COPY_PROMPT;
  process.env.PLAYWRIGHT_NO_COPY_PROMPT = "1";
  mkdirSync(OUTPUT, { recursive: true });
  for (const url of [API_URL, FRONTEND_URL]) {
    const parsed = new URL(url);
    if (parsed.protocol !== "http:" || !["127.0.0.1", "localhost", "[::1]"].includes(parsed.hostname)) {
      throw new Error("인보이스 링크 검증은 로컬 HTTP 서버에서만 실행합니다.");
    }
  }
});
test.afterAll(() => {
  if (previousCopyPrompt === undefined) delete process.env.PLAYWRIGHT_NO_COPY_PROMPT;
  else process.env.PLAYWRIGHT_NO_COPY_PROMPT = previousCopyPrompt;
});

type Fixture = {
  organizationId: string;
  organizationName: string;
  token: string;
  headers: Record<string, string>;
  readHeaders: Record<string, string>;
  customerId: string;
  customerName: string;
  invoiceId: string;
  invoiceNumber: string;
  otherInvoiceId: string;
  month: string;
};

function safeFailure(error: unknown, secrets: Set<string>) {
  let message = error instanceof Error ? error.message : "인보이스 링크 검증을 마치지 못했습니다.";
  for (const secret of secrets) if (secret) message = message.split(secret).join("[redacted]");
  return new Error(message.replace(/me_(?:test|live|invoice|view|session)_[A-Za-z0-9_-]+/g, "[redacted token]"));
}

function sql(statement: string) {
  return execFileSync("docker", ["exec", "-i", DB_CONTAINER, "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", "meterengine", "-d", "meterengine", "-At"], {
    input: statement, encoding: "utf8", stdio: ["pipe", "pipe", "pipe"],
  }).trim();
}

function uuid(value: string) {
  if (!/^[a-f\d-]{36}$/i.test(value)) throw new Error("검증 데이터의 UUID가 잘못됐습니다.");
  return value;
}

async function fixture(request: APIRequestContext, secrets: Set<string>): Promise<Fixture> {
  const suffix = randomUUID();
  const organizationName = `조회 링크 검증 ${suffix}`;
  const email = `invoice-link-${suffix}@fe-e2e.local`;
  const month = new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit" }).format(new Date());
  const signup = await request.post(`${API_URL}/v1/signups`, { data: {
    organization_name: organizationName, name: "조회 링크 담당자", email, password: PASSWORD,
    consents: CONSENT_DOCUMENTS.map(({ document }) => ({ document, document_version: CONSENT_VERSION })),
  } });
  expect(signup.status()).toBe(201);
  const organizationId = uuid((await signup.json()).organization.id);
  const operator = await request.post(`${API_URL}/v1/sessions`, { data: { email: OPERATOR_EMAIL, password: OPERATOR_PASSWORD } });
  expect(operator.status()).toBe(201);
  const operatorToken = (await operator.json()).token as string;
  secrets.add(operatorToken);
  expect((await request.put(`${API_URL}/v1/organizations/${organizationId}/status`, { headers: { Authorization: `Bearer ${operatorToken}` }, data: { status: "active" } })).status()).toBe(200);
  const session = await request.post(`${API_URL}/v1/sessions`, { data: { email, password: PASSWORD } });
  expect(session.status()).toBe(201);
  const token = (await session.json()).token as string;
  secrets.add(token);
  const headers = { Authorization: `Bearer ${token}` };
  const key = await request.post(`${API_URL}/v1/api-keys`, { headers, data: { label: "고객 인보이스 조회 검증", mode: "test", scopes: ["read", "ingest"] } });
  expect(key.status()).toBe(201);
  const readKey = (await key.json()).secret as string;
  secrets.add(readKey);
  const readHeaders = { Authorization: `Bearer ${readKey}` };
  const customerName = "링크를 전달받은 고객";
  const customer = await request.post(`${API_URL}/v1/customers`, { headers, data: { name: customerName, external_id: `invoice-link-${suffix}`, billing_email: "private-recipient@fe-e2e.local" } });
  expect(customer.status()).toBe(201);
  const customerId = uuid((await customer.json()).id);
  const other = await request.post(`${API_URL}/v1/customers`, { headers, data: { name: "공개되면 안 되는 다른 고객", external_id: `invoice-link-other-${suffix}` } });
  expect(other.status()).toBe(201);
  const otherId = uuid((await other.json()).id);
  const code = `invoice-link-${suffix}`;
  expect((await request.post(`${API_URL}/v1/billable-metrics`, { headers, data: { code, name: "모델 요청", event_type: code, aggregation: "sum", target_property: "units", dimension_properties: [] } })).status()).toBe(201);
  expect((await request.post(`${API_URL}/v1/billable-metrics/${code}/price-rates`, { headers, data: { dimension_values: {}, unit_price: 100, effective_from: month } })).status()).toBe(201);
  expect((await request.post(`${API_URL}/v1/event-batches`, { headers: readHeaders, data: { events: [customerId, otherId].map((id, index) => ({ transaction_id: `invoice-link-${suffix}-${index}`, customer_id: id, type: code, timestamp: new Date(Date.now() - 60_000).toISOString(), properties: { units: (index + 1) * 10 } })) } })).status()).toBe(200);
  const finalized = await request.post(`${API_URL}/v1/invoices?month=${month}`, { headers });
  expect(finalized.status()).toBe(200);
  const list = await request.get(`${API_URL}/v1/invoices?month=${month}`, { headers });
  expect(list.status()).toBe(200);
  const invoices = (await list.json()).invoices as { id: string; invoice_number: string; customer_id: string; total_amount: number }[];
  const invoice = invoices.find(row => row.customer_id === customerId);
  const otherInvoice = invoices.find(row => row.customer_id === otherId);
  if (!invoice || !otherInvoice || invoice.total_amount !== 1100) throw new Error("조회 검증 청구서를 만들지 못했습니다.");
  if (!invoice.invoice_number) throw new Error("고객에게 표시할 청구서 번호가 없습니다.");
  return { organizationId, organizationName, token, headers, readHeaders, customerId, customerName, invoiceId: uuid(invoice.id), invoiceNumber: invoice.invoice_number, otherInvoiceId: uuid(otherInvoice.id), month };
}

async function mint(request: APIRequestContext, data: Fixture, secrets: Set<string>, days?: number) {
  const result = await request.post(`${API_URL}/v1/invoices/${data.invoiceId}/view-links`, { headers: data.headers, data: days === undefined ? {} : { expires_in_days: days } });
  expect(result.status()).toBe(201);
  const link = await result.json() as { id: string; token: string; created_at: string; expires_at: string; mode: string };
  if (!link.token || typeof link.token !== "string") throw new Error("조회 링크의 일회 표시 토큰이 없습니다.");
  secrets.add(link.token);
  return link;
}

async function lookup(request: APIRequestContext, token: string) {
  return request.post(`${API_URL}/v1/public/invoice-links/lookup`, { data: { token } });
}

async function assertLayout(page: Page, file: string) {
  const width = await page.evaluate(() => ({ viewport: window.innerWidth, document: document.documentElement.scrollWidth }));
  expect(width.document).toBeLessThanOrEqual(width.viewport);
  await page.screenshot({ path: `${OUTPUT}/${file}`, fullPage: true });
}

test("발급 권한, 인보이스 경계와 공개 조회의 결제 및 취소 금액을 검증한다", async ({ request }) => {
  const secrets = new Set([PASSWORD, OPERATOR_PASSWORD]);
  try {
    const data = await fixture(request, secrets);
    const link = await mint(request, data, secrets);
    expect(link.mode).toBe("test");
    expect(Date.parse(link.expires_at) - Date.parse(link.created_at)).toBe(30 * 86_400_000);
    const failed = await request.post(`${API_URL}/v1/invoices/${data.invoiceId}/view-links`, { headers: data.readHeaders, data: {} });
    expect(failed.status()).toBe(403);
    expect((await request.post(`${API_URL}/v1/invoices/${data.invoiceId}/view-links`, { data: {} })).status()).toBe(401);
    expect((await request.get(`${API_URL}/v1/invoices`, { headers: { Authorization: `Bearer ${link.token}` } })).status()).toBe(401);
    for (const days of [0, 91]) expect((await request.post(`${API_URL}/v1/invoices/${data.invoiceId}/view-links`, { headers: data.headers, data: { expires_in_days: days } })).status()).toBe(400);
    const other = await fixture(request, secrets);
    expect((await request.get(`${API_URL}/v1/invoices/${data.invoiceId}/view-links`, { headers: other.headers })).status()).toBe(404);
    expect((await request.post(`${API_URL}/v1/invoices/${data.invoiceId}/view-links`, { headers: other.headers, data: {} })).status()).toBe(404);
    expect((await request.post(`${API_URL}/v1/invoices/${data.invoiceId}/view-links/${link.id}/revoke`, { headers: other.headers })).status()).toBe(404);
    const metadata = await request.get(`${API_URL}/v1/invoices/${data.invoiceId}/view-links`, { headers: data.readHeaders });
    expect(metadata.status()).toBe(200);
    expect((await metadata.json()).invoice_view_links).toHaveLength(1);
    expect((await metadata.text()).includes(link.token)).toBe(false);
    const first = await lookup(request, link.token);
    expect(first.status()).toBe(200);
    expect(first.headers()["cache-control"]).toContain("no-store");
    expect(first.headers()["referrer-policy"]).toBe("no-referrer");
    const publicData = await first.json();
    expect(publicData).toMatchObject({ invoice_number: data.invoiceNumber, organization_name: data.organizationName, customer_name: data.customerName, month: data.month, supply_amount: 1000, tax_amount: 100, total_amount: 1100, payment: { status: "unpaid", mode: "test", paid_amount: 0, canceled_amount: 0, outstanding_amount: 1100 } });
    const serialized = JSON.stringify(publicData);
    for (const forbidden of [data.organizationId, data.customerId, data.invoiceId, data.otherInvoiceId, "private-recipient@fe-e2e.local", "공개되면 안 되는 다른 고객", ...secrets]) expect(serialized.includes(forbidden)).toBe(false);
    expect((await (await lookup(request, link.token)).json())).toEqual(publicData);
    const pendingLink = await mint(request, { ...data, invoiceId: data.otherInvoiceId }, secrets, 7);
    sql(`INSERT INTO payment_attempt (id, organization_id, customer_id, invoice_id, mode, amount, order_name, status, requested_at) SELECT '${randomUUID()}', organization_id, customer_id, id, 'test', supply_amount + tax_amount, 'local invoice link pending fixture', 'pending', clock_timestamp() FROM invoice WHERE id = '${data.otherInvoiceId}';`);
    expect((await (await lookup(request, pendingLink.token)).json()).payment).toMatchObject({ status: "pending", mode: "test", paid_amount: null, canceled_amount: 0, outstanding_amount: null });
    const paymentId = randomUUID();
    sql(`INSERT INTO payment_attempt (id, organization_id, customer_id, invoice_id, mode, amount, order_name, status, payment_key, requested_at, completed_at) VALUES ('${paymentId}', '${data.organizationId}', '${data.customerId}', '${data.invoiceId}', 'test', 1100, 'local invoice link fixture', 'done', 'local-link-payment-${paymentId}', clock_timestamp(), clock_timestamp());`);
    expect((await (await lookup(request, link.token)).json()).payment).toMatchObject({ status: "paid", paid_amount: 1100, canceled_amount: 0, outstanding_amount: 0 });
    const cancel = (amount: number) => sql(`INSERT INTO payment_cancellation (id, organization_id, payment_attempt_id, mode, amount, reason, status, transaction_key, requested_at, completed_at) VALUES ('${randomUUID()}', '${data.organizationId}', '${paymentId}', 'test', ${amount}, 'local invoice link fixture', 'done', 'local-link-cancel-${randomUUID()}', clock_timestamp(), clock_timestamp());`);
    cancel(400);
    const partial = await (await lookup(request, link.token)).json();
    expect(partial).toMatchObject({ total_amount: 1100, payment: { status: "partially_canceled", paid_amount: 700, canceled_amount: 400, outstanding_amount: 0 } });
    cancel(700);
    expect((await (await lookup(request, link.token)).json()).payment).toMatchObject({ status: "canceled", paid_amount: 0, canceled_amount: 1100, outstanding_amount: 0 });
    expect((await request.delete(`${API_URL}/v1/customers/${data.customerId}`, { headers: data.headers })).status()).toBe(204);
    expect((await (await lookup(request, link.token)).json())).toMatchObject({ invoice_number: data.invoiceNumber, customer_name: data.customerName, total_amount: 1100 });
    sql(`INSERT INTO payment_setting (organization_id, mode, updated_at) VALUES ('${data.organizationId}', 'live', clock_timestamp()) ON CONFLICT (organization_id) DO UPDATE SET mode = 'live', updated_at = clock_timestamp();`);
    expect((await (await lookup(request, link.token)).json()).payment).toMatchObject({ mode: "test", status: "canceled", canceled_amount: 1100, outstanding_amount: 0 });
    const digest = createHash("sha256").update(link.token).digest("hex");
    expect(sql(`SELECT encode(token_hash, 'hex') = '${digest}' FROM invoice_view_link WHERE id = '${uuid(link.id)}';`)).toBe("t");
    expect((await request.post(`${API_URL}/v1/invoices/${data.otherInvoiceId}/view-links/${link.id}/revoke`, { headers: data.headers })).status()).toBe(404);
    expect((await request.post(`${API_URL}/v1/invoices/${data.invoiceId}/view-links/${link.id}/revoke`, { headers: data.readHeaders })).status()).toBe(403);
    const revoked = await request.post(`${API_URL}/v1/invoices/${data.invoiceId}/view-links/${link.id}/revoke`, { headers: data.headers });
    expect(revoked.status()).toBe(200);
    const unknownToken = `me_invoice_${randomBytes(16).toString("hex")}`;
    const invalid = await lookup(request, unknownToken);
    const expiredToken = `me_invoice_${randomBytes(16).toString("hex")}`;
    secrets.add(expiredToken);
    const expiredHash = createHash("sha256").update(expiredToken).digest("hex");
    sql(`INSERT INTO invoice_view_link (id, organization_id, invoice_id, token_hash, mode, created_at, expires_at) VALUES ('${randomUUID()}', '${data.organizationId}', '${data.invoiceId}', decode('${expiredHash}', 'hex'), 'test', clock_timestamp() - interval '2 days', clock_timestamp() - interval '1 day');`);
    const expired = await lookup(request, expiredToken);
    const retired = await lookup(request, link.token);
    expect(retired.status()).toBe(404);
    expect(invalid.status()).toBe(404);
    expect(expired.status()).toBe(404);
    for (const token of ["", null, "x".repeat(257)]) expect((await request.post(`${API_URL}/v1/public/invoice-links/lookup`, { data: { token } })).status()).toBe(404);
    expect((await retired.json()).code).toBe("invoice_view_link_not_found");
    expect((await invalid.json()).code).toBe("invoice_view_link_not_found");
    expect((await expired.json()).code).toBe("invoice_view_link_not_found");
    expect((await request.post(`${API_URL}/v1/invoices/${data.otherInvoiceId}/view-links/${pendingLink.id}/revoke`, { headers: data.headers })).status()).toBe(200);
    const audit = await request.get(`${API_URL}/v1/audit-logs?size=100`, { headers: data.headers });
    expect(audit.status()).toBe(200);
    for (const secret of secrets) expect((await audit.text()).includes(secret)).toBe(false);
    expect(Number(sql(`SELECT count(*) FROM audit_log WHERE organization_id = '${data.organizationId}' AND actor_type = 'invoice_view_link';`))).toBeGreaterThan(0);
    writeFileSync(`${OUTPUT}/api-result.json`, JSON.stringify({ passed: true, default_expiry_days: 30, read_key_write_status: failed.status(), anonymous_status: 401, cross_organization_status: 404, reusable: true, payment_states: ["unpaid", "pending", "paid", "partially_canceled", "canceled"], link_mode_preserved: true, archived_customer_invoice_preserved: true, token_stored_as_sha256: true, retired_status: retired.status(), expired_status: expired.status(), audit_token_leak: false }, null, 2));
  } catch (error) { throw safeFailure(error, secrets); }
});

test("콘솔에서 전달한 링크를 고객이 로그인 없이 열고 새로고침하며 폐기 후 접근하지 못한다", async ({ browser, request }) => {
  const secrets = new Set([PASSWORD, OPERATOR_PASSWORD]);
  const contexts: BrowserContext[] = [];
  try {
    const data = await fixture(request, secrets);
    const owner = await browser.newContext({ baseURL: FRONTEND_URL, viewport: { width: 1280, height: 900 } });
    contexts.push(owner);
    await owner.addCookies([{ name: "me_session", value: data.token, domain: "localhost", path: "/", httpOnly: true, sameSite: "Lax" }]);
    await owner.grantPermissions(["clipboard-read", "clipboard-write"], { origin: FRONTEND_URL });
    const consolePage = await owner.newPage();
    await consolePage.goto(`/payments/${data.invoiceId}`);
    const section = consolePage.locator("section").filter({ has: consolePage.getByRole("heading", { name: "고객 조회 링크", exact: true }) });
    await expect(section.getByRole("button", { name: "고객 조회 링크 발급", exact: true })).toBeVisible();
    const period = section.getByRole("combobox", { name: /사용 기간/ });
    await expect(period).toHaveValue("30");
    writeFileSync(`${OUTPUT}/console-label-observation.json`, JSON.stringify({
      exact_role_name_match: await section.getByRole("combobox", { name: "사용 기간", exact: true }).count(),
      exact_label_match: await section.getByLabel("사용 기간", { exact: true }).count(),
      aria_snapshot: await period.ariaSnapshot(),
      label_dom: await period.evaluate(element => (element as HTMLSelectElement).labels?.[0]?.outerHTML ?? ""),
    }, null, 2));
    await section.getByRole("button", { name: "고객 조회 링크 발급", exact: true }).click();
    await expect(section.getByRole("button", { name: "고객 조회 링크 복사", exact: true })).toBeVisible();
    await section.getByRole("button", { name: "고객 조회 링크 복사", exact: true }).click();
    const copied = await consolePage.evaluate(() => navigator.clipboard.readText());
    const shared = new URL(copied);
    const token = shared.hash.slice(1);
    if (!token || shared.pathname !== "/invoice-links" || shared.search !== "") throw new Error("고객 전달 링크가 fragment 방식이 아닙니다.");
    secrets.add(token);
    const customer = await browser.newContext({ baseURL: FRONTEND_URL, viewport: { width: 1280, height: 900 } });
    contexts.push(customer);
    await customer.grantPermissions(["clipboard-read", "clipboard-write"], { origin: FRONTEND_URL });
    const page = await customer.newPage();
    const network: string[] = [];
    page.on("request", item => network.push(item.url()));
    const navigation = await page.goto(copied);
    expect(navigation?.headers()["cache-control"]).toContain("no-store");
    expect(navigation?.headers()["referrer-policy"]).toBe("no-referrer");
    await expect(page.getByRole("heading", { name: /청구서$/, exact: false }).first()).toBeVisible();
    await expect(page.getByText(data.customerName, { exact: true })).toBeVisible();
    await expect(page.getByText(data.invoiceNumber, { exact: true })).toBeVisible();
    expect(page.url()).toBe(`${FRONTEND_URL}/invoice-links`);
    await expect(page.getByRole("heading", { name: "청구 항목", exact: true })).toBeVisible();
    await expect(page.getByText("항목별 공급가액은 수량에 단가를 곱한 금액에서 10원 미만을 버린 값입니다.", { exact: false })).toBeVisible();
    await expect(page.getByRole("heading", { name: "결제 내역", exact: true })).toBeVisible();
    await expect(page.getByRole("button")).toHaveText(["결제 상태 새로 조회", "조회 링크 복사"]);
    for (const value of [token, data.token, data.organizationId, data.customerId, data.invoiceId, "공개되면 안 되는 다른 고객"]) expect((await page.content()).includes(value)).toBe(false);
    await assertLayout(page, "public-invoice-desktop.png");
    await page.reload();
    await expect(page.getByText(data.customerName, { exact: true })).toBeVisible();
    expect(page.url()).toBe(`${FRONTEND_URL}/invoice-links`);
    await page.getByRole("link", { name: "본문으로 이동", exact: true }).focus();
    await page.keyboard.press("Enter");
    await expect(page).toHaveURL(`${FRONTEND_URL}/invoice-links#main-content`);
    await page.reload();
    await expect(page.getByText(data.customerName, { exact: true })).toBeVisible();
    expect((await customer.cookies()).find(cookie => cookie.name === "me_session")).toBeUndefined();
    const paymentId = randomUUID();
    sql(`INSERT INTO payment_attempt (id, organization_id, customer_id, invoice_id, mode, amount, order_name, status, payment_key, requested_at, completed_at) VALUES ('${paymentId}', '${data.organizationId}', '${data.customerId}', '${data.invoiceId}', 'test', 1100, 'local browser link fixture', 'done', 'local-browser-link-payment-${paymentId}', clock_timestamp(), clock_timestamp()); INSERT INTO payment_cancellation (id, organization_id, payment_attempt_id, mode, amount, reason, status, transaction_key, requested_at, completed_at) VALUES ('${randomUUID()}', '${data.organizationId}', '${paymentId}', 'test', 400, 'local browser link fixture', 'done', 'local-browser-link-cancel-${randomUUID()}', clock_timestamp(), clock_timestamp());`);
    const refresh = page.getByRole("button", { name: "결제 상태 새로 조회", exact: true });
    await refresh.focus();
    await refresh.press("Enter");
    await expect(page.getByText("부분 취소", { exact: true })).toBeVisible();
    const paymentSection = page.locator("section").filter({ has: page.getByRole("heading", { name: "결제 내역", exact: true }) });
    await expect(paymentSection.locator("dd")).toContainText(["700", "400", "0"]);
    await page.getByRole("button", { name: "조회 링크 복사", exact: true }).click();
    const customerCopied = await page.evaluate(() => navigator.clipboard.readText());
    if (customerCopied !== copied) throw new Error("주소를 정리한 뒤 고객의 원본 링크 복사를 유지하지 못했습니다.");
    await assertLayout(page, "public-partial-cancellation-desktop.png");
    await page.setViewportSize({ width: 390, height: 844 });
    await assertLayout(page, "public-invoice-mobile.png");
    await expect(page.getByRole("region", { name: "청구 항목 상세", exact: true })).toBeHidden();
    const lineRecords = page.getByRole("list", { name: "청구 항목", exact: true });
    expect(await lineRecords.getByRole("listitem").count()).toBeGreaterThan(0);
    const firstRecord = lineRecords.getByRole("listitem").first();
    await expect(firstRecord).toContainText(/원/);
    await expect(firstRecord).toContainText("단가");
    const recordBox = await firstRecord.boundingBox();
    expect(recordBox && recordBox.x + recordBox.width <= 390).toBe(true);
    writeFileSync(`${OUTPUT}/mobile-line-records-result.json`, JSON.stringify({ records: await lineRecords.getByRole("listitem").count(), right_edge: recordBox ? recordBox.x + recordBox.width : null }, null, 2));
    const secondLink = await mint(request, { ...data, invoiceId: data.otherInvoiceId }, secrets, 7);
    sql(`INSERT INTO payment_attempt (id, organization_id, customer_id, invoice_id, mode, amount, order_name, status, requested_at) SELECT '${randomUUID()}', organization_id, customer_id, id, 'test', supply_amount + tax_amount, 'local browser pending fixture', 'pending', clock_timestamp() FROM invoice WHERE id = '${data.otherInvoiceId}';`);
    await page.goto(`${FRONTEND_URL}/invoice-links#${secondLink.token}`);
    await expect(page.getByText("공개되면 안 되는 다른 고객", { exact: true })).toBeVisible();
    await expect(page.getByText(data.customerName, { exact: true })).toHaveCount(0);
    expect(page.url()).toBe(`${FRONTEND_URL}/invoice-links`);
    await expect(page.getByText("결제 확인 중", { exact: true })).toBeVisible();
    await expect(page.getByText("확인 중", { exact: true })).toHaveCount(2);
    await assertLayout(page, "public-pending-mobile.png");
    await page.goto(copied);
    await expect(page.getByText(data.customerName, { exact: true })).toBeVisible();
    expect(page.url()).toBe(`${FRONTEND_URL}/invoice-links`);
    for (const url of network) {
      for (const secret of secrets) expect(url.includes(secret)).toBe(false);
      if (url.startsWith("http")) expect(new URL(url).origin).toBe(FRONTEND_URL);
    }
    const metadata = await request.get(`${API_URL}/v1/invoices/${data.invoiceId}/view-links`, { headers: data.headers });
    const linkId = (await metadata.json()).invoice_view_links[0].id as string;
    await consolePage.reload();
    await expect(consolePage.getByRole("button", { name: "고객 조회 링크 복사", exact: true })).toHaveCount(0);
    await consolePage.getByRole("button", { name: "링크 폐기", exact: true }).click();
    await consolePage.getByRole("dialog").getByRole("button", { name: "링크 폐기", exact: true }).click();
    await expect.poll(async () => (await lookup(request, token)).status()).toBe(404);
    await expect(section.getByText("폐기됨 / 테스트", { exact: true })).toBeVisible();
    await section.screenshot({ path: `${OUTPUT}/console-links-revoked.png` });
    await page.reload();
    await expect(page.getByRole("heading", { name: "사용할 수 없는 조회 링크입니다", exact: true })).toBeVisible();
    await assertLayout(page, "public-revoked-mobile.png");
    expect((await request.get(`${API_URL}/v1/invoices/${data.invoiceId}/view-links`, { headers: data.headers })).status()).toBe(200);
    expect((await request.post(`${API_URL}/v1/invoices/${data.otherInvoiceId}/view-links/${secondLink.id}/revoke`, { headers: data.headers })).status()).toBe(200);
    writeFileSync(`${OUTPUT}/browser-result.json`, JSON.stringify({ passed: true, invoice_number: data.invoiceNumber, fragment_removed: true, reusable_on_reload: true, customer_session_required: false, network_token_leak: false, external_network_requests: 0, source_customer_id_in_html: false, desktop_width: 1280, mobile_width: 390, revoked_lookup_status: 404, link_id: linkId }, null, 2));
  } catch (error) { throw safeFailure(error, secrets); }
  finally { for (const context of contexts) await context.close(); }
});

test("조회 링크 누락과 일시 조회 실패를 안내하고 원래 링크로 다시 조회한다", async ({ page, request }) => {
  await page.goto(`${FRONTEND_URL}/invoice-links`);
  await expect(page.getByRole("heading", { name: "청구서 조회 정보가 없습니다", exact: true })).toBeVisible();
  await expect(page.getByText("받은 메일이나 메시지의 원래 조회 링크를 다시 열어 주세요.", { exact: false })).toBeVisible();
  await expect(page.getByText(/만료되었거나 폐기/)).toHaveCount(0);
  await expect(page.getByRole("textbox", { name: /고객|인보이스/ })).toHaveCount(0);
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", /noindex/);
  const cross = await request.post(`${FRONTEND_URL}/invoice-links/lookup`, { headers: { Origin: "http://evil.example" }, data: { token: randomBytes(32).toString("base64url") } });
  expect(cross.status()).toBe(403);
  const missing = await request.post(`${FRONTEND_URL}/invoice-links/lookup`, { headers: { Origin: FRONTEND_URL }, data: {} });
  expect(missing.status()).toBe(404);
  expect((await request.post(`${FRONTEND_URL}/invoice-links/lookup`, { headers: { Origin: FRONTEND_URL }, data: { token: "x".repeat(2048) } })).status()).toBe(413);
  expect((await request.post(`${FRONTEND_URL}/invoice-links/lookup`, { headers: { Origin: FRONTEND_URL, "Content-Type": "text/plain" }, data: "not-json" })).status()).toBe(400);
  await assertLayout(page, "public-missing-desktop.png");
  const secrets = new Set([PASSWORD, OPERATOR_PASSWORD]);
  try {
    const data = await fixture(request, secrets);
    const link = await mint(request, data, secrets);
    await page.route("**/invoice-links/lookup", route => route.fulfill({ status: 503, contentType: "application/json", body: '{"message":"local outage fixture"}' }));
    await page.goto(`${FRONTEND_URL}/invoice-links#${link.token}`);
    await expect(page.getByRole("heading", { name: "청구서를 불러오지 못했습니다", exact: true })).toBeVisible();
    await page.setViewportSize({ width: 390, height: 844 });
    await assertLayout(page, "public-unavailable-mobile.png");
    await page.unroute("**/invoice-links/lookup");
    let releaseOutage = () => {};
    const outage = new Promise<void>(resolve => { releaseOutage = resolve; });
    await page.route("**/invoice-links/lookup", async route => { await outage; await route.fulfill({ status: 503, contentType: "application/json", body: '{"message":"local outage fixture"}' }); });
    await page.getByRole("button", { name: "다시 조회", exact: true }).press("Enter");
    await expect(page.getByRole("button", { name: "조회 중", exact: true })).toBeDisabled();
    releaseOutage();
    await expect(page.locator("main").getByRole("status")).toHaveText("다시 조회했지만 청구서를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
    await page.unroute("**/invoice-links/lookup");
    await page.getByRole("button", { name: "다시 조회", exact: true }).press("Enter");
    await expect(page.getByText(data.customerName, { exact: true })).toBeVisible();
    expect(page.url()).toBe(`${FRONTEND_URL}/invoice-links`);
    sql(`INSERT INTO payment_attempt (id, organization_id, customer_id, invoice_id, mode, amount, order_name, status, failure_code, failure_message, requested_at, completed_at) VALUES ('${randomUUID()}', '${data.organizationId}', '${data.customerId}', '${data.invoiceId}', 'test', 1100, 'local failed lookup fixture', 'failed', 'LOCAL_DECLINED', 'local fixture failure', clock_timestamp(), clock_timestamp());`);
    await page.getByRole("button", { name: "결제 상태 새로 조회", exact: true }).click();
    await expect(page.getByText("결제 실패", { exact: true })).toBeVisible();
    await expect(page.getByText("결제 실패", { exact: true })).toHaveCSS("color", "rgb(169, 35, 52)");
    await expect(page.getByText("최근 결제 처리 시각 (KST)", { exact: true })).toBeVisible();
    await expect(page.getByText("결제 완료 시각 (KST)", { exact: true })).toHaveCount(0);
    expect((await (await lookup(request, link.token)).json()).payment).toMatchObject({ status: "failed", paid_amount: 0, canceled_amount: 0, outstanding_amount: 1100 });
    await assertLayout(page, "public-failed-mobile.png");
    expect((await request.post(`${API_URL}/v1/invoices/${data.invoiceId}/view-links/${link.id}/revoke`, { headers: data.headers })).status()).toBe(200);
    writeFileSync(`${OUTPUT}/failure-result.json`, JSON.stringify({ passed: true, missing_link_hint: true, cross_origin_status: cross.status(), malformed_lookup_status: missing.status(), injected_frontend_outage_recovered_against_real_backend: true, failed_payment_processing_timestamp_label: true, failed_amount_krw: 1100 }, null, 2));
  } catch (error) { throw safeFailure(error, secrets); }
});
