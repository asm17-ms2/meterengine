import { expect, test, type BrowserContext, type Page } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { CONSENT_DOCUMENTS, CONSENT_VERSION } from "../src/lib/auth/consents";

const API_URL = process.env.S1_API_URL ?? "http://127.0.0.1:38147";
const PASSWORD = "local-events-e2e-password";
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

async function expectQuery(page: Page, expected: Record<string, string | null>) {
  for (const [key, value] of Object.entries(expected)) {
    await expect.poll(() => new URL(page.url()).searchParams.get(key)).toBe(value);
  }
}

function eventLinks(page: Page) {
  return page.locator(".data-desktop-table tbody").getByRole("link");
}

test("자유 기간과 초 단위 경계, 실제 유형과 검색, 페이지와 상세 복귀를 실제 화면에서 확인한다", async ({ browser, request }) => {
  test.setTimeout(120_000);
  const suffix = randomUUID();
  const email = `events-${suffix}@fe-e2e.local`;
  const queryText = `slice-${suffix}`;
  const eventType = `unmetered-${suffix}`;
  const properties = JSON.stringify({ segment: "target", enabled: true });
  const currentMonth = new Date(Date.now() + 9 * 3600_000).toISOString().slice(0, 7);
  const monthStart = Date.parse(`${currentMonth}-01T00:00:00Z`);
  const previousDate = new Date(monthStart - 86_400_000).toISOString().slice(0, 10);
  const previousMonth = previousDate.slice(0, 7);
  const firstDate = `${currentMonth}-01`;
  const followingDate = new Date(monthStart + 86_400_000).toISOString().slice(0, 10);
  const beforeDate = new Date(monthStart - 2 * 86_400_000).toISOString().slice(0, 10);
  const ids = Array.from({ length: 55 }, (_, index) => `${queryText}/거래/${String(index).padStart(2, "0")}${index === 54 ? "/한글/상세" : ""}`);
  const inclusiveEndId = `${queryText}/거래/55/종료일끝`;
  const beforeId = `${queryText}/거래/98/시작전`;
  const secondsFrom = `${previousDate}T23:59:10`;
  const secondsTo = `${previousDate}T23:59:13`;
  const secrets = new Set([PASSWORD, OPERATOR_PASSWORD]);
  let context: BrowserContext | undefined;
  let failure: Error | undefined;

  try {
    let customerId = "";
    let organizationId = "";
    await test.step("새 도입사와 월 경계 원본 이벤트 준비", async () => {
      const signup = await request.post(`${API_URL}/v1/signups`, { data: {
        organization_name: `자유 기간 E2E ${suffix}`, name: "기간 검증 대표관리자", email, password: PASSWORD,
        consents: CONSENT_DOCUMENTS.map(({ document }) => ({ document, document_version: CONSENT_VERSION })),
      } });
      expect(signup.status()).toBe(201);
      const { organization } = await signup.json();
      organizationId = organization.id;
      const operator = await request.post(`${API_URL}/v1/sessions`, { data: { email: OPERATOR_EMAIL, password: OPERATOR_PASSWORD } });
      expect(operator.status()).toBe(201);
      const operatorToken = (await operator.json()).token;
      if (typeof operatorToken !== "string" || !operatorToken) throw new Error("운영자 검증 세션을 받지 못했습니다.");
      secrets.add(operatorToken);
      expect((await request.put(`${API_URL}/v1/organizations/${organization.id}/status`, { headers: { Authorization: `Bearer ${operatorToken}` }, data: { status: "active" } })).status()).toBe(200);
      const login = await request.post(`${API_URL}/v1/sessions`, { data: { email, password: PASSWORD } });
      expect(login.status()).toBe(201);
      const token = (await login.json()).token;
      if (typeof token !== "string" || !token) throw new Error("대표관리자 검증 세션을 받지 못했습니다.");
      secrets.add(token);
      const headers = { Authorization: `Bearer ${token}` };
      for (let index = 0; index < 25; index++) {
        const filler = await request.post(`${API_URL}/v1/customers`, { headers, data: { name: `가 원격 조회 고객 ${String(index).padStart(2, "0")}`, external_id: `events-filler-${suffix}-${index}` } });
        expect(filler.status()).toBe(201);
      }
      const customer = await request.post(`${API_URL}/v1/customers`, { headers, data: { name: "자유 기간 검증 고객", external_id: `events-customer-${suffix}` } });
      expect(customer.status()).toBe(201);
      customerId = (await customer.json()).id;
      const other = await request.post(`${API_URL}/v1/customers`, { headers, data: { name: "자유 기간 검증 고객", external_id: `other-customer-${suffix}` } });
      expect(other.status()).toBe(201);
      const otherCustomerId = (await other.json()).id;
      const event = (transactionId: string, timestamp: string, overrides: Record<string, unknown> = {}) => ({ transaction_id: transactionId, customer_id: customerId, type: eventType, properties: { segment: "target", enabled: true }, timestamp, ...overrides });
      const baseTime = Date.parse(`${previousDate}T23:58:50+09:00`);
      const events = [
        ...ids.map((id, index) => event(id, new Date(baseTime + index * 1000).toISOString())),
        event(inclusiveEndId, `${firstDate}T23:59:59+09:00`),
        event(beforeId, `${beforeDate}T23:59:59+09:00`),
        event(`${queryText}/거래/99/종료다음날`, `${followingDate}T00:00:00+09:00`),
        event(`${queryText}/거래/70/다른속성`, new Date(baseTime).toISOString(), { properties: { segment: "other", enabled: true } }),
        event(`${queryText}/거래/71/다른유형`, new Date(baseTime).toISOString(), { type: `other-type-${suffix}` }),
        event(`${queryText}/거래/72/다른고객`, new Date(baseTime).toISOString(), { customer_id: otherCustomerId }),
        event(`outside-query-${suffix}`, new Date(baseTime).toISOString()),
      ];
      const batch = await request.post(`${API_URL}/v1/event-batches`, { headers, data: { events } });
      expect(batch.status()).toBe(200);
      expect((await batch.json()).results.map((result: { result: string }) => result.result)).toEqual(events.map(() => "stored"));
    });

    context = await browser.newContext({ baseURL: "http://localhost:33147", viewport: { width: 1280, height: 900 } });
    const page = await context.newPage();
    const filters = page.getByRole("main").locator("form.event-filters");
    const screenshotFilters = async (path: string) => {
      await page.evaluate(() => window.scrollTo(0, 0));
      const bounds = await filters.boundingBox();
      if (!bounds) throw new Error("기간 필터의 캡처 영역이 없습니다.");
      await page.screenshot({ path, fullPage: true, clip: { x: 0, y: 0, width: 390, height: Math.ceil(bounds.y + bounds.height + 24) } });
    };
    const advanced = filters.locator("details");
    const periodSummary = filters.locator('[aria-label="현재 조회 기간"]');
    const preserved = { customer_id: customerId, type: eventType, q: queryText, properties, sort: "transaction_id", order: "asc", size: "50" };
    const dateQuery = { ...preserved, from: previousDate, to: firstDate, range_mode: "date", month: null };

    await test.step("미터 없이 수집된 유형으로 날짜 범위 조회", async () => {
      await page.goto(`/login?returnTo=${encodeURIComponent(`/events?month=${previousMonth}`)}`);
      await page.getByLabel("이메일", { exact: true }).fill(email);
      await page.getByLabel("비밀번호", { exact: true }).fill(PASSWORD);
      await page.getByRole("button", { name: "로그인", exact: true }).click();
      await expect(page.getByRole("heading", { name: "수집 로그", exact: true })).toBeVisible();
      await expect(filters.locator(`datalist option[value="${eventType}"]`)).toHaveCount(1);
      await expect(filters.getByRole("radio", { name: "월별", exact: true })).toBeChecked();
      await expect(filters.getByLabel("조회 월", { exact: true })).toHaveValue(previousMonth);
      await expect(filters.getByLabel(/^정렬 기준/)).toBeHidden();
      await expect(filters.locator('input[name="from"]')).toHaveCount(0);
      await expect(periodSummary).toContainText("KST");
      const monthlySummary = await periodSummary.innerText();
      if (process.env.EVENT_PERIOD_DEFAULT_SCREENSHOT_PATH) await page.screenshot({ path: process.env.EVENT_PERIOD_DEFAULT_SCREENSHOT_PATH });
      await advanced.locator("summary").press("Enter");
      await expect(filters.getByLabel(/^정렬 기준/)).toBeVisible();
      await advanced.locator("summary").press("Enter");
      await expect(filters.getByLabel(/^정렬 기준/)).toBeHidden();
      await filters.getByRole("radio", { name: "직접 지정", exact: true }).press("Space");
      await expect(filters.getByRole("radio", { name: "직접 지정", exact: true })).toBeChecked();
      await expect(filters.getByLabel(/^지정 단위/)).toHaveValue("date");
      await expect(filters.getByLabel("시작 날짜 (포함)", { exact: true })).toHaveValue(`${previousMonth}-01`);
      await expect(filters.getByLabel("종료 날짜 (해당 일 포함)", { exact: true })).toHaveValue(previousDate);
      const beforeInvalidRange = page.url();
      await filters.getByLabel("시작 날짜 (포함)", { exact: true }).fill(followingDate);
      await filters.getByLabel("종료 날짜 (해당 일 포함)", { exact: true }).fill(previousDate);
      await page.getByRole("button", { name: "이 조건으로 조회", exact: true }).click();
      const rangeError = filters.getByRole("alert");
      await expect(rangeError).toContainText("종료는 시작보다 뒤여야 합니다.");
      await expect(filters.getByLabel("시작 날짜 (포함)", { exact: true })).toBeFocused();
      for (const name of ["시작 날짜 (포함)", "종료 날짜 (해당 일 포함)"]) {
        const field = filters.getByLabel(name, { exact: true });
        await expect(field).toHaveAttribute("aria-invalid", "true");
        expect((await field.getAttribute("aria-describedby"))?.split(" ")).toContain(await rangeError.getAttribute("id"));
      }
      expect(page.url()).toBe(beforeInvalidRange);
      await filters.getByLabel("시작 날짜 (포함)", { exact: true }).fill(previousDate);
      await filters.getByLabel("종료 날짜 (해당 일 포함)", { exact: true }).fill(firstDate);
      await filters.getByLabel(/^지정 단위/).selectOption("datetime");
      await expect(filters.getByLabel("시작 시각 (포함)", { exact: true })).toHaveValue(new RegExp(`^${previousDate}T00:00(:00)?$`));
      await expect(filters.getByLabel("종료 시각 (미포함)", { exact: true })).toHaveValue(new RegExp(`^${followingDate}T00:00(:00)?$`));
      await filters.getByLabel(/^지정 단위/).selectOption("date");
      await expect(filters.getByLabel("시작 날짜 (포함)", { exact: true })).toHaveValue(previousDate);
      await expect(filters.getByLabel("종료 날짜 (해당 일 포함)", { exact: true })).toHaveValue(firstDate);
      await expect(periodSummary).toHaveText(monthlySummary);
      const customerFilter = filters.getByRole("combobox", { name: "고객", exact: true });
      await customerFilter.fill("자유 기간 검증 고객");
      const customerOptions = page.locator(".ant-select-dropdown .ant-select-item-option");
      const chosenCustomer = customerOptions.filter({ hasText: customerId });
      await expect(chosenCustomer).toBeVisible();
      await expect(customerOptions.filter({ hasText: "자유 기간 검증 고객" })).toHaveCount(2);
      await chosenCustomer.click();
      await expect(filters.locator('input[name="customer_id"]')).toHaveValue(customerId);
      await filters.getByLabel("이벤트 유형", { exact: true }).fill(eventType);
      await filters.getByLabel("이벤트 또는 고객 찾기", { exact: true }).fill(queryText);
      await advanced.locator("summary").press("Enter");
      await filters.getByLabel(/^정렬 기준/).selectOption("transaction_id");
      await filters.getByLabel(/^정렬 순서/).selectOption("asc");
      await filters.getByLabel(/^페이지당 표시/).selectOption("50");
      const propertiesInput = filters.getByLabel("속성과 값 (JSON 객체)", { exact: true });
      await propertiesInput.fill("{invalid");
      await advanced.locator("summary").press("Enter");
      await expect(propertiesInput).toBeHidden();
      await page.getByRole("button", { name: "이 조건으로 조회", exact: true }).click();
      await expect(propertiesInput).toBeVisible();
      await expect(propertiesInput).toBeFocused();
      await expect(propertiesInput).toHaveAttribute("aria-invalid", "true");
      await expect(filters.getByRole("alert")).toContainText("JSON 객체로 입력하세요.");
      expect(page.url()).toBe(beforeInvalidRange);
      await propertiesInput.fill(properties);
      await page.getByRole("button", { name: "이 조건으로 조회", exact: true }).click();
      await expectQuery(page, { ...dateQuery, page: null });
      await expect(eventLinks(page)).toHaveText(ids.slice(0, 50));
      await expect(page.getByText("총 56건, 이 페이지 50줄", { exact: false })).toBeVisible();
      await expect(advanced.locator("summary")).toContainText(/적용\s*[1-9]/);
      await expect(filters.getByLabel("속성과 값 (JSON 객체)", { exact: true })).toBeVisible();
      await expect(periodSummary).toContainText("KST");
      await expect(periodSummary).not.toHaveText(monthlySummary);
      if (process.env.EVENT_PERIOD_CUSTOM_SCREENSHOT_PATH) await page.screenshot({ path: process.env.EVENT_PERIOD_CUSTOM_SCREENSHOT_PATH });
    });

    await test.step("같은 기간에서 검색만 적용해도 편집 상태를 해제", async () => {
      const narrowedQuery = `${queryText}/거래/54`;
      await filters.getByLabel("이벤트 또는 고객 찾기", { exact: true }).fill(narrowedQuery);
      await page.getByRole("button", { name: "이 조건으로 조회", exact: true }).click();
      await expectQuery(page, { ...dateQuery, q: narrowedQuery, page: null });
      await expect(eventLinks(page)).toHaveText([ids[54]]);
      await expect(filters.getByText("조건 변경됨. 조회 버튼을 눌러 적용하세요.", { exact: true })).toHaveCount(0);
      await expect(filters.getByLabel("이벤트 또는 고객 찾기", { exact: true })).toHaveValue(narrowedQuery);
      await filters.getByLabel("이벤트 또는 고객 찾기", { exact: true }).fill(queryText);
      await page.getByRole("button", { name: "이 조건으로 조회", exact: true }).click();
      await expectQuery(page, { ...dateQuery, page: null });
      await expect(eventLinks(page)).toHaveText(ids.slice(0, 50));
      await expect(filters.getByText("조건 변경됨. 조회 버튼을 눌러 적용하세요.", { exact: true })).toHaveCount(0);
      const unchangedUrl = page.url();
      await filters.getByLabel("이벤트 또는 고객 찾기", { exact: true }).fill(`${queryText}-edited`);
      await filters.getByLabel("이벤트 또는 고객 찾기", { exact: true }).fill(queryText);
      await expect(filters.getByText("조건 변경됨. 조회 버튼을 눌러 적용하세요.", { exact: true })).toBeVisible();
      await page.getByRole("button", { name: "이 조건으로 조회", exact: true }).click();
      await expect(filters).toHaveAttribute("aria-busy", "false");
      await expect(filters.getByText("조건 변경됨. 조회 버튼을 눌러 적용하세요.", { exact: true })).toHaveCount(0);
      expect(page.url()).toBe(unchangedUrl);
    });

    await test.step("초 단위 시작 포함과 종료 제외", async () => {
      await filters.getByLabel(/^지정 단위/).selectOption("datetime");
      await filters.getByLabel("시작 시각 (포함)", { exact: true }).fill(secondsFrom);
      await filters.getByLabel("종료 시각 (미포함)", { exact: true }).fill(secondsTo);
      await page.getByRole("button", { name: "이 조건으로 조회", exact: true }).click();
      await expectQuery(page, { ...preserved, from: secondsFrom, to: secondsTo, range_mode: null, month: null, page: null });
      await expect(eventLinks(page)).toHaveText(ids.slice(20, 23));
      await expect(filters.getByLabel("시작 시각 (포함)", { exact: true })).toHaveValue(secondsFrom);
      await expect(filters.getByLabel("종료 시각 (미포함)", { exact: true })).toHaveValue(secondsTo);
      await filters.getByLabel(/^지정 단위/).selectOption("date");
      await filters.getByLabel(/^지정 단위/).selectOption("datetime");
      await expect(filters.getByLabel("시작 시각 (포함)", { exact: true })).toHaveValue(secondsFrom);
      await expect(filters.getByLabel("종료 시각 (미포함)", { exact: true })).toHaveValue(secondsTo);
    });

    await test.step("월별 전환과 다시 자유 기간으로 복귀", async () => {
      await filters.getByRole("radio", { name: "월별", exact: true }).check();
      await filters.getByLabel("조회 월", { exact: true }).fill(previousMonth);
      await page.getByRole("button", { name: "이 조건으로 조회", exact: true }).click();
      await expectQuery(page, { ...preserved, month: previousMonth, from: null, to: null, range_mode: null, page: null });
      await expect(eventLinks(page)).toHaveText(ids.slice(0, 50));
      await expect(page.getByText("총 56건, 이 페이지 50줄", { exact: false })).toBeVisible();
      await page.getByRole("navigation", { name: "페이지", exact: true }).getByRole("link", { name: "다음", exact: true }).click();
      await expectQuery(page, { ...preserved, month: previousMonth, from: null, to: null, range_mode: null, page: "1" });
      await expect(eventLinks(page)).toHaveText([...ids.slice(50), beforeId]);
      await filters.getByRole("radio", { name: "직접 지정", exact: true }).check();
      await filters.getByLabel(/^지정 단위/).selectOption("date");
      await filters.getByLabel("시작 날짜 (포함)", { exact: true }).fill(previousDate);
      await filters.getByLabel("종료 날짜 (해당 일 포함)", { exact: true }).fill(firstDate);
      await page.getByRole("button", { name: "이 조건으로 조회", exact: true }).click();
      await expectQuery(page, { ...dateQuery, page: null });
      await expect(eventLinks(page)).toHaveText(ids.slice(0, 50));
      await page.getByRole("navigation", { name: "페이지", exact: true }).getByRole("link", { name: "다음", exact: true }).click();
      await expectQuery(page, { ...dateQuery, page: "1" });
      await expect(eventLinks(page)).toHaveText([...ids.slice(50), inclusiveEndId]);
    });

    await test.step("최근 기간과 직접 지정 상태를 새로고침과 뒤로가기에서 복원", async () => {
      const listUrl = page.url();
      for (const [label, days] of [["최근 24시간", 1], ["최근 7일", 7]] as const) {
        const draftQuery = days === 1 ? `${queryText}-draft` : queryText;
        await filters.getByLabel("이벤트 또는 고객 찾기", { exact: true }).fill(draftQuery);
        await expect(filters.getByText("조건 변경됨. 조회 버튼을 눌러 적용하세요.", { exact: true })).toBeVisible();
        await filters.getByRole("button", { name: label, exact: true }).click();
        await expectQuery(page, { ...preserved, q: draftQuery, month: null, range_mode: null, page: null });
        await expect(filters.getByRole("radio", { name: "직접 지정", exact: true })).toBeChecked();
        await expect(filters.getByLabel(/^지정 단위/)).toHaveValue("datetime");
        const selected = new URL(page.url()).searchParams;
        const from = selected.get("from");
        const to = selected.get("to");
        expect(from).not.toBeNull();
        expect(to).not.toBeNull();
        expect(Date.parse(to!) - Date.parse(from!)).toBe(days * 86_400_000);
        const kst = (value: string) => new Date(Date.parse(value) + 9 * 3600_000).toISOString().slice(0, 19);
        const expectedFrom = kst(from!);
        const expectedTo = kst(to!);
        await expect(filters.getByLabel("시작 시각 (포함)", { exact: true })).toHaveValue(new RegExp(`^${expectedFrom.replace(/:00$/, "(?::00)?")}$`));
        await expect(filters.getByLabel("종료 시각 (미포함)", { exact: true })).toHaveValue(new RegExp(`^${expectedTo.replace(/:00$/, "(?::00)?")}$`));
        const appliedSummary = await periodSummary.innerText();
        await page.reload();
        await expect(periodSummary).toHaveText(appliedSummary);
        await expect(filters.getByRole("radio", { name: "직접 지정", exact: true })).toBeChecked();
      }
      await page.goto(listUrl);
      await expectQuery(page, { ...dateQuery, page: "1" });
      await filters.getByRole("button", { name: "최근 24시간", exact: true }).click();
      await expect(filters.getByLabel(/^지정 단위/)).toHaveValue("datetime");
      await page.goBack();
      await expectQuery(page, { ...dateQuery, page: "1" });
      await expect(filters.getByLabel(/^지정 단위/)).toHaveValue("date");
      await expect(filters.getByLabel("시작 날짜 (포함)", { exact: true })).toHaveValue(previousDate);
      await expect(filters.getByLabel("종료 날짜 (해당 일 포함)", { exact: true })).toHaveValue(firstDate);
      await expect(eventLinks(page)).toHaveText([...ids.slice(50), inclusiveEndId]);
    });

    await test.step("한글과 슬래시 거래 ID 상세 딥링크와 같은 목록 복귀", async () => {
      const listSearch = new URL(page.url()).searchParams.toString();
      await page.getByRole("link", { name: ids[54], exact: true }).click();
      const drawer = page.getByRole("dialog", { name: "이벤트 상세", exact: true });
      await expect(drawer).toBeVisible();
      await expectQuery(page, { ...dateQuery, page: "1", transaction_id: ids[54] });
      await expect(drawer.getByText(ids[54], { exact: true })).toBeVisible();
      await expect(drawer.locator("pre")).toContainText(`"transaction_id": "${ids[54]}"`);
      await page.reload();
      await expect(drawer).toBeVisible();
      await expect(drawer.locator("pre")).toContainText(`"transaction_id": "${ids[54]}"`);
      await drawer.getByRole("button", { name: "닫기", exact: true }).click();
      await expect(drawer).toHaveCount(0);
      await expect.poll(() => new URL(page.url()).searchParams.toString()).toBe(listSearch);
      await expect(eventLinks(page)).toHaveText([...ids.slice(50), inclusiveEndId]);
    });
    await test.step("390px에서도 직접 지정은 보이고 상세 조건은 키보드로 펼친다", async () => {
      await page.setViewportSize({ width: 390, height: 844 });
      await page.goto(`/events?month=${previousMonth}`);
      await expect(filters.getByRole("radio", { name: "직접 지정", exact: true })).toBeVisible();
      await expect(filters.getByLabel(/^정렬 기준/)).toBeHidden();
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
      if (process.env.EVENT_PERIOD_MOBILE_SCREENSHOT_PATH) await screenshotFilters(process.env.EVENT_PERIOD_MOBILE_SCREENSHOT_PATH);
      await filters.getByRole("radio", { name: "직접 지정", exact: true }).press("Space");
      await expect(filters.getByLabel("시작 날짜 (포함)", { exact: true })).toBeVisible();
      await expect(filters.getByLabel("종료 날짜 (해당 일 포함)", { exact: true })).toBeVisible();
      await advanced.locator("summary").press("Enter");
      await expect(filters.getByLabel(/^정렬 기준/)).toBeVisible();
      await advanced.locator("summary").press("Enter");
      await expect(filters.getByLabel(/^정렬 기준/)).toBeHidden();
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
      if (process.env.EVENT_PERIOD_MOBILE_CUSTOM_SCREENSHOT_PATH) await screenshotFilters(process.env.EVENT_PERIOD_MOBILE_CUSTOM_SCREENSHOT_PATH);
    });

    await test.step("매핑 진단도 21번째 이후의 중복 이름 고객을 검색해 제출한다", async () => {
      await page.setViewportSize({ width: 1280, height: 900 });
      await page.goto(`/event-issues?month=${previousMonth}`);
      await expectQuery(page, { month: previousMonth, organization_id: organizationId });
      const before = page.url();
      await page.getByRole("combobox", { name: "고객", exact: true }).fill("자유 기간 검증 고객");
      const options = page.locator(".ant-select-dropdown .ant-select-item-option");
      await expect(options.filter({ hasText: "자유 기간 검증 고객" })).toHaveCount(2);
      await options.filter({ hasText: customerId }).click();
      expect(page.url()).toBe(before);
      await page.getByRole("button", { name: "진단 조회", exact: true }).click();
      await expectQuery(page, { customer_id: customerId, month: previousMonth });
      await expect(page.locator('input[name="customer_id"]')).toHaveValue(customerId);
    });

  } catch (error) {
    let message = error instanceof Error ? error.message : "이벤트 실제 화면 검증을 마치지 못했습니다.";
    for (const secret of secrets) message = message.split(secret).join("[redacted]");
    failure = new Error(message);
  } finally {
    await context?.close();
  }
  if (failure) throw failure;
});
