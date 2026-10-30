import { expect, test, type APIRequestContext, type APIResponse, type Page } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { closeSync, fchmodSync, mkdirSync, openSync, writeFileSync } from "node:fs";
import { CONSENT_DOCUMENTS, CONSENT_VERSION } from "../src/lib/auth/consents";
import { ENVIRONMENT_LABELS } from "../src/lib/environment";
import { formatKoreanMonth } from "../src/lib/format";

const API_URL = process.env.S1_API_URL ?? "http://127.0.0.1:38147";
const PG_URL = "http://127.0.0.1:38447";
const PASSWORD = "local-environment-e2e-password";
const OPERATOR_EMAIL = process.env.E2E_OPERATOR_EMAIL ?? "operator@fe-e2e.local";
const OPERATOR_PASSWORD = process.env.E2E_OPERATOR_PASSWORD ?? "local-operator-e2e-password";
const ARTIFACT_DIR = process.env.A6_EVIDENCE_DIR ?? "/private/tmp/meterengine-a6-full-2026-10-27";
type Environment = "test" | "live";
type Scope = { environment: Environment; headers: Record<string, string>; key: string; customerId: string; invoiceId?: string; total: number };

async function json<T>(response: APIResponse, status = 200): Promise<T> {
  expect(response.status()).toBe(status);
  return response.json() as Promise<T>;
}

async function state(request: APIRequestContext, scope: Scope) {
  return json<{ invoice_payments: { invoice_id: string; status: string; mode: string; amount: number; canceled_amount: number }[] }>(await request.get(`${API_URL}/v1/invoice-payments`, { headers: scope.headers }));
}

async function confirmAction(page: Page, name: string) {
  await page.getByRole("button", { name, exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByRole("button", { name, exact: true }).click();
  await expect(dialog).toHaveCount(0);
}

test("한 조직의 test와 live가 같은 고객 외부 ID와 미터 코드로 각각 청구, 결제, 취소하고 서로 영향을 주지 않는다", async ({ page, browser, request }) => {
  test.setTimeout(180_000);
  expect(await json(await request.get(`${PG_URL}/health`))).toEqual({ provider: "controlled-local-fixture" });
  const suffix = randomUUID();
  const month = new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit" }).format(new Date());
  const secrets = new Set([PASSWORD, OPERATOR_PASSWORD]);
  try {
    const operator = await json<{ token: string }>(await request.post(`${API_URL}/v1/sessions`, { data: { email: OPERATOR_EMAIL, password: OPERATOR_PASSWORD } }), 201);
    secrets.add(operator.token);
    const email = `environment-${suffix}@fe-e2e.local`;
    const signup = await json<{ organization: { id: string } }>(await request.post(`${API_URL}/v1/signups`, { data: { organization_name: `환경 격리 ${suffix}`, name: "환경 검증 대표", email, password: PASSWORD, consents: CONSENT_DOCUMENTS.map(({ document }) => ({ document, document_version: CONSENT_VERSION })) } }), 201);
    const organizationId = signup.organization.id;
    await json(await request.put(`${API_URL}/v1/organizations/${organizationId}/status`, { headers: { Authorization: `Bearer ${operator.token}` }, data: { status: "active" } }));
    const session = await json<{ token: string }>(await request.post(`${API_URL}/v1/sessions`, { data: { email, password: PASSWORD } }), 201);
    secrets.add(session.token);
    await page.context().addCookies([{ name: "me_session", value: session.token, url: "http://localhost:33147", httpOnly: true, sameSite: "Lax" }]);
    const memberHeaders = { Authorization: `Bearer ${session.token}`, "X-MeterEngine-Organization-Id": organizationId };
    const href = (path: string, environment: Environment) => `${path}?${new URLSearchParams({ organization_id: organizationId, environment, month })}`;
    const metricCode = `env-units-${suffix}`;
    const scopes: Scope[] = [];
    for (const environment of ["test", "live"] as const) {
      const headers = { ...memberHeaders, "X-MeterEngine-Environment": environment };
      const customer = await json<{ id: string }>(await request.post(`${API_URL}/v1/customers`, { headers, data: { name: `${environment} 고객 ${suffix}`, external_id: `same-external-${suffix}`, billing_email: `${environment}-${suffix}@example.test` } }), 201);
      await json(await request.post(`${API_URL}/v1/billable-metrics`, { headers, data: { code: metricCode, name: `${environment} 미터`, event_type: metricCode, aggregation: "sum", target_property: "units", dimension_properties: [] } }), 201);
      const unitPrice = environment === "test" ? 100 : 200;
      const units = environment === "test" ? 2 : 5;
      await json(await request.post(`${API_URL}/v1/billable-metrics/${metricCode}/price-rates`, { headers, data: { model: "per_unit", dimension_values: {}, effective_from: month, unit_price: unitPrice } }), 201);
      const issued = await json<{ secret: string }>(await request.post(`${API_URL}/v1/api-keys`, { headers, data: { label: `${environment} 검증 키`, mode: environment, scopes: ["read", "ingest"] } }), 201);
      secrets.add(issued.secret);
      const keyHeaders = { Authorization: `Bearer ${issued.secret}` };
      await json(await request.post(`${API_URL}/v1/events`, { headers: keyHeaders, data: { transaction_id: `same-transaction-${suffix}`, customer_id: customer.id, type: metricCode, timestamp: new Date(Date.now() - 60_000).toISOString(), properties: { units } } }));
      const rejected = await request.get(`${API_URL}/v1/customers`, { headers: { ...keyHeaders, "X-MeterEngine-Environment": environment === "test" ? "live" : "test" } });
      expect(rejected.status()).toBe(403);
      const scope: Scope = { environment, headers, key: issued.secret, customerId: customer.id, total: Math.round(unitPrice * units * 1.1) };
      scopes.push(scope);
      await page.goto(href("/settings", environment));
      await expect(page.getByLabel(/^대표자명/)).toHaveValue("");
      await page.getByLabel(/^사업자등록번호/).fill(environment === "test" ? "1234567890" : "9876543210");
      await page.getByLabel(/^대표자명/).fill(`${environment} 대표`);
      await page.getByLabel(/^사업장 주소/).fill(`${environment} 사업장 주소`);
      await page.getByRole("button", { name: "공급자 정보 저장", exact: true }).click();
      await expect(page.getByText("처리했습니다.", { exact: true })).toBeVisible();
      await expect(page.getByRole("button", { name: "결제 모드 변경", exact: true })).toHaveCount(0);
      await expect(page.getByText("결제 모드를 바꾸려면 상단 업무 환경을 전환하세요.", { exact: false })).toBeVisible();
      await page.getByRole("button", { name: "토스 키 저장", exact: true }).click();
      const credentials = page.getByRole("dialog");
      await expect(credentials.locator('input[name="mode"]')).toHaveValue(environment);
      await credentials.getByLabel(/^클라이언트 키/).fill(`${environment}_ck_local_a6_client`);
      await credentials.getByLabel(/^시크릿 키/).fill(`${environment}_sk_local_a6_secret`);
      await credentials.getByRole("button", { name: "토스 키 저장", exact: true }).click();
      await expect(credentials).toHaveCount(0);
      await page.goto(href("/billing", environment));
      await page.getByRole("button", { name: "월 전체 확정", exact: true }).click();
      await page.getByRole("dialog").getByRole("button", { name: "확정", exact: true }).click();
      await expect(page.getByRole("dialog")).toContainText("1건을 확정했습니다");
      const invoices = await json<{ invoices: { id: string; customer_id: string; total_amount: number }[] }>(await request.get(`${API_URL}/v1/invoices?month=${month}`, { headers }));
      expect(invoices.invoices).toHaveLength(1);
      expect(invoices.invoices[0]).toMatchObject({ customer_id: customer.id, total_amount: scope.total });
      scope.invoiceId = invoices.invoices[0].id;
      await page.goto(href(`/customers/${customer.id}`, environment));
      await page.getByRole("button", { name: "카드 등록 링크 만들기", exact: true }).click();
      await page.getByRole("dialog").getByRole("button", { name: "카드 등록 링크 만들기", exact: true }).click();
      const registration = page.getByRole("dialog").getByRole("link", { name: "카드 등록 링크 열기", exact: true });
      await expect(registration).toBeVisible();
      const registrationPath = (await registration.getAttribute("href"))!;
      const customerContext = await browser.newContext();
      try {
        const card = await customerContext.newPage();
        await card.route("https://js.tosspayments.com/v2/standard", route => route.fulfill({ contentType: "application/javascript", body: `window.TossPayments = () => ({payment: ({customerKey}) => ({requestBillingAuth: async (options) => { const url = new URL(options.successUrl); url.searchParams.set("authKey", "mvp-auth-local-a6-${environment}-${suffix}"); url.searchParams.set("customerKey", customerKey); location.assign(url); }})});` }));
        await card.goto(`http://localhost:33147${registrationPath}`);
        await expect(card.getByText(environment === "test" ? "테스트 카드 등록" : "실제 결제용 카드 등록", { exact: true })).toBeVisible();
        await card.getByRole("checkbox").check();
        await card.getByRole("button", { name: "토스에서 카드 등록하기", exact: true }).click();
        await expect(card.getByRole("heading", { name: "카드를 등록했습니다", exact: true })).toBeVisible();
      } finally { await customerContext.close(); }
      await page.goto(href(`/payments/${scope.invoiceId}`, environment));
      await confirmAction(page, "등록 카드로 결제");
      expect((await state(request, scope)).invoice_payments).toEqual([expect.objectContaining({ invoice_id: scope.invoiceId, mode: environment, amount: scope.total, status: "paid", canceled_amount: 0 })]);
    }
    expect(scopes[0].customerId).not.toBe(scopes[1].customerId);
    expect(scopes[0].invoiceId).not.toBe(scopes[1].invoiceId);
    mkdirSync(ARTIFACT_DIR, { recursive: true, mode: 0o700 });
    const fixture = openSync(`${ARTIFACT_DIR}/environment-credentials.json`, "w", 0o600);
    try {
      fchmodSync(fixture, 0o600);
      writeFileSync(fixture, JSON.stringify({ organizationId, email, password: PASSWORD, token: session.token, month, metricCode, baseUrl: API_URL, scopes: scopes.map(scope => ({ environment: scope.environment, key: scope.key, customerId: scope.customerId, invoiceId: scope.invoiceId, total: scope.total })) }));
    } finally { closeSync(fixture); }
    for (const [index, scope] of scopes.entries()) {
      const other = scopes[1 - index];
      const otherBefore = await state(request, other);
      await page.goto(href(`/payments/${scope.invoiceId}`, scope.environment));
      await page.getByRole("button", { name: "잔액 전액 취소", exact: true }).click();
      const cancellation = page.getByRole("dialog");
      await cancellation.getByLabel(/^취소 사유/).fill(`${scope.environment} 격리 취소`);
      await cancellation.getByRole("button", { name: /전액 취소 요청$/ }).click();
      await expect(cancellation).toHaveCount(0);
      expect((await state(request, scope)).invoice_payments[0]).toMatchObject({ status: "canceled", canceled_amount: scope.total });
      expect(await state(request, other)).toEqual(otherBefore);
      const inaccessible = await request.get(`${API_URL}/v1/invoices/${scope.invoiceId}`, { headers: other.headers });
      expect(inaccessible.status()).toBe(404);
      const query = await json<{ items: { id: string }[] }>(await request.get(`${API_URL}/v1/customers/search?q=${suffix}`, { headers: scope.headers }));
      expect(query.items.map(customer => customer.id)).toEqual([scope.customerId]);
      await page.getByRole("combobox", { name: "업무 환경", exact: true }).click();
      await page.locator(".ant-select-item-option", { hasText: ENVIRONMENT_LABELS[other.environment] }).click();
      await page.getByRole("dialog").getByRole("button", { name: "전환", exact: true }).click();
      await expect.poll(() => new URL(page.url()).pathname).toBe("/payments");
      expect(new URL(page.url()).searchParams.get("environment")).toBe(other.environment);
      await expect(page.getByText("데이터를 불러오지 못했습니다", { exact: true })).toHaveCount(0);
    }
    expect((await json<{ customers: unknown[] }>(await request.get(`${API_URL}/v1/customers`, { headers: memberHeaders }))).customers).toHaveLength(0);
    for (const scope of scopes) {
      for (const screen of ["overview", "settings", "events", "payments"]) {
        await page.goto(href(`/${screen}`, scope.environment));
        const heading = { overview: `${formatKoreanMonth(month)} 과금 운영`, settings: "도입사 설정", events: "수집 로그", payments: "수납과 취소" }[screen];
        await expect(page.getByRole("heading", { level: 1, name: heading, exact: true })).toBeVisible();
        await expect(page.locator(".workspace-environment")).toContainText(ENVIRONMENT_LABELS[scope.environment]);
        await expect(page.getByText("업무 화면을 준비하고 있습니다.", { exact: true })).toHaveCount(0);
        if (screen === "events") {
          await expect(page.locator("form#event-search")).toBeVisible();
          await expect(page.getByText("조회 조건을 준비하고 있습니다.", { exact: true })).toHaveCount(0);
          await expect(page.getByText("불러오는 중...", { exact: true })).toHaveCount(0);
          await expect(page.getByRole("cell", { name: `${scope.environment} 고객 ${suffix}`, exact: true }).first()).toBeVisible();
        }
        await expect(page.getByText("데이터를 불러오지 못했습니다", { exact: true })).toHaveCount(0);
        await page.screenshot({ path: `${ARTIFACT_DIR}/a6-${scope.environment}-${screen}-desktop.png`, fullPage: true });
      }
      await page.setViewportSize({ width: 390, height: 844 });
      await expect(page.getByRole("heading", { level: 1, name: "수납과 취소", exact: true })).toBeVisible();
      await expect(page.locator(".workspace-environment")).toContainText(ENVIRONMENT_LABELS[scope.environment]);
      await expect(page.getByText("업무 화면을 준비하고 있습니다.", { exact: true })).toHaveCount(0);
      await expect(page.locator(".workspace-mobile-records").getByText(`${scope.environment} 고객 ${suffix}`, { exact: true })).toBeVisible();
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
      await page.screenshot({ path: `${ARTIFACT_DIR}/a6-${scope.environment}-payments-mobile.png`, fullPage: true });
      await page.setViewportSize({ width: 1280, height: 900 });
    }
  } catch (error) {
    let message = error instanceof Error ? error.message : "환경 격리 검증에 실패했습니다";
    for (const secret of secrets) message = message.split(secret).join("[redacted]");
    throw new Error(message.replace(/me_(?:test|live)_[A-Za-z0-9]+/g, "[redacted]"));
  }
});
