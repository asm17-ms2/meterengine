import { test, expect, type APIRequestContext, type Locator, type Page } from "@playwright/test";

const FIXTURE_URL = "http://127.0.0.1:38150";
const INVOICE_ID = "11111111-1111-4111-8111-111111111111";
const ENDPOINT_ID = "33333333-3333-4333-8333-333333333333";
type FixtureState = {
  kind: string;
  cancellationCalls: { idempotencyKey: string; amount: number; reason: string }[];
  cancellationEffects: number;
  cancellationCompletedAfterMs: number | null;
  cancellationCount: number;
  retryCalls: number;
  deliveryStatus: string;
  keyCalls: { label: string; rateLimitPerMinute: number | null }[];
  keyCount: number;
  activeOrganizationId: string;
  initialSessionReturned: boolean;
  customerQueries: { expectedOrganizationId: string | null; activeOrganizationId: string }[];
  invoiceListCalls: Record<string, string>[];
  invoiceSearchCalls: number;
};

async function readState(request: APIRequestContext): Promise<FixtureState> {
  return (await request.get(`${FIXTURE_URL}/__state`)).json();
}

async function control(request: APIRequestContext, scenario: string) {
  expect((await request.post(`${FIXTURE_URL}/__control`, { data: { scenario } })).ok()).toBe(true);
}

async function openCancellation(page: Page) {
  await page.getByRole("button", { name: "일부 금액 취소", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel(/^취소 금액 \(원\)/).fill("1000");
  await dialog.getByLabel(/^취소 사유/).fill("계약 테스트 취소");
  return dialog;
}

async function closeDialog(dialog: Locator) {
  await dialog.locator("button.ant-modal-close").click();
  await expect(dialog).toHaveCount(0);
}

test.beforeEach(async ({ context, request }) => {
  expect((await request.post(`${FIXTURE_URL}/__control`, { data: { reset: true } })).ok()).toBe(true);
  await context.addCookies([{ name: "me_session", value: "workspace-contract-session", url: "http://localhost:33150" }]);
});

test("격리 계약: 6초 걸린 부분 취소가 성공하고 한 번만 전송된다", async ({ page, request }) => {
  await control(request, "delayed-cancellation");
  await page.goto(`/payments/${INVOICE_ID}`);
  const dialog = await openCancellation(page);
  await dialog.getByRole("button", { name: "입력한 금액 취소 요청", exact: true }).click();
  await expect.poll(async () => (await readState(request)).cancellationCalls.length).toBe(1);
  await expect(dialog.locator("form")).toHaveAttribute("aria-busy", "true");
  await expect(dialog).toHaveCount(0, { timeout: 15_000 });
  await expect(page.getByRole("cell", { name: "계약 테스트 취소", exact: true })).toBeVisible();
  const state = await readState(request);
  expect(state.kind).toBe("isolated-workspace-contract");
  expect(state.cancellationCompletedAfterMs).toBeGreaterThanOrEqual(5_900);
  expect(state.cancellationCalls).toHaveLength(1);
  expect(state.cancellationEffects).toBe(1);
  expect(state.cancellationCount).toBe(1);
  expect(state.cancellationCalls[0].idempotencyKey).toMatch(/^[0-9a-f-]{36}$/);
});

test("격리 계약: 취소 응답 유실은 재제출을 잠그고 다시 연 창에서도 요청 ID를 유지한다", async ({ page, request }) => {
  await control(request, "lost-cancellation-response");
  await page.goto(`/payments/${INVOICE_ID}`);
  let dialog = await openCancellation(page);
  const originalRequestId = await dialog.locator('input[name="request_id"]').inputValue();
  await dialog.getByRole("button", { name: "입력한 금액 취소 요청", exact: true }).click();
  await expect(dialog.getByRole("alert")).toContainText("처리 결과를 확인하지 못했습니다");
  await expect(dialog.getByRole("button", { name: /입력한 금액 취소 요청$/ })).toBeDisabled();
  await expect(dialog.getByRole("link", { name: "결제와 취소 내역 새로 조회" })).toBeVisible();
  expect((await readState(request)).cancellationCalls).toHaveLength(1);
  page.once("dialog", confirmation => confirmation.accept());
  await closeDialog(dialog);
  dialog = await openCancellation(page);
  expect(await dialog.locator('input[name="request_id"]').inputValue()).toBe(originalRequestId);
  await dialog.getByRole("button", { name: "입력한 금액 취소 요청", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  const state = await readState(request);
  expect(state.cancellationCalls).toHaveLength(2);
  expect(state.cancellationCalls.every(call => call.idempotencyKey === originalRequestId)).toBe(true);
  expect(state.cancellationEffects).toBe(1);
  expect(state.cancellationCount).toBe(1);
  await expect(page.getByRole("cell", { name: "계약 테스트 취소", exact: true })).toBeVisible();
});

test("격리 계약: 확정 실패 뒤 새 취소 요청을 선택해야 새 요청 ID로 복구한다", async ({ page, request }) => {
  await control(request, "rejected-cancellation-first");
  await page.goto(`/payments/${INVOICE_ID}`);
  const dialog = await openCancellation(page);
  const originalRequestId = await dialog.locator('input[name="request_id"]').inputValue();
  await dialog.getByRole("button", { name: "입력한 금액 취소 요청", exact: true }).click();
  await expect(dialog.getByRole("alert")).toContainText("수신 서버가 취소를 거절했습니다");
  await expect(dialog.getByRole("button", { name: /입력한 금액 취소 요청$/ })).toBeDisabled();
  expect((await readState(request)).cancellationCalls).toHaveLength(1);
  expect((await readState(request)).cancellationEffects).toBe(0);
  await dialog.getByRole("button", { name: "새 취소 요청", exact: true }).click();
  const newRequestId = await dialog.locator('input[name="request_id"]').inputValue();
  expect(newRequestId).not.toBe(originalRequestId);
  await dialog.getByLabel(/^취소 금액 \(원\)/).fill("1000");
  await dialog.getByLabel(/^취소 사유/).fill("계약 테스트 취소");
  await dialog.getByRole("button", { name: "입력한 금액 취소 요청", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  const state = await readState(request);
  expect(state.cancellationCalls.map(call => call.idempotencyKey)).toEqual([originalRequestId, newRequestId]);
  expect(state.cancellationEffects).toBe(1);
  expect(state.cancellationCount).toBe(2);
});

test("격리 계약: 웹훅 재전송의 202 빈 응답을 성공으로 받고 발송 상태를 갱신한다", async ({ page, request }) => {
  await page.goto(`/integrations/${ENDPOINT_ID}`);
  const row = page.getByRole("row").filter({ hasText: "결제 성공" });
  const retry = row.getByRole("button", { name: "다시 보내기", exact: true });
  await retry.click();
  const dialog = page.getByRole("dialog");
  await dialog.getByRole("button", { name: "다시 보내기", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(page.getByRole("cell", { name: "재시도 대기", exact: true })).toBeVisible();
  await expect(retry).toHaveCount(0);
  await expect(page.getByText("응답을 읽지 못했습니다", { exact: false })).toHaveCount(0);
  const state = await readState(request);
  expect(state.retryCalls).toBe(1);
  expect(state.deliveryStatus).toBe("pending");
});

test("격리 계약: 발급 중 닫기를 막고 도착한 비밀값을 그 창에서만 보여 준다", async ({ page, request }) => {
  await control(request, "delayed-key");
  await page.goto("/integrations");
  await page.getByRole("button", { name: "API 키 발급", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel(/^키 이름/).fill("대기 중 발급");
  await dialog.getByRole("button", { name: "API 키 발급", exact: true }).click();
  await expect.poll(async () => (await readState(request)).keyCalls.length).toBe(1);
  const close = dialog.locator("button.ant-modal-close");
  await expect(close).toBeDisabled();
  const bounds = await close.boundingBox();
  expect(bounds).not.toBeNull();
  await page.mouse.click(bounds!.x + bounds!.width / 2, bounds!.y + bounds!.height / 2);
  await page.keyboard.press("Escape");
  await page.locator(".ant-modal-wrap").click({ position: { x: 5, y: 5 } });
  await expect(dialog).toBeVisible();
  await expect(dialog.locator(".secret-result code")).toBeVisible();
  await expect(close).toBeEnabled();
  await expect(dialog.getByRole("button", { name: /API 키 발급$/ })).toBeDisabled();
  const state = await readState(request);
  expect(state.keyCount).toBe(1);
  expect(state.keyCalls).toHaveLength(1);
  expect(state.keyCalls[0].rateLimitPerMinute).toBeNull();
  await closeDialog(dialog);
  await page.getByRole("button", { name: "API 키 발급", exact: true }).click();
  await expect(dialog.locator(".secret-result code")).toHaveCount(0);
  expect(await page.evaluate(() => localStorage.length + sessionStorage.length)).toBe(0);
});

test("격리 계약: 키 한도는 양수 정수로 보내고 비우면 null로 보낸다", async ({ page, request }) => {
  await page.goto("/integrations");
  for (const limit of ["120", ""]) {
    await page.getByRole("button", { name: "API 키 발급", exact: true }).click();
    const dialog = page.getByRole("dialog");
    await dialog.getByLabel(/^키 이름/).fill(limit ? "유한 한도" : "무제한");
    await dialog.getByLabel(/^분당 요청 한도/).fill(limit);
    await dialog.getByRole("button", { name: "API 키 발급", exact: true }).click();
    await expect(dialog.locator(".secret-result code")).toBeVisible();
    await closeDialog(dialog);
  }
  const state = await readState(request);
  expect(state.keyCalls.map(call => call.rateLimitPerMinute)).toEqual([120, null]);
  expect(state.keyCount).toBe(2);
});


test("격리 계약: 전액 취소는 잔액을 명시하고 부분 취소의 빈 금액은 전송하지 않는다", async ({ page, request }) => {
  await page.goto(`/payments/${INVOICE_ID}`);
  await page.getByRole("button", { name: "일부 금액 취소", exact: true }).click();
  let dialog = page.getByRole("dialog");
  await dialog.getByLabel(/^취소 사유/).fill("빈 금액 방지");
  await dialog.getByRole("button", { name: "입력한 금액 취소 요청", exact: true }).click();
  expect((await readState(request)).cancellationCalls).toHaveLength(0);
  page.once("dialog", confirmation => confirmation.accept());
  await closeDialog(dialog);
  await page.getByRole("button", { name: "잔액 전액 취소", exact: true }).click();
  dialog = page.getByRole("dialog");
  await expect(dialog.getByRole("button", { name: /11,000원 전액 취소 요청/ })).toBeVisible();
  expect(await dialog.locator('input[name="amount"]').inputValue()).toBe("11000");
  await dialog.getByLabel(/^취소 사유/).fill("명시적 전액 취소");
  await dialog.getByRole("button", { name: /전액 취소 요청/ }).click();
  await expect(dialog).toHaveCount(0);
  expect((await readState(request)).cancellationCalls[0].amount).toBe(11000);
});


test("격리 계약: 최초 화면을 읽는 동안 소속이 바뀌어도 다른 도입사의 고객이 노출되지 않는다", async ({ page, request }) => {
  const organizationId = "55555555-5555-4555-8555-555555555555";
  const switchedOrganizationId = "88888888-8888-4888-8888-888888888888";
  await control(request, "organization-switch-after-session");
  await page.goto("/customers");
  await expect(page.getByRole("heading", { name: "고객", exact: true })).toBeVisible();
  await expect(page.getByRole("alert").filter({ hasText: "도입사 선택이 바뀌어 조회를 중단했습니다." })).toBeVisible();
  await expect(page.getByText("다른 도입사 B의 비공개 고객", { exact: true })).toHaveCount(0);
  await expect.poll(() => new URL(page.url()).searchParams.get("organization_id")).toBe(organizationId);
  const state = await readState(request);
  expect(state.initialSessionReturned).toBe(true);
  expect(state.activeOrganizationId).toBe(switchedOrganizationId);
  expect(state.customerQueries.length).toBeGreaterThan(0);
  expect(state.customerQueries.every(query => query.expectedOrganizationId === organizationId && query.activeOrganizationId === switchedOrganizationId)).toBe(true);
  await page.reload();
  await expect(page.getByRole("heading", { name: "도입사 선택이 바뀌었습니다", exact: true })).toBeVisible();
  await expect(page.getByText("다른 도입사 B의 비공개 고객", { exact: true })).toHaveCount(0);

  expect((await request.post(`${FIXTURE_URL}/__control`, { data: { reset: true, scenario: "organization-switch-after-search" } })).ok()).toBe(true);
  await page.goto("/customers");
  await expect(page.getByRole("row").filter({ hasText: "처음 도입사 A 고객 0" })).toHaveCount(1);
  await expect.poll(() => new URL(page.url()).searchParams.get("organization_id")).toBe(organizationId);
  const next = page.getByRole("navigation", { name: "페이지", exact: true }).getByRole("link", { name: "다음", exact: true });
  const href = await next.getAttribute("href");
  expect(href).not.toBeNull();
  expect(new URL(href!, "http://localhost:33150").searchParams.get("organization_id"), "최초 SSR의 페이지 링크도 렌더된 도입사를 유지").toBe(organizationId);
  expect((await readState(request)).activeOrganizationId).toBe(switchedOrganizationId);
  await next.click();
  await expect(page.getByRole("alert").filter({ hasText: "도입사 선택이 바뀌어 조회를 중단했습니다." })).toBeVisible();
  expect(new URL(page.url()).searchParams.get("organization_id")).toBe(organizationId);
  const blockedQuery = (await readState(request)).customerQueries.at(-1);
  expect(blockedQuery).toEqual({ expectedOrganizationId: organizationId, activeOrganizationId: switchedOrganizationId });
  await expect(page.getByText("다른 도입사 B의 비공개 고객", { exact: true })).toHaveCount(0);
  await page.reload();
  await expect(page.getByRole("heading", { name: "도입사 선택이 바뀌었습니다", exact: true })).toBeVisible();
});


test("격리 계약: 확정된 월의 0원 고객을 구분하고 검색 페이지 반복 없이 조건별 월 조회를 한다", async ({ page, request }) => {
  const customerId = "12121212-1212-4212-8212-121212121212";
  await control(request, "finalized-month-zero-customer");
  await page.goto(`/billing?month=2026-10&customer_id=${customerId}&sort=amount&order=desc&environment=legacy`);
  await expect(page.getByText("확정됨 (finalized)", { exact: true })).toBeVisible();
  await expect(page.getByText("청구 예정액이 0원인 고객 1곳은 인보이스가 만들어지지 않았습니다.", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "모두 펼치기", exact: true })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "모두 접기", exact: true })).toHaveCount(0);
  const monthLink = page.getByRole("link", { name: "이 달에 확정된 인보이스 101건 보기", exact: true });
  await expect(monthLink).toBeVisible();
  const monthHref = await monthLink.getAttribute("href");
  expect(monthHref).not.toBeNull();
  const monthDestination = new URL(monthHref!, "http://localhost:33150");
  expect(monthDestination.searchParams.has("customer_id")).toBe(false);
  expect(monthDestination.searchParams.get("organization_id")).toBe("55555555-5555-4555-8555-555555555555");
  expect(monthDestination.searchParams.get("environment") ?? "legacy").toBe("legacy");
  await expect(page.getByText(/^다른 고객의 확정 청구/)).toHaveCount(0);
  const destinationHref = await page.getByRole("link", { name: "인보이스 보기", exact: true }).getAttribute("href");
  expect(destinationHref).not.toBeNull();
  const destination = new URL(destinationHref!, "http://localhost:33150");
  expect(destination.pathname).toBe("/invoices");
  expect(destination.searchParams.get("customer_id")).toBe(customerId);
  expect(destination.searchParams.get("month")).toBe("2026-10");
  expect(destination.searchParams.has("sort")).toBe(false);
  expect(destination.searchParams.has("order")).toBe(false);
  await page.getByRole("button", { name: "0원 고객 1곳 보기", exact: true }).click();
  await expect(page.getByRole("row").filter({ hasText: "인보이스 없는 0원 고객" })).toHaveCount(1);
  await expect(page.getByText(/^다른 고객의 확정 청구/)).toHaveCount(0);
  const state = await readState(request);
  expect(state.invoiceSearchCalls, "월 전체 조회는 검색 페이지를 반복하지 않음").toBe(0);
  expect(state.invoiceListCalls).toHaveLength(2);
  expect(state.invoiceListCalls).toContainEqual({ month: "2026-10", customer_id: customerId });
  expect(state.invoiceListCalls).toContainEqual({ month: "2026-10" });
  for (const call of state.invoiceListCalls) {
    expect(call.page).toBeUndefined();
    expect(call.size).toBeUndefined();
  }
});

test("the payments list can show only the month's failed payments and go back to all of them", async ({ page, request }) => {
  await control(request, "failed-payments");
  await page.goto("/payments?month=2026-10&payment_status=failed");
  await expect(page.getByText("결제 실패 2건만 표시합니다.", { exact: false })).toBeVisible();
  for (const index of [0, 1]) await expect(page.getByRole("link", { name: `다른 고객의 확정 청구 ${index}`, exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "다른 고객의 확정 청구 2", exact: true })).toHaveCount(0);
  await expect(page.locator('form input[type="hidden"][name="payment_status"]')).toHaveValue("failed");
  await page.getByRole("link", { name: "전체 수납 보기", exact: true }).click();
  await expect(page).toHaveURL(url => url.pathname === "/payments" && !url.searchParams.has("payment_status") && url.searchParams.get("month") === "2026-10");
  await expect(page.getByRole("link", { name: "다른 고객의 확정 청구 2", exact: true })).toBeVisible();
});
