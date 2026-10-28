import { expect, test, type APIRequestContext, type Page } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";
import { CONSENT_DOCUMENTS, CONSENT_VERSION } from "../src/lib/auth/consents";

const API_URL = process.env.S1_API_URL ?? "http://127.0.0.1:38147";
const FRONTEND_URL = process.env.QUERY_FRONTEND_URL ?? "http://localhost:33147";
const PASSWORD = "local-query-e2e-password";
const OPERATOR_EMAIL = process.env.E2E_OPERATOR_EMAIL ?? "operator@fe-e2e.local";
const OPERATOR_PASSWORD = process.env.E2E_OPERATOR_PASSWORD ?? "local-operator-e2e-password";

test.use({ trace: "off", screenshot: "off", video: "off" });

async function createTenant(request: APIRequestContext, operatorToken: string, suffix: string) {
  const email = `query-${suffix}@fe-e2e.local`;
  const signup = await request.post(`${API_URL}/v1/signups`, { data: { organization_name: `조회 검증 ${suffix}`, name: "조회 대표관리자", email, password: PASSWORD, consents: CONSENT_DOCUMENTS.map(({ document }) => ({ document, document_version: CONSENT_VERSION })) } });
  expect(signup.status(), "격리 검증 도입사 가입").toBe(201);
  const organizationId = (await signup.json()).organization.id as string;
  const approval = await request.put(`${API_URL}/v1/organizations/${organizationId}/status`, { headers: { Authorization: `Bearer ${operatorToken}` }, data: { status: "active" } });
  expect(approval.status(), "격리 검증 도입사 승인").toBe(200);
  const session = await request.post(`${API_URL}/v1/sessions`, { data: { email, password: PASSWORD } });
  expect(session.status(), "격리 검증 도입사 로그인").toBe(201);
  const token = (await session.json()).token as string;
  return { organizationId, token, email, headers: { Authorization: `Bearer ${token}` } };
}

async function expectScope(page: Page, expected: Record<string, string>) {
  await expect.poll(() => {
    const current = new URL(page.url()).searchParams;
    return Object.fromEntries(Object.keys(expected).map(key => [key, current.get(key)]));
  }).toEqual(expected);
}

async function chooseCustomer(page: Page, label: string, name: string) {
  const picker = page.getByRole("combobox", { name: label, exact: true });
  await picker.scrollIntoViewIfNeeded();
  await picker.click();
  await expect(picker).toHaveAttribute("aria-expanded", "true");
  await expect(page.locator(".ant-select").filter({ has: page.getByRole("combobox", { name: label, exact: true }) })).not.toHaveClass(/ant-select-loading/);
  await page.locator(".ant-select-dropdown:visible .ant-select-item-option").filter({ hasText: name }).click();
}

async function expandAdvanced(page: Page) {
  const details = page.getByRole("form", { name: "조회 조건", exact: true }).locator("details");
  if (await details.getAttribute("open") === null) await details.locator("summary").click();
  return details;
}

test("가격 조회 조건, 전체 CSV, 이전 조직의 단가 폼과 CSV 경계를 보존한다", async ({ browser, request }) => {
  test.setTimeout(180_000);
  const suffix = randomUUID();
  const currentMonth = new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit" }).format(new Date());
  const activeCode = `aa-query-${suffix}`;
  const oldCodes = Array.from({ length: 21 }, (_, index) => `query-${suffix}-${String(index).padStart(2, "0")}`);
  const secrets = new Set([PASSWORD, OPERATOR_PASSWORD]);
  const context = await browser.newContext({ baseURL: FRONTEND_URL, viewport: { width: 1280, height: 900 }, acceptDownloads: true });
  const page = await context.newPage();
  let failure: unknown;
  try {
    const operator = await request.post(`${API_URL}/v1/sessions`, { data: { email: OPERATOR_EMAIL, password: OPERATOR_PASSWORD } });
    expect(operator.status(), "검증 운영자 로그인").toBe(201);
    const operatorToken = (await operator.json()).token as string;
    secrets.add(operatorToken);
    const tenantA = await createTenant(request, operatorToken, `a-${suffix}`);
    const tenantB = await createTenant(request, operatorToken, `b-${suffix}`);
    secrets.add(tenantA.token); secrets.add(tenantB.token);
    for (const tenant of [tenantA, tenantB]) {
      const created = await request.post(`${API_URL}/v1/billable-metrics`, { headers: tenant.headers, data: { code: activeCode, name: `현재 미터 ${suffix}`, event_type: activeCode, aggregation: "sum", target_property: "units", dimension_properties: [] } });
      expect(created.status(), "조직별 동일 코드 미터 준비").toBe(201);
    }
    const customerName = `전용 가격 고객 ${suffix}`;
    const customer = await request.post(`${API_URL}/v1/customers`, { headers: tenantA.headers, data: { name: customerName } });
    expect(customer.status(), "가격 조회 고객 준비").toBe(201);
    const customerId = (await customer.json()).id as string;
    for (const scope of [{ unit_price: 10 }, { customer_id: customerId, unit_price: 20 }]) {
      const rate = await request.post(`${API_URL}/v1/billable-metrics/${activeCode}/price-rates`, { headers: tenantA.headers, data: { dimension_values: {}, effective_from: currentMonth, ...scope } });
      expect(rate.status(), "공통과 고객 전용 가격 준비").toBe(201);
    }
    for (const code of oldCodes) {
      const created = await request.post(`${API_URL}/v1/billable-metrics`, { headers: tenantA.headers, data: { code, name: `보관 미터 ${code}`, event_type: code, aggregation: "sum", target_property: "units", dimension_properties: [] } });
      expect(created.status(), "보관 미터 준비").toBe(201);
      expect((await request.delete(`${API_URL}/v1/billable-metrics/${code}`, { headers: tenantA.headers })).status(), "미터 보관").toBe(204);
    }
    await context.addCookies([{ name: "me_session", value: tenantA.token, url: FRONTEND_URL, httpOnly: true, sameSite: "Lax" }]);
    const preserved = { environment: "legacy", organization_id: tenantA.organizationId, month: currentMonth, metric_status: "archived", plan_status: "all", rate_status: "archived", metric: oldCodes[0], q: suffix, sort: "code", order: "asc" };
    await page.goto(`/pricing?${new URLSearchParams(preserved)}`);
    const rail = page.getByRole("region", { name: "과금 미터 선택", exact: true });
    await expect(rail.getByRole("button", { name: /보관 미터/ })).toHaveCount(20);
    await rail.getByRole("link", { name: "다음", exact: true }).click();
    await expectScope(page, { ...preserved, page: "1" });
    await expect(rail.getByRole("button", { name: /보관 미터/ })).toHaveCount(1);
    const beforeFilterChange = page.url();
    await expandAdvanced(page);
    await page.getByRole("combobox", { name: "페이지 크기", exact: true }).selectOption("50");
    expect(page.url(), "페이지 크기는 다른 조건과 함께 적용").toBe(beforeFilterChange);
    await page.getByRole("button", { name: "조건 적용", exact: true }).click();
    await expectScope(page, { ...preserved, size: "50" });
    await expect(rail.getByRole("button", { name: /보관 미터/ })).toHaveCount(21);
    const appliedAdvanced = page.getByRole("form", { name: "조회 조건", exact: true }).locator("details");
    await expect(appliedAdvanced).toHaveJSProperty("open", true);
    await expect(appliedAdvanced.locator("summary")).toContainText("적용 1개");
    await appliedAdvanced.locator("summary").click();
    await page.getByRole("button", { name: "조건 적용", exact: true }).click();
    await expectScope(page, { ...preserved, size: "50" });
    await page.getByRole("button", { name: "조건 적용", exact: true }).click();
    await expectScope(page, { ...preserved, size: "50" });
    const downloaded = page.waitForEvent("download");
    await page.getByRole("button", { name: "조건 전체 CSV 내보내기", exact: true }).click();
    const download = await downloaded;
    const downloadPath = await download.path();
    if (!downloadPath) throw new Error("검증 CSV 파일이 없습니다.");
    const csv = await readFile(downloadPath, "utf8");
    for (const code of oldCodes) expect(csv.includes(code), "보관 전체 CSV 포함").toBe(true);
    expect(csv.includes(activeCode), "사용 중 미터 CSV 제외").toBe(false);

    await test.step("작성 중인 검색, 고객, 미터, 금액과 기간을 한 번에 적용", async () => {
      for (const route of ["usage", "billing", "invoices"]) {
        await page.goto(`/${route}?${new URLSearchParams({ environment: "legacy", organization_id: tenantA.organizationId, month: currentMonth })}`);
        const originalUrl = page.url();
        const advanced = await expandAdvanced(page);
        const form = page.getByRole("form", { name: "조회 조건", exact: true });
        await form.getByRole("searchbox", { name: "검색", exact: true }).fill(suffix);
        if (route !== "usage") {
          await form.getByLabel("최소 금액 (원)", { exact: true }).fill("0");
          await form.getByLabel("최대 금액 (원)", { exact: true }).fill("10000");
        }
        if (route === "invoices") {
          await form.getByLabel("확정 시각 시작 (KST)", { exact: true }).fill("2026-01-01T00:00");
          await form.getByLabel("확정 시각 종료 전 (KST)", { exact: true }).fill("2027-01-01T00:00");
        }
        if (route === "billing") {
          await form.getByLabel("최소 금액 (원)", { exact: true }).fill("-1");
          await advanced.locator("summary").click();
          await form.getByRole("button", { name: "조건 적용", exact: true }).click();
          await expect(advanced).toHaveJSProperty("open", true);
          await expect(form.getByLabel("최소 금액 (원)", { exact: true })).toBeFocused();
          expect(page.url()).toBe(originalUrl);
          await form.getByLabel("최소 금액 (원)", { exact: true }).fill("0");
        }
        await form.getByRole("combobox", { name: "페이지 크기", exact: true }).selectOption("50");
        await advanced.locator("summary").click();
        await form.getByRole("combobox", { name: "과금 미터", exact: true }).scrollIntoViewIfNeeded();
        await form.getByRole("combobox", { name: "과금 미터", exact: true }).fill(activeCode);
        const metricOptions = page.locator(".ant-select-dropdown:visible .ant-select-item-option");
        await expect(metricOptions).toHaveCount(1);
        const metricListId = await form.getByRole("combobox", { name: "과금 미터", exact: true }).getAttribute("aria-controls");
        expect(metricListId).not.toBeNull();
        const metricPopup = page.locator(".ant-select-dropdown").filter({ has: page.locator(`[id="${metricListId}"]`) });
        await metricOptions.filter({ hasText: `현재 미터 ${suffix}` }).click();
        await expect(metricPopup).toBeHidden();
        await chooseCustomer(page, "고객", customerName);
        expect(page.url(), "필터를 조합하는 동안 이동하지 않음").toBe(originalUrl);
        await expect(form.getByRole("searchbox", { name: "검색", exact: true })).toHaveValue(suffix);
        if (route !== "usage") await expect(form.getByLabel("최대 금액 (원)", { exact: true })).toHaveValue("10000");
        await form.getByRole("button", { name: "조건 적용", exact: true }).click();
        await expectScope(page, { environment: "legacy", organization_id: tenantA.organizationId, customer_id: customerId, billable_metric_code: activeCode, q: suffix, size: "50", ...(route === "usage" ? {} : { min_amount: "0", max_amount: "10000" }), ...(route === "invoices" ? { from: "2026-01-01T00:00", to: "2027-01-01T00:00" } : { month: currentMonth }) });
      }
    });
    await test.step("서버의 50건 페이지를 PC와 모바일에서 다시 자르지 않음", async () => {
      const needle = `페이지 고객 ${suffix}`;
      for (let index = 0; index < 25; index++) {
        const created = await request.post(`${API_URL}/v1/customers`, { headers: tenantA.headers, data: { name: `${needle} ${index}` } });
        expect(created.status()).toBe(201);
      }
      await page.goto(`/customers?${new URLSearchParams({ environment: "legacy", organization_id: tenantA.organizationId })}`);
      const form = page.getByRole("form", { name: "조회 조건", exact: true });
      const advanced = await expandAdvanced(page);
      await form.getByRole("searchbox", { name: "이름 또는 외부 ID로 찾기", exact: true }).fill(needle);
      await form.getByRole("combobox", { name: "관리 상태", exact: true }).selectOption("all");
      await form.getByRole("combobox", { name: "페이지 크기", exact: true }).selectOption("50");
      await advanced.locator("summary").click();
      await expect(form.getByRole("searchbox", { name: "이름 또는 외부 ID로 찾기", exact: true })).toHaveValue(needle);
      await form.getByRole("button", { name: "조건 적용", exact: true }).click();
      await expectScope(page, { q: needle, size: "50", status: "all" });
      await expect(page.locator(".workspace-desktop-table").getByRole("row").filter({ hasText: needle })).toHaveCount(25);
      await expect(page.locator(".workspace-desktop-table .ant-pagination")).toHaveCount(0);
      await page.setViewportSize({ width: 390, height: 844 });
      await expect(page.locator(".workspace-mobile-records .workspace-mobile-record")).toHaveCount(25);
      await page.setViewportSize({ width: 1280, height: 900 });
    });
    await page.goto(`/pricing?${new URLSearchParams({ environment: "legacy", organization_id: tenantA.organizationId, month: currentMonth, metric: activeCode })}`);
    await page.getByRole("searchbox", { name: "검색", exact: true }).fill(suffix);
    await chooseCustomer(page, "적용 대상", customerName);
    expect(new URL(page.url()).searchParams.has("customer_id"), "고객 선택은 제출 전 이동하지 않음").toBe(false);
    await expect(page.getByRole("searchbox", { name: "검색", exact: true })).toHaveValue(suffix);
    await page.getByRole("button", { name: "조건 적용", exact: true }).click();
    await expectScope(page, { organization_id: tenantA.organizationId, customer_id: customerId, metric: activeCode, month: currentMonth });
    await expect(page.locator('section[aria-labelledby="current-prices-title"]')).toContainText("20원");
    await page.goto(`/pricing?${new URLSearchParams({ environment: "legacy", organization_id: tenantA.organizationId, month: currentMonth, metric: activeCode })}`);
    await page.getByRole("button", { name: "단가 버전 등록", exact: true }).click();
    const dialog = page.getByRole("dialog");
    await dialog.getByLabel("계산 방식", { exact: true }).selectOption("graduated");
    await dialog.getByLabel("구간 1 단가", { exact: true }).fill("10");
    await dialog.getByLabel("구간 2 단가", { exact: true }).fill("10");
    const upper = dialog.getByLabel("구간 1 끝 수량", { exact: true });
    await upper.fill("0");
    await dialog.getByRole("button", { name: "이 단가 버전 저장", exact: true }).click();
    await expect(upper).toBeFocused();
    await expect(upper).toHaveAttribute("aria-invalid", "true");
    const describedBy = await upper.getAttribute("aria-describedby");
    const fieldError = dialog.getByText("끝 수량은 0보다 크고 앞 구간보다 커야 합니다.", { exact: true });
    await expect(fieldError).toHaveAttribute("id", describedBy!);
    await upper.fill("100");
    await expect(upper).toHaveAttribute("aria-invalid", "false");
    await dialog.getByLabel("계산 방식", { exact: true }).selectOption("per_unit");
    await dialog.getByLabel("사용량 1당 단가 (원)", { exact: true }).fill("999");
    await context.addCookies([{ name: "me_session", value: tenantB.token, url: FRONTEND_URL, httpOnly: true, sameSite: "Lax" }]);
    await dialog.getByRole("button", { name: "이 단가 버전 저장", exact: true }).click();
    await expect(dialog.getByRole("alert")).toContainText("도입사가 바뀌었습니다");
    for (const tenant of [tenantA, tenantB]) {
      const rates = await request.get(`${API_URL}/v1/billable-metrics/${activeCode}/price-rates?status=all`, { headers: tenant.headers });
      expect(rates.status(), "조직별 단가 경계 조회").toBe(200);
      expect((await rates.json()).price_rates.length, "오래된 폼은 어느 조직에도 쓰지 않음").toBe(tenant === tenantA ? 1 : 0);
    }
    page.once("dialog", event => event.accept());
    await dialog.locator(".workspace-dialog-actions").getByRole("button", { name: "닫기", exact: true }).click();
    const billingHref = await page.getByRole("link", { name: "청구 예정액 확인", exact: true }).getAttribute("href");
    expect(billingHref).not.toBeNull();
    expect(new URL(billingHref!, FRONTEND_URL).searchParams.get("organization_id"), "공유 세션이 바뀌어도 다음 업무 링크는 렌더된 도입사 유지").toBe(tenantA.organizationId);
    const exportResponse = page.waitForResponse(response => new URL(response.url()).pathname === "/exports/billable-metric-prices");
    await page.getByRole("button", { name: "조건 전체 CSV 내보내기", exact: true }).click();
    const blocked = await exportResponse;
    expect(new URL(blocked.url()).searchParams.get("organization_id"), "CSV는 렌더된 조직으로 고정").toBe(tenantA.organizationId);
    expect(blocked.status(), "바뀐 세션으로 다른 조직 CSV를 받지 않음").toBe(403);
    expect((await blocked.json()).code, "조직 불일치가 API 권한 경계에서 차단됨").toBe("permission_denied");
  } catch (error) {
    let message = error instanceof Error ? error.stack ?? error.message : "조회 확장 실증을 마치지 못했습니다.";
    for (const secret of secrets) if (secret) message = message.split(secret).join("[redacted]");
    failure = new Error(message.replace(/me_(?:test|live|reg|invite)_[A-Za-z0-9]+/g, "[redacted token]"));
  } finally {
    await context.close();
  }
  if (failure) throw failure;
});
