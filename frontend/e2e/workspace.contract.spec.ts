import { test, expect, type APIRequestContext, type Locator, type Page } from "@playwright/test";

const FIXTURE_URL = "http://127.0.0.1:38150";
const INVOICE_ID = "11111111-1111-4111-8111-111111111111";
const ENDPOINT_ID = "33333333-3333-4333-8333-333333333333";
type FixtureState = {
  kind: string;
  customerOptionQueries: string[];
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
  await expect(page.getByRole("cell", { name: "발송 대기", exact: true })).toBeVisible();
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
  page.once("dialog", confirm => void confirm.accept());
  await closeDialog(dialog);
  await page.getByRole("button", { name: "API 키 발급", exact: true }).click();
  await expect(dialog.locator(".secret-result code")).toHaveCount(0);
  expect(await page.evaluate(() => localStorage.length + sessionStorage.length)).toBe(0);
});

test("격리 계약: 업무 환경이 바뀐 API 키 발급은 공용 문구로 거절하고 전송하지 않는다", async ({ page, request }) => {
  await page.goto("/integrations");
  await page.getByRole("button", { name: "API 키 발급", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel(/^키 이름/).fill("바뀐 환경");
  await dialog.locator('input[name="environment"]').evaluate(input => { (input as HTMLInputElement).value = "test"; });
  await dialog.getByRole("button", { name: "API 키 발급", exact: true }).click();
  await expect(dialog.getByRole("alert")).toContainText("열어 둔 화면의 업무 환경이 바뀌었습니다.");
  expect((await readState(request)).keyCalls).toHaveLength(0);
});

test("격리 계약: 업무 환경 전환은 목적지를 밝힌 확인 창을 거치고 화살표 키로는 바뀌지 않는다", async ({ page }) => {
  let nativeDialogs = 0;
  page.on("dialog", dialog => { nativeDialogs++; void dialog.dismiss(); });
  await page.goto("/customers");
  const environment = page.getByRole("combobox", { name: "업무 환경", exact: true });
  await environment.focus();
  await page.keyboard.press("ArrowDown");
  await page.keyboard.press("Escape");
  expect(new URL(page.url()).searchParams.get("environment")).toBeNull();
  await environment.click();
  await page.locator(".ant-select-item-option", { hasText: "실제 환경" }).click();
  const confirm = page.getByRole("dialog");
  await expect(confirm).toContainText("실제 환경으로 전환할까요?");
  await confirm.getByRole("button", { name: "취소", exact: true }).click();
  expect(new URL(page.url()).searchParams.get("environment")).toBeNull();
  await environment.click();
  await page.locator(".ant-select-item-option", { hasText: "실제 환경" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "전환", exact: true }).click();
  await expect.poll(() => new URL(page.url()).searchParams.get("environment")).toBe("live");
  expect(nativeDialogs).toBe(0);
});

test("격리 계약: 공백을 낀 환경 값도 다듬어 비교해 다른 환경으로 보내지 않는다", async ({ page, request }) => {
  await page.goto("/integrations");
  await page.getByRole("button", { name: "API 키 발급", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel(/^키 이름/).fill("공백 환경");
  await dialog.locator('input[name="environment"]').evaluate(input => { (input as HTMLInputElement).value = " live "; });
  await dialog.getByRole("button", { name: "API 키 발급", exact: true }).click();
  await expect(dialog.getByRole("alert")).toContainText("열어 둔 화면의 업무 환경이 바뀌었습니다.");
  expect((await readState(request)).keyCalls).toHaveLength(0);
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
    page.once("dialog", confirm => void confirm.accept());
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


test("격리 계약: 조건 전체 CSV 내보내기는 보조 버튼이라 카드 폭으로 늘어나지 않는다", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("/customers");
  const button = page.getByRole("button", { name: "조건 전체 CSV 내보내기", exact: true });
  await expect(button).toBeVisible();
  const card = button.locator("xpath=ancestor::*[contains(@class, 'ant-card') or contains(@class, 'workspace-panel')][1]");
  const [buttonBox, cardBox] = await Promise.all([button.boundingBox(), card.boundingBox()]);
  expect(buttonBox && cardBox ? buttonBox.width < cardBox.width / 2 : false).toBe(true);
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

test("changing the month on the payments list keeps the customer carried in from other screens", async ({ page, request }) => {
  await control(request, "failed-payments");
  await page.goto("/payments?month=2026-10&customer_id=77777777-7777-4777-8777-777777777777");
  await page.getByLabel("청구 월", { exact: true }).fill("2026-09");
  await page.getByRole("button", { name: "조회", exact: true }).click();
  await expect(page).toHaveURL(url => url.searchParams.get("month") === "2026-09" && url.searchParams.get("customer_id") === "77777777-7777-4777-8777-777777777777");
});

test("격리 계약: 거꾸로 된 금액 범위는 요청하지 않고 입력칸 옆에 고칠 곳을 보여 준다", async ({ page }) => {
  await page.goto("/invoices");
  const form = page.locator('form[aria-label="조회 조건"]');
  await form.locator("summary").click();
  await form.getByLabel("최소 금액 (원)").fill("5000");
  await form.getByLabel("최대 금액 (원)").fill("100");
  await form.getByRole("button", { name: "조건 적용", exact: true }).click();
  await expect(form.getByRole("alert")).toHaveText("최대 금액은 최소 금액보다 작을 수 없습니다.");
  await expect(form.getByRole("spinbutton", { name: "최대 금액 (원)", exact: true })).toBeFocused();
  expect(new URL(page.url()).searchParams.get("min_amount")).toBeNull();
});

test("격리 계약: 고객 선택지는 입력을 멈춘 뒤 한 번만 찾고 빈 검색어로 열 때는 다시 받지 않는다", async ({ page, request }) => {
  await control(request, "customer-options");
  await page.goto("/invoices");
  const filter = page.getByRole("combobox", { name: "고객", exact: true });
  await expect(filter).toBeVisible();
  const loadedWithPage = (await readState(request)).customerOptionQueries.length;
  await filter.click();
  await filter.pressSequentially("검색고객", { delay: 30 });
  await expect(page.locator(".ant-select-item-option").filter({ hasText: "검색으로만 보이는 고객" })).toBeVisible();
  expect((await readState(request)).customerOptionQueries.slice(loadedWithPage)).toEqual(["검색고객"]);
});

test("격리 계약: 검색으로 고른 고객은 필터를 다시 열어도 이름으로 보인다", async ({ page, request }) => {
  await control(request, "customer-options");
  await page.goto("/invoices");
  const filter = page.getByRole("combobox", { name: "고객", exact: true });
  await filter.click();
  await filter.pressSequentially("검색고객");
  await page.locator(".ant-select-item-option").filter({ hasText: "검색으로만 보이는 고객" }).click();
  await page.locator('form[aria-label="조회 조건"]').getByRole("button", { name: "조건 적용", exact: true }).click();
  await expect.poll(() => new URL(page.url()).searchParams.get("customer_id")).toBe("77777777-7777-4777-8777-777777777777");
  await page.reload();
  await filter.click();
  await page.keyboard.press("Escape");
  const form = page.locator('form[aria-label="조회 조건"]');
  await expect(form.getByText("검색으로만 보이는 고객", { exact: true }).filter({ visible: true })).toHaveCount(1);
  await expect(form.getByText("77777777-7777-4777-8777-777777777777")).toHaveCount(0);
});

test("격리 계약: 고객 검색이 실패하면 조회 중에서 멈추지 않고 실패를 알린다", async ({ page, request }) => {
  await control(request, "customer-options");
  await page.goto("/invoices");
  const filter = page.getByRole("combobox", { name: "고객", exact: true });
  await filter.click();
  await filter.pressSequentially("실패");
  const empty = page.locator(".ant-select-dropdown .ant-select-item-empty");
  await expect(empty).toBeVisible();
  await expect(empty).not.toHaveText("조회 중");
  await expect(empty).not.toHaveText("일치하는 고객이 없습니다");
  await expect(page.locator(".ant-select-item-option")).toHaveCount(0);
});

test("격리 계약: 화면을 그릴 때 고객 선택지를 받지 못했으면 필터를 열 때 다시 받는다", async ({ page, request }) => {
  await control(request, "customer-options-retry");
  await page.goto("/invoices");
  await page.getByRole("combobox", { name: "고객", exact: true }).click();
  await expect(page.locator(".ant-select-item-option").filter({ hasText: "다시 받은 고객" })).toBeVisible();
});

test("격리 계약: 월 확정 결과 창은 다섯 건까지만 펼치고 나머지는 인보이스 화면으로 안내한다", async ({ page, request }) => {
  await control(request, "finalize-month");
  await page.goto("/billing?month=2026-09&environment=legacy");
  await page.getByRole("button", { name: "월 전체 확정", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "확정", exact: true }).click();
  const result = page.getByRole("dialog", { name: /인보이스 7건을 확정했습니다/ });
  await expect(result.getByRole("listitem")).toHaveCount(5);
  await expect(result.getByText("나머지 2건은 '인보이스 보기'에서 확인하세요.", { exact: true })).toBeVisible();
});

test("격리 계약: 청구할 고객이 없어 월 전체 확정이 비활성이면 그 이유를 버튼 설명으로 보인다", async ({ page, request }) => {
  await control(request, "finalized-month-zero-customer");
  await page.goto("/billing?month=2026-10&environment=legacy");
  const button = page.getByRole("button", { name: "월 전체 확정", exact: true });
  await expect(button).toBeDisabled();
  await expect(button).toHaveAccessibleDescription("청구할 금액이 있는 고객이 없거나 이미 모두 확정됐습니다.");
});

test("격리 계약: 청구 예정액을 불러오지 못하면 월 전체 확정이 비활성이고 그 이유를 버튼 설명으로 보인다", async ({ page, request }) => {
  await control(request, "draft-failure");
  await page.goto("/billing?month=2026-09&environment=legacy");
  const button = page.getByRole("button", { name: "월 전체 확정", exact: true });
  await expect(button).toBeDisabled();
  await expect(button).toHaveAccessibleDescription("청구 예정액을 불러오지 못해 확정할 수 없습니다.");
});
