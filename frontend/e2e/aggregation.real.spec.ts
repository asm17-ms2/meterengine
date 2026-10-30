import { expect, test, type BrowserContext, type Locator, type Page } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { CONSENT_DOCUMENTS, CONSENT_VERSION } from "../src/lib/auth/consents";

const API_URL = process.env.S1_API_URL ?? "http://127.0.0.1:38147";
const PASSWORD = "local-aggregation-e2e-password";
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

type MetricCase = {
  aggregation: string;
  label: string;
  raw: number;
  quantity: number;
  unit?: number;
  rounding?: string;
  roundingLabel?: string;
  percentile?: number;
  ui?: boolean;
};

const METRICS: MetricCase[] = [
  { aggregation: "sum", label: "합계 (SUM)", raw: 93, quantity: 93 },
  { aggregation: "count", label: "이벤트 수 (COUNT)", raw: 8, quantity: 8, ui: true },
  { aggregation: "count_distinct", label: "고유 값 수 (COUNT DISTINCT)", raw: 5, quantity: 5 },
  { aggregation: "min", label: "최솟값 (MIN)", raw: 11, quantity: 11 },
  { aggregation: "max", label: "최댓값 (MAX)", raw: 40, quantity: 13, unit: 3, rounding: "floor", roundingLabel: "내림 (FLOOR)", ui: true },
  { aggregation: "avg", label: "평균 (AVG)", raw: 23.25, quantity: 3, unit: 10, rounding: "ceil", roundingLabel: "올림 (CEIL)", ui: true },
  { aggregation: "first", label: "첫 값 (FIRST)", raw: 11, quantity: 11 },
  { aggregation: "last", label: "마지막 값 (LAST)", raw: 40, quantity: 40 },
  { aggregation: "median", label: "중앙값 (MEDIAN)", raw: 21, quantity: 21 },
  { aggregation: "percentile", label: "백분위수 (PERCENTILE)", raw: 37.15, quantity: 37.15, percentile: 95, ui: true },
];

async function choose(page: Page, scope: Locator, label: string, option: string) {
  const combobox = scope.getByLabel(label, { exact: true });
  await combobox.press("ArrowDown");
  await expect(combobox).toHaveAttribute("aria-expanded", "true");
  const listId = await combobox.getAttribute("aria-controls");
  if (!listId) throw new Error(`${label}의 선택 목록 연결이 없습니다.`);
  const popup = page.locator(".ant-select-dropdown").filter({ has: page.locator(`[id="${listId}"]`) });
  for (let attempt = 0; attempt < 20; attempt++) {
    const activeId = await combobox.getAttribute("aria-activedescendant");
    if (activeId && await page.locator(`[id="${activeId}"]`).getAttribute("aria-label") === option) {
      await combobox.press("Enter");
      await expect(combobox).toHaveAttribute("aria-expanded", "false");
      await expect(popup).toBeHidden();
      return;
    }
    await combobox.press("ArrowDown");
  }
  throw new Error(`${label}에서 ${option} 선택 항목을 찾지 못했습니다.`);
}

function safeFailure(error: unknown, secrets: Set<string>) {
  let message = error instanceof Error ? error.message : "집계와 청구 E2E를 마치지 못했습니다.";
  for (const secret of secrets) if (secret) message = message.split(secret).join("[redacted]");
  return new Error(message.replace(/me_(?:test|live|reg|invite)_[A-Za-z0-9]+/g, "[redacted token]"));
}

test("집계 10종과 단위 변환을 수집부터 사용량, 청구 예정액, 확정까지 연결한다", async ({ browser, request }) => {
  const suffix = randomUUID();
  const email = `aggregation-${suffix}@fe-e2e.local`;
  const eventType = `aggregation-event-${suffix}`;
  const metricCode = (metric: MetricCase) => `aggregation-${metric.aggregation}-${suffix}`;
  const metricName = (metric: MetricCase) => `검증 ${metric.label}`;
  const fractionCode = (aggregation: string) => `fraction-${aggregation}-${suffix}`;
  const currentMonth = new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit" }).format(new Date());
  const currentStart = Date.parse(`${currentMonth}-01T00:00:00+09:00`);
  const previousDay = new Date(currentStart - 1).toLocaleDateString("sv-SE", { timeZone: "Asia/Seoul" });
  const month = currentMonth;
  const secrets = new Set([PASSWORD, OPERATOR_PASSWORD]);
  let context: BrowserContext | undefined;
  let failure: Error | undefined;

  try {
    const signup = await request.post(`${API_URL}/v1/signups`, { data: {
      organization_name: `집계 E2E ${suffix}`, name: "집계 검증 대표관리자", email, password: PASSWORD,
      consents: CONSENT_DOCUMENTS.map(({ document }) => ({ document, document_version: CONSENT_VERSION })),
    } });
    expect(signup.status()).toBe(201);
    const organizationId = (await signup.json()).organization.id;
    const operator = await request.post(`${API_URL}/v1/sessions`, { data: { email: OPERATOR_EMAIL, password: OPERATOR_PASSWORD } });
    expect(operator.status()).toBe(201);
    const operatorToken = (await operator.json()).token;
    if (typeof operatorToken !== "string" || !operatorToken) throw new Error("운영자 검증 세션이 없습니다.");
    secrets.add(operatorToken);
    expect((await request.put(`${API_URL}/v1/organizations/${organizationId}/status`, { headers: { Authorization: `Bearer ${operatorToken}` }, data: { status: "active" } })).status()).toBe(200);

    context = await browser.newContext({ baseURL: "http://localhost:33147", viewport: { width: 1280, height: 900 } });
    const page = await context.newPage();
    await page.goto("/login?returnTo=%2Fbillable-metrics");
    await page.getByLabel("이메일", { exact: true }).fill(email);
    await page.getByLabel("비밀번호", { exact: true }).fill(PASSWORD);
    await page.getByRole("button", { name: "로그인", exact: true }).click();
    await expect(page).toHaveURL(/\/billable-metrics(?:\?.*)?$/);
    const token = (await context.cookies()).find(cookie => cookie.name === "me_session")?.value;
    if (!token) throw new Error("대표관리자 검증 세션이 없습니다.");
    secrets.add(token);
    const headers = { Authorization: `Bearer ${token}` };
    const calculation = (metric: MetricCase) => ({
      name: metricName(metric), event_type: eventType, aggregation: metric.aggregation,
      target_property: metric.aggregation === "count" ? null : "units", dimension_properties: [],
      unit_size: metric.unit ?? 1, rounding_mode: metric.rounding ?? "none", rounding_precision: 0,
      percentile: metric.percentile ?? null,
    });

    await test.step("COUNT의 속성 생략과 조건부 백분위, 단위 변환을 화면에서 등록", async () => {
      for (const metric of METRICS.filter(metric => metric.ui)) {
        await page.getByRole("button", { name: "미터 등록", exact: true }).first().click();
        const dialog = page.getByRole("dialog", { name: "미터 등록", exact: true });
        await dialog.getByLabel("코드", { exact: true }).fill(metricCode(metric));
        await dialog.getByLabel("이름", { exact: true }).fill(metricName(metric));
        await dialog.getByLabel("이벤트 타입", { exact: true }).fill(eventType);
        await choose(page, dialog, "집계 함수", metric.label);
        const target = dialog.getByLabel("집계 대상 속성", { exact: true });
        if (metric.aggregation === "count") await expect(target).toHaveCount(0);
        else await target.fill("units");
        const percentile = dialog.getByLabel("백분위 (0-100)", { exact: true });
        if (metric.percentile !== undefined) await percentile.fill(String(metric.percentile));
        else await expect(percentile).toHaveCount(0);
        await expect(dialog.getByLabel("과금 단위 크기", { exact: true })).toHaveValue("1");
        if (metric.unit) await dialog.getByLabel("과금 단위 크기", { exact: true }).fill(String(metric.unit));
        if (metric.roundingLabel) {
          await choose(page, dialog, "반올림 방식", metric.roundingLabel);
          await expect(dialog.getByLabel("소수 자릿수", { exact: true })).toHaveValue("0");
        }
        await dialog.getByRole("button", { name: "저장", exact: true }).click();
        await expect(dialog).toHaveCount(0);
        await expect(page.getByRole("row").filter({ hasText: metricCode(metric) })).toHaveCount(1);
      }
      for (const metric of METRICS.filter(metric => !metric.ui)) {
        const created = await request.post(`${API_URL}/v1/billable-metrics`, { headers, data: { code: metricCode(metric), ...calculation(metric) } });
        expect(created.status()).toBe(201);
      }
      await page.reload();
      for (const metric of METRICS) await expect(page.getByRole("row").filter({ hasText: metricCode(metric) })).toHaveCount(1);
      if (process.env.AGGREGATION_METRICS_SCREENSHOT_PATH) await page.screenshot({ path: process.env.AGGREGATION_METRICS_SCREENSHOT_PATH });
    });

    let customerId = "";
    let readHeaders: Record<string, string> = {};
    await test.step("서버 간 API 키로 중복과 월 경계를 포함한 원본 이벤트 수집", async () => {
      const customer = await request.post(`${API_URL}/v1/customers`, { headers, data: { name: "집계 검증 고객", external_id: `aggregation-customer-${suffix}` } });
      expect(customer.status()).toBe(201);
      customerId = (await customer.json()).id;
      const key = await request.post(`${API_URL}/v1/api-keys`, { headers, data: { label: "집계 E2E 수집과 조회", mode: "test", scopes: ["read", "ingest"] } });
      expect(key.status()).toBe(201);
      const secret = (await key.json()).secret;
      if (typeof secret !== "string" || !secret) throw new Error("수집 검증 키가 없습니다.");
      secrets.add(secret);
      readHeaders = { Authorization: `Bearer ${secret}` };
      const values: unknown[] = [11, 21, 21, 40, "21", true, { nested: 21 }, undefined];
      const events = values.map((value, index) => ({
        transaction_id: `aggregation-${suffix}-${index}`, customer_id: customerId, type: eventType,
        properties: value === undefined ? {} : { units: value }, timestamp: `${currentMonth}-01T00:00:${10 + index}+09:00`,
      }));
      const boundary = { ...events[0], transaction_id: `aggregation-${suffix}-boundary`, timestamp: `${previousDay}T23:59:59+09:00`, properties: { units: 999 } };
      const batch = await request.post(`${API_URL}/v1/event-batches`, { headers: readHeaders, data: { events: [...events, boundary, events[0]] } });
      expect(batch.status()).toBe(200);
      expect((await batch.json()).results.map((result: { result: string }) => result.result)).toEqual([...events.map(() => "stored"), "stored", "duplicate"]);
      for (const metric of METRICS) {
        expect((await request.post(`${API_URL}/v1/billable-metrics/${metricCode(metric)}/price-rates`, { headers, data: { dimension_values: {}, unit_price: 100, effective_from: month } })).status()).toBe(201);
      }
    });

    await test.step("원본 집계량과 과금량, 청구 라인의 수량과 금액을 조회", async () => {
      const usage = await request.get(`${API_URL}/v1/usage?month=${month}`, { headers: readHeaders });
      expect(usage.status()).toBe(200);
      const actual = (await usage.json()).billable_metric_usages;
      for (const metric of METRICS) {
        const row = actual.find((row: { code: string }) => row.code === metricCode(metric));
        expect(row).toMatchObject({ aggregation: metric.aggregation, target_property: metric.aggregation === "count" ? null : "units" });
        const customer = row.customers.find((entry: { customer_id: string }) => entry.customer_id === customerId);
        expect(customer.raw_quantity).toBe(metric.raw);
        expect(customer.quantity).toBe(metric.quantity);
        expect(customer.dimension_usages).toHaveLength(1);
        expect(customer.dimension_usages[0]).toMatchObject({ dimension_values: {}, raw_quantity: metric.raw, quantity: metric.quantity });
      }
      const draft = await request.get(`${API_URL}/v1/invoices/draft?month=${month}`, { headers: readHeaders });
      expect(draft.status()).toBe(200);
      const customer = (await draft.json()).customers.find((entry: { customer_id: string }) => entry.customer_id === customerId);
      expect(customer.amount).toBe(24_210);
      expect(customer.lines).toHaveLength(METRICS.length);
      for (const metric of METRICS) expect(customer.lines.find((line: { billable_metric_code: string }) => line.billable_metric_code === metricCode(metric))).toMatchObject({ quantity: metric.quantity, unit_price: 100, amount: Math.trunc(metric.quantity * 100 / 10) * 10 });
      expect(customer.unpriced_usages).toEqual([]);
      expect(customer.negative_usages).toEqual([]);
      await page.goto(`/usage?month=${month}&customer_id=${customerId}`);
      const table = page.locator(".data-desktop-table");
      for (const metric of METRICS) {
        const row = table.getByRole("row").filter({ hasText: metricCode(metric) });
        await expect(row).toHaveCount(1);
        await expect(row.getByRole("cell").last()).toContainText(String(metric.quantity));
      }
      if (process.env.AGGREGATION_USAGE_SCREENSHOT_PATH) await page.screenshot({ path: process.env.AGGREGATION_USAGE_SCREENSHOT_PATH });
    });

    await test.step("수집 뒤 계산식 변경은 막고 이름 변경은 허용", async () => {
      const average = METRICS.find(metric => metric.aggregation === "avg")!;
      const blocked = await request.put(`${API_URL}/v1/billable-metrics/${metricCode(average)}`, { headers, data: { ...calculation(average), unit_size: 20 } });
      expect(blocked.status()).toBe(409);
      expect((await blocked.json()).code).toBe("billable_metric_has_events");
      await page.goto("/billable-metrics");
      await page.getByRole("button", { name: `${metricName(average)} 수정`, exact: true }).click();
      const dialog = page.getByRole("dialog", { name: "미터 수정", exact: true });
      await dialog.getByLabel("이름", { exact: true }).fill("이름을 바꾼 평균 미터");
      await dialog.getByRole("button", { name: "저장", exact: true }).click();
      await expect(dialog).toHaveCount(0);
      await expect(page.getByRole("row").filter({ hasText: metricCode(average) })).toContainText("이름을 바꾼 평균 미터");
    });

    await test.step("390px에서 백분위와 단위 변환 필드가 화면 안에 배치", async () => {
      await page.setViewportSize({ width: 390, height: 844 });
      await page.getByRole("button", { name: "미터 등록", exact: true }).first().click();
      const dialog = page.getByRole("dialog", { name: "미터 등록", exact: true });
      await choose(page, dialog, "집계 함수", "백분위수 (PERCENTILE)");
      await choose(page, dialog, "반올림 방식", "반올림 (ROUND)");
      for (const label of ["백분위 (0-100)", "과금 단위 크기", "소수 자릿수"]) {
        const bounds = await dialog.getByLabel(label, { exact: true }).boundingBox();
        expect(bounds).not.toBeNull();
        expect(bounds!.x).toBeGreaterThanOrEqual(0);
        expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(390);
      }
      if (process.env.AGGREGATION_MOBILE_SCREENSHOT_PATH) await page.screenshot({ path: process.env.AGGREGATION_MOBILE_SCREENSHOT_PATH, fullPage: true });
      const layout = await dialog.evaluate(element => {
        const modal = element as HTMLElement;
        return {
          viewport: window.innerWidth, documentWidth: document.documentElement.scrollWidth,
          dialogWidth: modal.clientWidth, dialogScrollWidth: modal.scrollWidth,
          overflowingElements: Array.from(modal.querySelectorAll<HTMLElement>("*")).filter(child => {
            const bounds = child.getBoundingClientRect();
            return bounds.width > 0 && (child.scrollWidth > child.clientWidth + 1 || bounds.right > window.innerWidth || bounds.left < 0);
          }).slice(0, 24).map(child => ({
            tag: child.tagName, className: child.className, clientWidth: child.clientWidth, scrollWidth: child.scrollWidth,
            left: child.getBoundingClientRect().left, right: child.getBoundingClientRect().right,
            overflowX: getComputedStyle(child).overflowX, minWidth: getComputedStyle(child).minWidth,
          })),
        };
      });
      console.info("집계 모바일 레이아웃", JSON.stringify(layout));
      expect(layout.documentWidth, "390px 문서의 가로 너비").toBeLessThanOrEqual(layout.viewport);
      expect(layout.dialogScrollWidth, "390px 미터 폼의 내부 가로 너비").toBeLessThanOrEqual(layout.dialogWidth);
      const combobox = dialog.getByLabel("반올림 방식", { exact: true });
      await combobox.press("ArrowDown");
      await expect(combobox).toHaveAttribute("aria-expanded", "true");
      const listId = await combobox.getAttribute("aria-controls");
      if (!listId) throw new Error("반올림 선택 목록 연결이 없습니다.");
      const popup = page.locator(".ant-select-dropdown").filter({ has: page.locator(`[id="${listId}"]`) });
      await expect(popup).toBeVisible();
      await expect(popup).not.toHaveClass(/ant-slide-up-(?:appear|enter|leave)/);
      await expect.poll(() => popup.evaluate(element => getComputedStyle(element).opacity)).toBe("1");
      const popupBounds = await popup.boundingBox();
      console.info("집계 모바일 열린 선택 목록", JSON.stringify(popupBounds));
      if (process.env.AGGREGATION_MOBILE_SCREENSHOT_PATH) await page.screenshot({ path: process.env.AGGREGATION_MOBILE_SCREENSHOT_PATH.replace(/\.png$/, "-dropdown.png"), fullPage: true });
      expect(popupBounds).not.toBeNull();
      expect(popupBounds!.x).toBeGreaterThanOrEqual(0);
      expect(popupBounds!.x + popupBounds!.width, "390px에서 열린 반올림 선택 목록의 오른쪽").toBeLessThanOrEqual(390);
      await combobox.press("Escape");
      await expect(popup).toBeHidden();
      await dialog.getByRole("button", { name: "취소", exact: true }).click();
      await page.setViewportSize({ width: 1280, height: 900 });
    });

    await test.step("분수 사용량에 단가를 곱할 때 중간 소수 근사로 과소 청구하지 않는다", async () => {
      const fractionType = `fraction-event-${suffix}`;
      for (const aggregation of ["sum", "avg"]) {
        const metric = await request.post(`${API_URL}/v1/billable-metrics`, { headers, data: {
          code: fractionCode(aggregation), name: `분수 검증 ${aggregation}`, event_type: fractionType,
          aggregation, target_property: "units", dimension_properties: [], unit_size: aggregation === "sum" ? 3 : 1,
          rounding_mode: "none", rounding_precision: 0,
        } });
        expect(metric.status()).toBe(201);
        expect((await request.post(`${API_URL}/v1/billable-metrics/${fractionCode(aggregation)}/price-rates`, { headers, data: { dimension_values: {}, unit_price: 300, effective_from: month } })).status()).toBe(201);
      }
      const events = [1, 0, 0].map((units, index) => ({
        transaction_id: `fraction-${suffix}-${index}`, customer_id: customerId, type: fractionType,
        properties: { units }, timestamp: `${currentMonth}-01T01:00:${10 + index}+09:00`,
      }));
      const batch = await request.post(`${API_URL}/v1/event-batches`, { headers: readHeaders, data: { events } });
      expect(batch.status()).toBe(200);
      expect((await batch.json()).results.map((result: { result: string }) => result.result)).toEqual(["stored", "stored", "stored"]);
      const usage = await request.get(`${API_URL}/v1/usage?month=${month}`, { headers: readHeaders });
      expect(usage.status()).toBe(200);
      const actual = (await usage.json()).billable_metric_usages;
      for (const aggregation of ["sum", "avg"]) {
        const row = actual.find((row: { code: string }) => row.code === fractionCode(aggregation));
        expect(row.unit_size).toBe(aggregation === "sum" ? 3 : 1);
        const customer = row.customers.find((entry: { customer_id: string }) => entry.customer_id === customerId);
        const fraction = { raw_quantity_numerator: 1, raw_quantity_denominator: aggregation === "avg" ? 3 : 1 };
        expect(customer).toMatchObject(fraction);
        expect(customer.quantity).toBeCloseTo(1 / 3, 14);
        expect(customer.dimension_usages).toHaveLength(1);
        expect(customer.dimension_usages[0]).toMatchObject({ dimension_values: {}, ...fraction });
        expect(customer.dimension_usages[0].quantity).toBeCloseTo(1 / 3, 14);
      }
      const draft = await request.get(`${API_URL}/v1/invoices/draft?month=${month}`, { headers: readHeaders });
      expect(draft.status()).toBe(200);
      const customer = (await draft.json()).customers.find((entry: { customer_id: string }) => entry.customer_id === customerId);
      expect(customer.amount).toBe(24_410);
      expect(customer.lines).toHaveLength(METRICS.length + 2);
      for (const aggregation of ["sum", "avg"]) {
        const line = customer.lines.find((line: { billable_metric_code: string }) => line.billable_metric_code === fractionCode(aggregation));
        expect(line).toMatchObject({ quantity_numerator: 1, quantity_denominator: 3, unit_price: 300, amount: 100 });
        expect(line.quantity).toBeCloseTo(1 / 3, 14);
      }
    });

    await test.step("청구 검토에서 확정하고 서버 간 조회로 스냅샷 검증", async () => {
      await page.goto(`/billing?month=${month}`);
      for (const aggregation of ["sum", "avg"]) {
        const row = page.locator(".data-desktop-table").getByRole("row").filter({ hasText: fractionCode(aggregation) });
        await expect(row.getByText("계산 기준: 1 / 3", { exact: true })).toBeVisible();
        await expect(row.getByRole("cell").last()).toContainText("100원");
      }
      await page.setViewportSize({ width: 390, height: 844 });
      const mobile = page.locator(".data-mobile-records");
      await mobile.getByText("청구 계산 내역", { exact: true }).click();
      await expect(mobile.getByText("계산 기준: 1 / 3", { exact: true })).toHaveCount(2);
      await expect(mobile.getByText("계산 기준: 1 / 3", { exact: true }).first()).toBeVisible();
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
      await page.setViewportSize({ width: 1280, height: 900 });
      await expect(page.getByRole("button", { name: "월 전체 확정", exact: true })).toBeEnabled();
      await page.getByRole("button", { name: "월 전체 확정", exact: true }).click();
      await page.getByRole("dialog").getByRole("button", { name: "확정", exact: true }).click();
      await expect(page.getByRole("dialog")).toContainText("1건을 확정했습니다");
      const invoices = await request.get(`${API_URL}/v1/invoices?month=${month}&customer_id=${customerId}`, { headers: readHeaders });
      expect(invoices.status()).toBe(200);
      const invoice = (await invoices.json()).invoices[0];
      expect(invoice).toMatchObject({ supply_amount: 24_410, tax_amount: 2_441, total_amount: 26_851 });
      expect(invoice.lines).toHaveLength(METRICS.length + 2);
      for (const metric of METRICS) expect(invoice.lines.find((line: { billable_metric_code: string }) => line.billable_metric_code === metricCode(metric))).toMatchObject({ quantity: metric.quantity, amount: Math.trunc(metric.quantity * 100 / 10) * 10 });
      for (const aggregation of ["sum", "avg"]) expect(invoice.lines.find((line: { billable_metric_code: string }) => line.billable_metric_code === fractionCode(aggregation))).toMatchObject({ quantity_numerator: 1, quantity_denominator: 3, unit_price: 300, amount: 100 });
      const repeated = await request.post(`${API_URL}/v1/invoices?month=${month}`, { headers });
      expect(repeated.status()).toBe(200);
      expect((await repeated.json()).finalized_count).toBe(0);
    });
  } catch (error) {
    failure = safeFailure(error, secrets);
  } finally {
    await context?.close();
  }
  if (failure) throw failure;
});
