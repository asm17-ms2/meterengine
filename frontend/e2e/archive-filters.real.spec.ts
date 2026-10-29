import { expect, test, type Locator, type Page } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { CONSENT_DOCUMENTS, CONSENT_VERSION } from "../src/lib/auth/consents";

const API_URL = process.env.S1_API_URL ?? "http://127.0.0.1:38147";
const PG_URL = "http://127.0.0.1:38447";
const PASSWORD = "local-archive-filter-e2e-password";
const OPERATOR_EMAIL = process.env.E2E_OPERATOR_EMAIL ?? "operator@fe-e2e.local";
const OPERATOR_PASSWORD = process.env.E2E_OPERATOR_PASSWORD ?? "local-operator-e2e-password";

test.use({ trace: "off", screenshot: "off", video: "off" });

let previousCopyPrompt: string | undefined;
test.beforeAll(() => {
  previousCopyPrompt = process.env.PLAYWRIGHT_NO_COPY_PROMPT;
  process.env.PLAYWRIGHT_NO_COPY_PROMPT = "1";
});
test.afterAll(() => {
  if (previousCopyPrompt === undefined) delete process.env.PLAYWRIGHT_NO_COPY_PROMPT;
  else process.env.PLAYWRIGHT_NO_COPY_PROMPT = previousCopyPrompt;
});

function section(page: Page, heading: string) {
  return page.locator("section.workspace-panel").filter({ has: page.getByRole("heading", { name: heading, exact: true }) });
}

async function selectedFilter(page: Page, label: string, value: string) {
  const select = page.getByRole("combobox", { name: label, exact: true });
  const pathname = new URL(page.url()).pathname;
  const isSubmitted = label === "관리 상태" && ["/customers", "/billable-metrics"].includes(pathname) || pathname === "/pricing" && ["미터 관리 상태", "요금제 관리 상태", "단가 관리 상태"].includes(label);
  if (isSubmitted) {
    await expect(select).toBeVisible();
    await expect.poll(() => new URL(page.url()).searchParams.get("organization_id")).toMatch(/^[0-9a-f-]{36}$/);
    const originalUrl = page.url();
    await select.selectOption({ label: value });
    expect(page.url(), "조건 선택만으로 이동하지 않음").toBe(originalUrl);
    await page.getByRole("button", { name: "조건 적용", exact: true }).click();
    await expect(select.locator("option:checked")).toHaveText(value);
  } else {
    const navigation = page.getByRole("navigation", { name: label, exact: true });
    await navigation.getByRole("link", { name: value, exact: true }).click();
    await expect(navigation.getByRole("link", { name: value, exact: true })).toHaveAttribute("aria-current", "page");
  }
}

async function expectRecords(scope: Locator, included: string[], excluded: string[] = []) {
  const records = scope.locator(".workspace-desktop-table");
  for (const name of included) await expect(records.getByRole("row").filter({ hasText: name })).toHaveCount(1);
  for (const name of excluded) await expect(records.getByRole("row").filter({ hasText: name })).toHaveCount(0);
}

function safeFailure(error: unknown, secrets: Set<string>) {
  let message = error instanceof Error ? error.message : "보관 상태 조회 E2E를 마치지 못했습니다.";
  for (const secret of secrets) if (secret) message = message.split(secret).join("[redacted]");
  return new Error(message.replace(/me_(?:test|live|reg|invite)_[A-Za-z0-9]+/g, "[redacted token]"));
}

test("미터 이름과 이름 정렬 머리글이 데스크톱 폭에서 보인다", async ({ page }) => {
  await page.goto("/login?returnTo=%2Fbillable-metrics");
  await page.getByLabel("이메일", { exact: true }).fill("member@meterengine.local");
  await page.getByLabel("비밀번호", { exact: true }).fill("local-member-password");
  await page.getByRole("button", { name: "로그인", exact: true }).click();
  await expect(page).toHaveURL(/\/billable-metrics(?:\?.*)?$/);

  for (const width of [1024, 1280, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    const heading = page.locator("th").filter({ hasText: /^이름$/ });
    await expect(heading).toBeVisible();
    await heading.focus();
    await expect(heading).toBeFocused();
    const name = page.locator(".ant-table-tbody > tr:not(.ant-table-measure-row) > td:nth-child(2)").first();
    await expect(name).toBeVisible();
    await expect(name).not.toBeEmpty();
  }
});

test("보관을 지원하는 모든 도입사 목록에서 상태별 조회와 독립 필터를 유지한다", async ({ browser, request }) => {
  const suffix = randomUUID();
  const email = `archive-owner-${suffix}@fe-e2e.local`;
  const activeName = `사용 고객 ${suffix}`;
  const archivedName = `보관 고객 ${suffix}`;
  const activeMetricName = `사용 미터 ${suffix}`;
  const archivedMetricName = `보관 미터 ${suffix}`;
  const activeMetricCode = `archive-current-${suffix}`;
  const archivedMetricCode = `archive-stopped-${suffix}`;
  const activeKeyLabel = `사용 키 ${suffix}`;
  const revokedKeyLabel = `폐기 키 ${suffix}`;
  const archivedMemberEmail = `archive-member-${suffix}@fe-e2e.local`;
  const activeWebhookUrl = `${PG_URL}/archive-active-${suffix}`;
  const archivedWebhookUrl = `${PG_URL}/archive-stopped-${suffix}`;
  const currentMonth = new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit" }).format(new Date());
  const [year, month] = currentMonth.split("-").map(Number);
  const nextMonth = `${year + (month === 12 ? 1 : 0)}-${String(month === 12 ? 1 : month + 1).padStart(2, "0")}`;
  const context = await browser.newContext({ baseURL: "http://localhost:33147", viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  const secrets = new Set([PASSWORD, OPERATOR_PASSWORD]);
  let failure: unknown;

  try {
    const signup = await request.post(`${API_URL}/v1/signups`, { data: {
      organization_name: `보관 조회 E2E ${suffix}`, name: "조회 대표관리자", email, password: PASSWORD,
      consents: CONSENT_DOCUMENTS.map(({ document }) => ({ document, document_version: CONSENT_VERSION })),
    } });
    expect(signup.status()).toBe(201);
    const organizationId = (await signup.json()).organization.id;
    const operator = await request.post(`${API_URL}/v1/sessions`, { data: { email: OPERATOR_EMAIL, password: OPERATOR_PASSWORD } });
    expect(operator.status()).toBe(201);
    const operatorToken = (await operator.json()).token;
    secrets.add(operatorToken);
    expect((await request.put(`${API_URL}/v1/organizations/${organizationId}/status`, { headers: { Authorization: `Bearer ${operatorToken}` }, data: { status: "active" } })).status()).toBe(200);
    await page.goto("/login?returnTo=%2Fcustomers");
    await page.getByLabel("이메일", { exact: true }).fill(email);
    await page.getByLabel("비밀번호", { exact: true }).fill(PASSWORD);
    await page.getByRole("button", { name: "로그인", exact: true }).click();
    await expect(page).toHaveURL(/\/customers(?:\?.*)?$/);
    const token = (await context.cookies()).find(cookie => cookie.name === "me_session")?.value;
    if (!token) throw new Error("도입사 검증 세션이 없습니다.");
    secrets.add(token);
    const headers = { Authorization: `Bearer ${token}` };
    const post = async <T = { id: string }>(path: string, data: Record<string, unknown>): Promise<T> => {
      const result = await request.post(`${API_URL}${path}`, { headers, data });
      expect(result.status()).toBe(201);
      return result.json();
    };
    const remove = async (path: string) => expect((await request.delete(`${API_URL}${path}`, { headers })).status()).toBe(204);
    let activeCustomerId = "";

    await test.step("각 관리 대상의 사용 중 항목과 보관 이력 준비", async () => {
      activeCustomerId = (await post("/v1/customers", { name: activeName, external_id: `archive-current-${suffix}` })).id;
      const oldCustomer = await post("/v1/customers", { name: archivedName, external_id: `archive-stopped-${suffix}` });
      await remove(`/v1/customers/${oldCustomer.id}`);
      for (const [code, name] of [[activeMetricCode, activeMetricName], [archivedMetricCode, archivedMetricName]]) {
        await post("/v1/billable-metrics", { code, name, event_type: code, aggregation: "sum", target_property: "units", dimension_properties: [] });
        const event = await request.post(`${API_URL}/v1/events`, { headers, data: { transaction_id: randomUUID(), customer_id: activeCustomerId, type: code, properties: { units: 1 }, timestamp: new Date().toISOString() } });
        expect(event.status()).toBe(200);
      }
      await remove(`/v1/billable-metrics/${archivedMetricCode}`);
      const oldRate = await post(`/v1/billable-metrics/${activeMetricCode}/price-rates`, { dimension_values: {}, unit_price: 123, effective_from: currentMonth });
      await remove(`/v1/billable-metrics/${activeMetricCode}/price-rates/${oldRate.id}`);
      await post(`/v1/billable-metrics/${activeMetricCode}/price-rates`, { dimension_values: {}, unit_price: 456, effective_from: nextMonth });
      const oldPlan = await post("/v1/price-plans", { effective_from: currentMonth, base_fee: 1234, minimum_amount: 0 });
      await remove(`/v1/price-plans/${oldPlan.id}`);
      await post("/v1/price-plans", { effective_from: nextMonth, base_fee: 5678, minimum_amount: 0 });
      const member = await post<{ user_account_id: string }>("/v1/organization/members", { email: archivedMemberEmail, name: "보관 관리자", password: PASSWORD });
      await remove(`/v1/organization/members/${member.user_account_id}`);
      for (const label of [activeKeyLabel, revokedKeyLabel]) {
        const key = await post<{ id: string; secret: string }>("/v1/api-keys", { label, mode: "test", scopes: ["read"] });
        secrets.add(key.secret);
        if (label === revokedKeyLabel) await remove(`/v1/api-keys/${key.id}`);
      }
      for (const url of [activeWebhookUrl, archivedWebhookUrl]) {
        const endpoint = await post<{ id: string; secret: string }>("/v1/webhook-endpoints", { url, event_types: ["webhook_test"] });
        secrets.add(endpoint.secret);
        if (url === archivedWebhookUrl) await remove(`/v1/webhook-endpoints/${endpoint.id}`);
      }
      for (const ending of ["old1", "new2"]) {
        const credential = { client_key: `test_ck_archive_${suffix}`, secret_key: `test_sk_archive_${suffix}_${ending}` };
        secrets.add(credential.client_key);
        secrets.add(credential.secret_key);
        expect((await request.put(`${API_URL}/v1/toss-payments-credentials/test`, { headers, data: credential })).status()).toBe(200);
        if (ending === "old1") await remove("/v1/toss-payments-credentials/test");
      }
      expect((await request.put(`${API_URL}/v1/payment-setting`, { headers, data: { mode: "test" } })).status()).toBe(200);
      for (let index = 0; index < 2; index++) {
        const registration = await post<{ token: string; customer_key: string }>(`/v1/customers/${activeCustomerId}/payment-method-registrations`, {});
        secrets.add(registration.token);
        const authKey = `mvp-auth-archive-${suffix}-${index}`;
        secrets.add(authKey);
        const registered = await request.post(`${API_URL}/v1/payment-method-registrations/${registration.token}/billing-keys`, { data: { auth_key: authKey, customer_key: registration.customer_key, consent_document_version: "2026-10-26" } });
        expect(registered.status()).toBe(201);
        if (index === 0) await remove(`/v1/customers/${activeCustomerId}/payment-method`);
      }
    });

    await test.step("고객 검색어와 월을 유지하며 세 관리 상태 조회", async () => {
      await page.goto(`/customers?month=${currentMonth}&q=${encodeURIComponent(suffix)}&page=7`);
      const customers = page.locator("section.workspace-panel");
      await expectRecords(customers, [], [activeName, archivedName]);
      await selectedFilter(page, "관리 상태", "보관됨");
      await expectRecords(customers, [archivedName], [activeName]);
      expect(new URL(page.url()).searchParams.get("q")).toBe(suffix);
      expect(new URL(page.url()).searchParams.get("month")).toBe(currentMonth);
      expect(new URL(page.url()).searchParams.has("page")).toBe(false);
      await selectedFilter(page, "관리 상태", "전체");
      await expectRecords(customers, [activeName, archivedName]);
      await page.reload();
      await expectRecords(customers, [activeName, archivedName]);
    });

    await test.step("미터의 사용 중, 보관됨, 전체를 직접 탐색", async () => {
      await page.goto("/billable-metrics");
      await expect(page.getByRole("row").filter({ hasText: activeMetricName })).toHaveCount(1);
      await expect(page.getByRole("row").filter({ hasText: archivedMetricName })).toHaveCount(0);
      await selectedFilter(page, "관리 상태", "보관됨");
      await expect(page.getByRole("row").filter({ hasText: archivedMetricName })).toHaveCount(1);
      await expect(page.getByRole("row").filter({ hasText: activeMetricName })).toHaveCount(0);
      await selectedFilter(page, "관리 상태", "전체");
      await expect(page.getByRole("row").filter({ hasText: activeMetricName })).toHaveCount(1);
      await expect(page.getByRole("row").filter({ hasText: archivedMetricName })).toHaveCount(1);
    });

    await test.step("조회 필터에서 보관한 고객과 수집된 이벤트 유형을 유지", async () => {
      const filterOptions = async (label: string, text: string) => {
        const combobox = page.getByRole("combobox", { name: label, exact: true });
        await expect(page.locator(".ant-select").filter({ has: combobox }).locator(".anticon-loading")).toHaveCount(0);
        await combobox.click();
        await combobox.fill(text);
        return page.locator(".ant-select-dropdown:visible .ant-select-item-option");
      };
      for (const path of ["/invoices", "/events", "/billing", "/usage"]) {
        await page.goto(path);
        const customers = await filterOptions("고객", suffix);
        await expect(customers.filter({ hasText: `${archivedName} (보관됨)` })).toHaveCount(1);
        await expect(customers.filter({ hasText: activeName })).toHaveCount(1);
      }
      await page.goto("/events");
      const eventTypes = page.getByRole("combobox", { name: "이벤트 유형", exact: true });
      const list = await eventTypes.getAttribute("list");
      expect(list).toBeTruthy();
      const options = `datalist[id="${list}"] option`;
      await expect(page.locator(`${options}[value="${archivedMetricCode}"]`)).toHaveCount(1);
      await expect(page.locator(`${options}[value="${activeMetricCode}"]`)).toHaveCount(1);
    });

    await test.step("요금제, 단가와 미터 필터를 서로 독립적으로 유지", async () => {
      await page.goto(`/pricing?month=${currentMonth}&metric=${activeMetricCode}`);
      const plans = page.locator("details").filter({ has: page.locator("summary").filter({ hasText: "요금제 변경 이력" }) });
      if ((await plans.getAttribute("open")) === null) await plans.locator("summary").click();
      const rates = section(page, `${activeMetricName} 단가 변경 이력`);
      await expect(plans.getByRole("row").filter({ hasText: "5,678원" })).toHaveCount(1);
      await expect(plans.getByRole("row").filter({ hasText: "1,234원" })).toHaveCount(0);
      await expect(rates.getByRole("row").filter({ hasText: "456원" })).toHaveCount(1);
      await selectedFilter(page, "요금제 관리 상태", "보관됨");
      await expect(plans.getByRole("row").filter({ hasText: "1,234원" })).toHaveCount(1);
      await expect(plans.getByRole("row").filter({ hasText: "5,678원" })).toHaveCount(0);
      await expect(rates.getByRole("row").filter({ hasText: "456원" })).toHaveCount(1);
      await selectedFilter(page, "단가 관리 상태", "보관됨");
      await expect(rates.getByRole("row").filter({ hasText: "123원" })).toHaveCount(1);
      await expect(rates.getByRole("row").filter({ hasText: "456원" })).toHaveCount(0);
      expect(new URL(page.url()).searchParams.get("plan_status")).toBe("archived");
      await selectedFilter(page, "미터 관리 상태", "보관됨");
      await expect(page.getByRole("button", { name: new RegExp(archivedMetricName) })).toBeVisible();
      await expect(page.getByRole("button", { name: new RegExp(activeMetricName) })).toHaveCount(0);
      await selectedFilter(page, "미터 관리 상태", "전체");
      await page.getByRole("button", { name: new RegExp(activeMetricName) }).click();
      await selectedFilter(page, "요금제 관리 상태", "전체");
      await selectedFilter(page, "단가 관리 상태", "전체");
      await expect(plans.getByRole("row").filter({ hasText: /1,234원|5,678원/ })).toHaveCount(2);
      await expect(rates.getByRole("row").filter({ hasText: /123원|456원/ })).toHaveCount(2);
      await page.getByRole("button", { name: "조건 적용", exact: true }).click();
      for (const name of ["plan_status", "rate_status", "metric_status"]) expect(new URL(page.url()).searchParams.get(name)).toBe("all");
      expect(new URL(page.url()).searchParams.get("month")).toBe(currentMonth);
    });

    await test.step("사용자와 서버 키, 통보의 상태별 목록 분리", async () => {
      await page.goto("/members");
      const members = section(page, "도입사 사용자");
      await expectRecords(members, [email], [archivedMemberEmail]);
      await selectedFilter(page, "사용자 관리 상태", "보관됨");
      await expectRecords(members, [archivedMemberEmail], [email]);
      await selectedFilter(page, "사용자 관리 상태", "전체");
      await expectRecords(members, [email, archivedMemberEmail]);
      await page.goto("/integrations");
      const keys = section(page, "서버 연동 키");
      const hooks = section(page, "처리 결과 통보");
      await expectRecords(keys, [activeKeyLabel], [revokedKeyLabel]);
      await expect(hooks.getByText(activeWebhookUrl, { exact: true })).toBeVisible();
      await expect(hooks.getByText(archivedWebhookUrl, { exact: true })).toHaveCount(0);
      await selectedFilter(page, "API 키 상태", "폐기됨");
      await expectRecords(keys, [revokedKeyLabel], [activeKeyLabel]);
      await selectedFilter(page, "통보 관리 상태", "보관됨");
      await expect(hooks.getByText(archivedWebhookUrl, { exact: true })).toBeVisible();
      await expect(hooks.getByText(activeWebhookUrl, { exact: true })).toHaveCount(0);
      expect(new URL(page.url()).searchParams.get("key_status")).toBe("revoked");
      await selectedFilter(page, "API 키 상태", "만료됨");
      await expectRecords(keys, [], [activeKeyLabel, revokedKeyLabel]);
      await selectedFilter(page, "API 키 상태", "전체");
      await selectedFilter(page, "통보 관리 상태", "전체");
      await expectRecords(keys, [activeKeyLabel, revokedKeyLabel]);
      await expect(hooks.getByText(activeWebhookUrl, { exact: true })).toBeVisible();
      await expect(hooks.getByText(archivedWebhookUrl, { exact: true })).toBeVisible();
      await page.reload();
      await expectRecords(keys, [activeKeyLabel, revokedKeyLabel]);
    });

    await test.step("보관한 PG 키와 카드의 이력을 구분 조회", async () => {
      await page.goto("/settings");
      const credentials = section(page, "토스페이먼츠 연동");
      await expectRecords(credentials, ["new2"], ["old1"]);
      await selectedFilter(page, "PG 키 관리 상태", "보관됨");
      await expectRecords(credentials, ["old1"], ["new2"]);
      await selectedFilter(page, "PG 키 관리 상태", "전체");
      await expectRecords(credentials, ["new2", "old1"]);
      await page.goto(`/customers/${activeCustomerId}?month=${currentMonth}`);
      const cards = section(page, "현재 결제 환경의 카드 관리");
      const rows = cards.locator(".workspace-desktop-table").getByRole("row").filter({ hasText: "43301234****123*" });
      await expect(rows).toHaveCount(1);
      await expect(cards.getByRole("button", { name: "카드 보관", exact: true })).toBeVisible();
      await selectedFilter(page, "카드 관리 상태", "보관됨");
      await expect(rows).toHaveCount(1);
      await expect(cards.getByRole("button", { name: "카드 보관", exact: true })).toHaveCount(0);
      await selectedFilter(page, "카드 관리 상태", "전체");
      await expect(rows).toHaveCount(2);
      await page.reload();
      await expect(rows).toHaveCount(2);
      expect(new URL(page.url()).searchParams.get("month")).toBe(currentMonth);
      await page.getByRole("button", { name: "조회", exact: true }).click();
      expect(new URL(page.url()).searchParams.get("card_status")).toBe("all");
      await expect(rows).toHaveCount(2);
    });

    await test.step("390px에서 필터와 보관 목록이 화면 안에 표시", async () => {
      await page.setViewportSize({ width: 390, height: 844 });
      const routes = [
        { path: "/customers?status=all", filters: ["관리 상태"] },
        { path: "/billable-metrics?status=all", filters: ["관리 상태"] },
        { path: `/pricing?month=${currentMonth}&metric=${activeMetricCode}&metric_status=all&plan_status=all&rate_status=all`, filters: ["미터 관리 상태", "요금제 관리 상태", "단가 관리 상태"] },
        { path: "/members?status=all", filters: ["사용자 관리 상태"] },
        { path: "/integrations?status=all&key_status=all", filters: ["API 키 상태", "통보 관리 상태"] },
        { path: "/settings?status=all", filters: ["PG 키 관리 상태"] },
        { path: `/customers/${activeCustomerId}?month=${currentMonth}&card_status=all`, filters: ["카드 관리 상태"] },
      ];
      for (const route of routes) {
        await page.goto(route.path);
        await expect(page.getByRole("main")).toBeVisible();
        await expect(page.getByText("데이터를 불러오지 못했습니다", { exact: true })).toHaveCount(0);
        for (const label of route.filters) {
          const select = page.getByRole("combobox", { name: label, exact: true });
          if (["/customers", "/billable-metrics", "/pricing"].includes(new URL(route.path, "http://localhost").pathname)) await expect(select).toHaveValue("all");
          else {
            const filter = page.getByRole("navigation", { name: label, exact: true });
            await expect(filter.getByRole("link", { name: "전체", exact: true })).toHaveAttribute("aria-current", "page");
          }
        }
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        if (route.path.startsWith("/pricing?") && process.env.ARCHIVE_FILTER_PRICING_SCREENSHOT_PATH) await page.screenshot({ path: process.env.ARCHIVE_FILTER_PRICING_SCREENSHOT_PATH, fullPage: true });
      }
      if (process.env.ARCHIVE_FILTER_SCREENSHOT_PATH) await section(page, "현재 결제 환경의 카드 관리").screenshot({ path: process.env.ARCHIVE_FILTER_SCREENSHOT_PATH });
    });
  } catch (error) {
    failure = safeFailure(error, secrets);
  } finally {
    await context.close();
  }
  if (failure) throw failure;
});
