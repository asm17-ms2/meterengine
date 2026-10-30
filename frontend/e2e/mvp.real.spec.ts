import { expect, test, type Page, type APIRequestContext } from "@playwright/test";
import { closeSync, fchmodSync, mkdirSync, openSync, writeFileSync } from "node:fs";

const API_URL = process.env.S1_API_URL ?? "http://127.0.0.1:38147";
const PG_URL = "http://127.0.0.1:38447";
const PASSWORD = "local-member-e2e-password";
const OPERATOR_EMAIL = process.env.E2E_OPERATOR_EMAIL ?? "operator@fe-e2e.local";
const OPERATOR_PASSWORD = process.env.E2E_OPERATOR_PASSWORD ?? "local-operator-e2e-password";
const kstMonth = (date: Date) => new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit" }).format(date);

async function login(page: Page, email: string, password = PASSWORD, returnTo = "") {
  await page.goto(`/login${returnTo ? `?returnTo=${encodeURIComponent(returnTo)}` : ""}`);
  await page.getByLabel("이메일", { exact: true }).fill(email);
  await page.getByLabel("비밀번호", { exact: true }).fill(password);
  await page.getByRole("button", { name: "로그인", exact: true }).click();
}

async function tokenOf(page: Page) {
  const token = (await page.context().cookies()).find(cookie => cookie.name === "me_session")?.value;
  if (!token) throw new Error("MVP 검증 세션이 없습니다");
  return token;
}

async function paymentState(request: APIRequestContext, key: string, invoiceId: string) {
  const response = await request.get(`${API_URL}/v1/invoice-payments`, { headers: { Authorization: `Bearer ${key}` } });
  expect(response.status()).toBe(200);
  return (await response.json()).invoice_payments.find((payment: { invoice_id: string }) => payment.invoice_id === invoiceId);
}

test("새 도입사의 수집, 청구, 카드 등록, 수납, 부분과 전액 취소가 실제 FE와 BE로 이어진다", async ({ page, browser, request }) => {
  const suffix = `${Date.now()}`;
  const providerBefore = await (await request.get(`${PG_URL}/__state`)).json();
  const email = `mvp-${suffix}@fe-e2e.local`;
  const organizationName = `MVP 도입사 ${suffix}`;
  const customerName = `MVP 고객 ${suffix}`;
  const metricCode = `mvp-units-${suffix}`;
  const month = kstMonth(new Date());
  await test.step("새 도입사 가입과 운영자 승인", async () => {
    await page.goto("/signup");
    await page.getByLabel("도입사 이름", { exact: true }).fill(organizationName);
    await page.getByLabel("담당자 이름", { exact: true }).fill("MVP 담당자");
    await page.getByLabel("이메일", { exact: true }).fill(email);
    await page.getByLabel("비밀번호", { exact: true }).fill(PASSWORD);
    await page.getByRole("checkbox").nth(0).check();
    await page.getByRole("checkbox").nth(1).check();
    await page.getByRole("button", { name: "가입 신청", exact: true }).click();
    await expect(page).toHaveURL(/\/pending$/);
    const operatorContext = await browser.newContext();
    const operator = await operatorContext.newPage();
    await login(operator, OPERATOR_EMAIL, OPERATOR_PASSWORD, "/admin/organizations");
    await expect(operator).toHaveURL(/\/admin\/organizations$/);
    const card = operator.getByRole("article").filter({ has: operator.getByRole("heading", { name: organizationName, exact: true }) });
    await card.getByRole("button", { name: "가입 승인", exact: true }).click();
    await operator.getByRole("button", { name: "가입 승인 확정", exact: true }).click();
    await expect(card).toHaveCount(0);
    await operatorContext.close();
    await login(page, email, PASSWORD, "/customers");
    await expect(page).toHaveURL(/\/customers(?:\?.*)?$/);
  });
  const memberToken = await tokenOf(page);
  const memberHeaders = { Authorization: `Bearer ${memberToken}` };
  let customerId = "";
  await test.step("고객 외부 ID와 과금 미터 등록", async () => {
    await page.getByRole("button", { name: "고객 등록", exact: true }).click();
    const customerDialog = page.getByRole("dialog");
    await customerDialog.getByLabel(/^고객명/).fill(customerName);
    await customerDialog.getByLabel(/외부 고객 ID/).fill(`external-${suffix}`);
    await customerDialog.getByLabel(/청구 이메일/).fill(`customer-${suffix}@fe-e2e.local`);
    await customerDialog.getByRole("button", { name: "고객 저장", exact: true }).click();
    await expect(customerDialog).toHaveCount(0);
    await page.getByRole("link", { name: customerName, exact: true }).click();
    await expect(page).toHaveURL(/\/customers\/[0-9a-f-]{36}(?:\?.*)?$/);
    customerId = new URL(page.url()).pathname.split("/").pop()!;
    await expect(page.getByText(`external-${suffix}`, { exact: true })).toBeVisible();
    await page.goto("/billable-metrics");
    await page.getByRole("button", { name: "미터 등록", exact: true }).first().click();
    const metricDialog = page.getByRole("dialog");
    await metricDialog.getByLabel("코드", { exact: true }).fill(metricCode);
    await metricDialog.getByLabel("이름", { exact: true }).fill("MVP 사용 단위");
    await metricDialog.getByLabel("이벤트 타입", { exact: true }).fill(metricCode);
    await metricDialog.getByLabel("집계 대상 속성", { exact: true }).fill("units");
    await metricDialog.getByRole("button", { name: "저장", exact: true }).click();
    await expect(metricDialog).toHaveCount(0);
  });
  await test.step("월 유효 단가와 최소 사용료, 기본료 설정", async () => {
    await page.goto(`/pricing?month=${month}`);
    await page.getByRole("button", { name: new RegExp(metricCode) }).click();
    await expect.poll(() => new URL(page.url()).searchParams.get("metric")).toBe(metricCode);
    await page.getByRole("button", { name: "단가 버전 등록", exact: true }).click();
    const rateDialog = page.getByRole("dialog");
    await rateDialog.getByLabel("사용량 1당 단가 (원)", { exact: true }).fill("100");
    await rateDialog.getByRole("button", { name: "이 단가 버전 저장", exact: true }).click();
    await expect(rateDialog).toHaveCount(0);
    await page.getByRole("button", { name: "요금제 버전 등록", exact: true }).click();
    const planDialog = page.getByRole("dialog");
    await planDialog.getByLabel(/^월 기본료 \(원\)/).fill("500");
    await planDialog.getByLabel(/월 최소 사용료 \(원\)/).fill("2500");
    await planDialog.getByRole("button", { name: "요금제 버전 등록", exact: true }).click();
    await expect(planDialog).toHaveCount(0);
  });
  await test.step("브라우저 공급자 정보 저장과 새 조회 확인", async () => {
    await page.goto("/settings");
    await page.getByLabel(/^사업자등록번호/).fill("1234567890");
    await page.getByLabel(/^대표자명/).fill("MVP 대표");
    await page.getByLabel(/^사업장 주소/).fill("서울 테스트 주소");
    await page.getByRole("button", { name: "공급자 정보 저장", exact: true }).click();
    await expect(page.getByText("처리했습니다.", { exact: true })).toBeVisible();
    const supplier = await request.get(`${API_URL}/v1/organization/supplier`, { headers: memberHeaders });
    expect(await supplier.json()).toMatchObject({ business_registration_number: "1234567890", representative_name: "MVP 대표", business_address: "서울 테스트 주소" });
    await page.reload();
    await expect(page.getByLabel(/^사업자등록번호/)).toHaveValue("1234567890");
    await expect(page.getByLabel(/^대표자명/)).toHaveValue("MVP 대표");
    await expect(page.getByLabel(/^사업장 주소/)).toHaveValue("서울 테스트 주소");
  });
  let apiKey = "";
  await test.step("연동 키 발급과 서명된 통보 수신 주소 등록", async () => {
    await page.goto("/integrations");
    await page.getByRole("button", { name: "API 키 발급", exact: true }).click();
    const keyDialog = page.getByRole("dialog");
    await keyDialog.getByLabel(/^키 이름/).fill("MVP 수집과 고객 조회");
    await keyDialog.getByLabel(/^기존 키 구분/).selectOption("test");
    await keyDialog.getByRole("checkbox", { name: /^조회 \(read\)/ }).check();
    await keyDialog.getByRole("checkbox", { name: /^수집 \(ingest\)/ }).check();
    await expect(keyDialog.getByRole("checkbox", { name: /^전체 관리 \(admin\)/ })).not.toBeChecked();
    await keyDialog.getByRole("button", { name: "API 키 발급", exact: true }).click();
    await expect(keyDialog.locator(".secret-result code")).toBeVisible();
    apiKey = (await keyDialog.locator(".secret-result code").textContent())!;
    expect(apiKey.startsWith("me_test_")).toBe(true);
    await page.context().grantPermissions(["clipboard-read", "clipboard-write"]);
    await keyDialog.getByRole("button", { name: "비밀값 복사", exact: true }).click();
    await expect(keyDialog.getByText("복사했습니다.", { exact: true })).toBeVisible();
    await page.keyboard.press("Escape");
    await page.getByRole("button", { name: "수신 주소 등록", exact: true }).click();
    const webhookDialog = page.getByRole("dialog");
    await webhookDialog.getByLabel(/^수신 URL/).fill(`${PG_URL}/meterengine-webhooks/${suffix}`);
    await webhookDialog.getByRole("button", { name: "수신 주소 등록", exact: true }).click();
    await expect(webhookDialog.locator(".secret-result code")).toBeVisible();
    const webhookSecret = await webhookDialog.locator(".secret-result code").textContent();
    await request.post(`${PG_URL}/__control`, { data: { webhookSecret, channel: suffix } });
    await webhookDialog.getByRole("button", { name: "비밀값 복사", exact: true }).click();
    await expect(webhookDialog.getByText("복사했습니다.", { exact: true })).toBeVisible();
    await page.keyboard.press("Escape");
  });
  const apiHeaders = { Authorization: `Bearer ${apiKey}` };
  await test.step("외부 고객 중복 등록과 배치 중복, 오류, 미래 이벤트 처리", async () => {
    const repeated = await request.post(`${API_URL}/v1/customers`, { headers: apiHeaders, data: { name: "덮어쓰면 안 되는 이름", external_id: `external-${suffix}` } });
    expect(repeated.status()).toBe(200);
    expect((await repeated.json()).id).toBe(customerId);
    const event = (index: number, units: number, timestamp = new Date(Date.now() - 60_000).toISOString()) => ({ transaction_id: `mvp-${suffix}-${index}`, customer_id: customerId, type: metricCode, properties: { units }, timestamp });
    const first = event(1, 12);
    const futureAt = new Date(Date.now() + 60 * 60 * 1000);
    const events = [first, event(2, 8), event(3, -2), event(4, 100, futureAt.toISOString()), event(5, 99, "bad-timestamp"), first];
    const response = await request.post(`${API_URL}/v1/event-batches`, { headers: apiHeaders, data: { events } });
    expect(response.status()).toBe(200);
    expect((await response.json()).results.map((row: { result: string }) => row.result)).toEqual(["stored", "stored", "stored", "stored", "rejected", "duplicate"]);
    const duplicate = await request.post(`${API_URL}/v1/event-batches`, { headers: apiHeaders, data: { events: events.slice(0, 4) } });
    expect((await duplicate.json()).results.every((row: { result: string }) => row.result === "duplicate")).toBe(true);
    const futureExclusions = await request.get(`${API_URL}/v1/unbilled-events?month=${kstMonth(futureAt)}`, { headers: apiHeaders });
    expect(await futureExclusions.json()).toMatchObject({ future_timestamp: { event_count: 1 } });
    const exclusions = await request.get(`${API_URL}/v1/unbilled-events?month=${month}`, { headers: apiHeaders });
    expect(await exclusions.json()).toMatchObject({ negative_quantity: { event_count: 1 } });
    const draft = await request.get(`${API_URL}/v1/invoices/draft?month=${month}`, { headers: apiHeaders });
    expect(draft.status()).toBe(200);
    const customer = (await draft.json()).customers.find((entry: { customer_id: string }) => entry.customer_id === customerId);
    expect(customer.amount).toBe(3000);
    expect(customer.lines.map((line: { amount: number }) => line.amount).sort((left: number, right: number) => left - right)).toEqual([500, 700, 1800]);
  });
  let invoiceId = "";
  await test.step("브라우저 청구 검토와 인보이스 확정", async () => {
    await page.goto(`/billing?month=${month}`);
    await expect(page.getByRole("button", { name: "월 전체 확정", exact: true })).toBeEnabled();
    await page.screenshot({ path: "/tmp/meterengine-mvp-billing.png", fullPage: true });
    await page.getByRole("button", { name: "월 전체 확정", exact: true }).click();
    await page.getByRole("dialog").getByRole("button", { name: "확정", exact: true }).click();
    await expect(page.getByRole("dialog")).toContainText("1건을 확정했습니다");
    const invoices = await request.get(`${API_URL}/v1/invoices?month=${month}&customer_id=${customerId}`, { headers: apiHeaders });
    const invoice = (await invoices.json()).invoices[0];
    expect(invoice).toMatchObject({ supply_amount: 3000, tax_amount: 300, total_amount: 3300 });
    invoiceId = invoice.id;
  });
  await test.step("토스 테스트 키 설정과 고객 카드 등록 동의", async () => {
    await page.goto("/settings");
    await page.getByRole("button", { name: "토스 키 저장", exact: true }).click();
    const dialog = page.getByRole("dialog");
    await dialog.getByLabel(/^클라이언트 키/).fill("test_ck_local_mvp_client");
    await dialog.getByLabel(/^시크릿 키/).fill("test_sk_local_mvp_secret");
    await dialog.getByRole("button", { name: "토스 키 저장", exact: true }).click();
    await expect(dialog).toHaveCount(0);
    await page.goto(`/customers/${customerId}?month=${month}`);
    await page.getByRole("button", { name: "카드 등록 링크 만들기", exact: true }).click();
    await page.getByRole("dialog").getByRole("button", { name: "카드 등록 링크 만들기", exact: true }).click();
    const registration = page.getByRole("dialog").getByRole("link", { name: "카드 등록 링크 열기", exact: true });
    await expect(registration).toBeVisible();
    const registrationPath = (await registration.getAttribute("href"))!;
    const customerContext = await browser.newContext();
    const customer = await customerContext.newPage();
    await customer.route("https://js.tosspayments.com/v2/standard", route => route.fulfill({ contentType: "application/javascript", body: `window.TossPayments = () => ({payment: ({customerKey}) => ({requestBillingAuth: async (options) => { const url = new URL(options.successUrl); url.searchParams.set("authKey", "mvp-auth-${suffix}"); url.searchParams.set("customerKey", customerKey); location.assign(url); }})});` }));
    await customer.goto(`http://localhost:33147${registrationPath}`);
    await expect(customer.getByRole("button", { name: "토스에서 카드 등록하기", exact: true })).toBeDisabled();
    await customer.getByRole("checkbox").check();
    await customer.getByRole("button", { name: "토스에서 카드 등록하기", exact: true }).click();
    await expect(customer.getByRole("heading", { name: "카드를 등록했습니다", exact: true })).toBeVisible();
    expect(customer.url()).not.toContain("authKey");
    await customer.screenshot({ path: "/tmp/meterengine-mvp-card-complete.png", fullPage: true });
    await customerContext.close();
    const card = await request.get(`${API_URL}/v1/customers/${customerId}/payment-method`, { headers: apiHeaders });
    expect((await card.json()).card_number).toBe("43301234****123*");
  });
  await test.step("관리자 결제, read 키 쓰기 거절과 중복 청구 방지", async () => {
    const forbidden = await request.post(`${API_URL}/v1/invoices/${invoiceId}/payment-attempts`, { headers: apiHeaders });
    expect(forbidden.status()).toBe(403);
    await page.goto(`/payments/${invoiceId}`);
    await page.getByRole("button", { name: "등록 카드로 결제", exact: true }).click();
    await page.getByRole("dialog").getByRole("button", { name: "등록 카드로 결제", exact: true }).click();
    await expect(page.getByRole("dialog")).toHaveCount(0);
    expect(await paymentState(request, apiKey, invoiceId)).toMatchObject({ status: "paid", amount: 3300, attempt_count: 1 });
    const duplicate = await request.post(`${API_URL}/v1/invoices/${invoiceId}/payment-attempts`, { headers: memberHeaders });
    expect(duplicate.status()).toBe(409);
    expect((await duplicate.json()).code).toBe("invoice_already_paid");
    const provider = await (await request.get(`${PG_URL}/__state`)).json();
    expect(provider.approval_calls - providerBefore.approval_calls).toBe(1);
  });
  await test.step("브라우저 부분 취소와 전액 취소, 확정 원장 보존", async () => {
    await request.post(`${PG_URL}/__control`, { data: { nextCancellationDelayMs: 6000 } });
    const attempts = await request.get(`${API_URL}/v1/invoices/${invoiceId}/payment-attempts`, { headers: apiHeaders });
    const attemptId = (await attempts.json()).payment_attempts[0].id;
    await page.getByRole("button", { name: "일부 금액 취소", exact: true }).click();
    let dialog = page.getByRole("dialog");
    const partialRequestId = await dialog.locator('input[name="request_id"]').inputValue();
    await dialog.getByLabel(/취소 금액/).fill("1000");
    await dialog.getByLabel(/^취소 사유/).fill("로컬 E2E 부분 취소");
    await dialog.getByRole("button", { name: "입력한 금액 취소 요청", exact: true }).click();
    await expect.poll(async () => {
      const response = await request.get(`${API_URL}/v1/payment-attempts/${attemptId}/cancellations`, { headers: apiHeaders });
      return (await response.json()).payment_cancellations[0]?.status;
    }, { intervals: [100, 200, 300], timeout: 4000 }).toBe("pending");
    const replayHeaders = { ...memberHeaders, "Idempotency-Key": partialRequestId };
    const repeatedPending = await request.post(`${API_URL}/v1/payment-attempts/${attemptId}/cancellations`, {
      headers: replayHeaders, data: { amount: 1000, reason: "로컬 E2E 부분 취소" },
    });
    expect(repeatedPending.status()).toBe(201);
    expect((await repeatedPending.json()).status).toBe("pending");
    const pendingObserver = await page.context().newPage();
    await pendingObserver.goto(`http://localhost:33147/payments/${invoiceId}`);
    await expect(pendingObserver.getByText("취소 결과 확인 중", { exact: true })).toBeVisible();
    await expect(pendingObserver.getByRole("button", { name: "일부 금액 취소", exact: true })).toHaveCount(0);
    await expect(pendingObserver.getByRole("button", { name: "등록 카드로 결제", exact: true })).toHaveCount(0);
    await pendingObserver.close();
    await expect(dialog).toHaveCount(0);
    expect(await paymentState(request, apiKey, invoiceId)).toMatchObject({ status: "partially_canceled", canceled_amount: 1000 });
    const repeatedDone = await request.post(`${API_URL}/v1/payment-attempts/${attemptId}/cancellations`, {
      headers: replayHeaders, data: { amount: 1000, reason: "로컬 E2E 부분 취소" },
    });
    expect(repeatedDone.status()).toBe(201);
    expect((await repeatedDone.json()).status).toBe("done");
    const cancellations = await request.get(`${API_URL}/v1/payment-attempts/${attemptId}/cancellations`, { headers: apiHeaders });
    expect((await cancellations.json()).payment_cancellations).toHaveLength(1);
    await page.getByRole("button", { name: "잔액 전액 취소", exact: true }).click();
    dialog = page.getByRole("dialog");
    expect(await dialog.locator('input[name="request_id"]').inputValue()).not.toBe(partialRequestId);
    await dialog.getByLabel(/^취소 사유/).fill("로컬 E2E 잔액 전부 취소");
    await dialog.getByRole("button", { name: /2,300원 전액 취소 요청/ }).click();
    await expect(dialog).toHaveCount(0);
    expect(await paymentState(request, apiKey, invoiceId)).toMatchObject({ status: "canceled", canceled_amount: 3300, attempt_count: 1 });
    const invoice = await request.get(`${API_URL}/v1/invoices/${invoiceId}`, { headers: apiHeaders });
    expect(await invoice.json()).toMatchObject({ supply_amount: 3000, tax_amount: 300, total_amount: 3300 });
    const duplicate = await request.post(`${API_URL}/v1/invoices/${invoiceId}/payment-attempts`, { headers: memberHeaders });
    expect(duplicate.status()).toBe(409);
    await page.goto(`/customers/${customerId}?month=${month}`);
    await expect(page.getByRole("cell", { name: "전액 취소", exact: true })).toBeVisible();
    await expect(page.getByText("데이터를 불러오지 못했습니다", { exact: true })).toHaveCount(0);
    const usagePanel = page.locator("section.workspace-panel").filter({ has: page.getByRole("heading", { name: "과금 대상 사용량", exact: true }) });
    await expect(usagePanel.getByRole("cell", { name: "18", exact: true })).toBeVisible();
    await page.screenshot({ path: "/tmp/meterengine-mvp-customer-after-cancel.png", fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    await expect(usagePanel.locator(".workspace-mobile-records").getByText("18", { exact: true })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.screenshot({ path: "/tmp/meterengine-mvp-customer-mobile.png", fullPage: true });
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto(`/overview?month=${month}`);
    await expect(page.getByText("데이터를 불러오지 못했습니다", { exact: true })).toHaveCount(0);
    await page.screenshot({ path: "/tmp/meterengine-mvp-overview-desktop.png", fullPage: true });
    await page.goto(`/payments/${invoiceId}`);
    await expect(page.getByRole("button", { name: "등록 카드로 결제", exact: true })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "일부 금액 취소", exact: true })).toHaveCount(0);
    await expect(page.getByText("전액 취소 완료", { exact: true })).toBeVisible();
    await page.screenshot({ path: "/tmp/meterengine-mvp-payments-desktop.png", fullPage: true });
  });
  await test.step("실제 예약 발송과 도입사 수신기의 서명 검증", async () => {
    await expect.poll(async () => {
      const provider = await (await request.get(`${PG_URL}/__state`)).json();
      return ["payment_succeeded", "payment_canceled", "unbilled_usage_detected"].every(type => provider.webhooks.some((event: { channel: string; type: string; is_valid: boolean }) => event.channel === suffix && event.type === type && event.is_valid));
    }, { timeout: 100_000, intervals: [1000, 3000, 5000], message: "결제 성공, 취소와 미청구 사용량 통보 수신(백엔드 UNBILLED_USAGE_REPORT_CRON을 짧은 주기로 두었는지 확인)" }).toBe(true);
    const provider = await (await request.get(`${PG_URL}/__state`)).json();
    const webhooks = provider.webhooks.filter((event: { channel: string }) => event.channel === suffix);
    expect(webhooks.every((event: { is_valid: boolean }) => event.is_valid)).toBe(true);
    expect(webhooks.filter((event: { type: string }) => event.type === "payment_succeeded")).toHaveLength(1);
    expect(webhooks.filter((event: { type: string }) => event.type === "payment_canceled")).toHaveLength(2);
    expect(provider.issuance_calls - providerBefore.issuance_calls).toBe(1);
    expect(provider.approval_calls - providerBefore.approval_calls).toBe(1);
    expect(provider.cancellation_calls - providerBefore.cancellation_calls).toBe(2);
  });
  await test.step("후속 시연 검증에 필요한 임시 인증 자료 보존", async () => {
    const response = await request.post(`${API_URL}/v1/api-keys`, { headers: memberHeaders, data: { label: "MVP 후속 시연 검증", mode: "test", scopes: ["admin"] } });
    expect(response.status()).toBe(201);
    const issued = await response.json();
    const session = await (await request.get(`${API_URL}/v1/sessions/current`, { headers: memberHeaders })).json();
    const directory = process.env.MVP_EVIDENCE_DIR ?? "/private/tmp/meterengine-orchestration-0696";
    mkdirSync(directory, { recursive: true, mode: 0o700 });
    const fd = openSync(`${directory}/demo-credentials.json`, "w", 0o600);
    try {
      fchmodSync(fd, 0o600);
      writeFileSync(fd, JSON.stringify({ apiKey: issued.secret, customerId, organizationId: session.organization.id, baseUrl: API_URL }));
    } finally { closeSync(fd); }
  });
});
