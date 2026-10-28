import { expect, test, type Page } from "@playwright/test";

const FIXTURE_URL = "http://127.0.0.1:38149";
const TOKEN = "a".repeat(43);
const REGISTRATION_PATH = `/payment-method-registrations/${TOKEN}`;

async function mockTossSdk(page: Page, outcome: "success" | "foreign-customer" | "canceled" = "success") {
  await page.route("https://js.tosspayments.com/v2/standard", route => route.fulfill({
    contentType: "application/javascript",
    body: `window.TossPayments = () => ({ payment: ({customerKey}) => ({ requestBillingAuth: async (options) => {
      const url = new URL(${JSON.stringify(outcome)} === "canceled" ? options.failUrl : options.successUrl);
      if (${JSON.stringify(outcome)} === "canceled") { url.searchParams.set("code", "PAY_PROCESS_CANCELED"); url.searchParams.set("message", "must-not-be-reflected"); }
      else { url.searchParams.set("authKey", "fixture-auth-key"); url.searchParams.set("customerKey", ${JSON.stringify(outcome)} === "foreign-customer" ? "wrong-customer" : customerKey); }
      window.location.assign(url.toString());
    }}) });`,
  }));
}

test.beforeEach(async ({ request }) => {
  await request.post(`${FIXTURE_URL}/__control`, { data: { reset: true } });
});

test("동의 뒤 토스 인증과 서버 발급을 거쳐 성공하며 새로고침에 재발급하지 않는다", async ({ page, request }) => {
  await mockTossSdk(page);
  await page.goto(REGISTRATION_PATH);
  await expect(page.getByRole("heading", { name: "결제에 사용할 카드를 등록해 주세요" })).toBeVisible();
  const button = page.getByRole("button", { name: "토스에서 카드 등록하기" });
  await expect(button).toBeDisabled();
  await page.getByRole("checkbox").check();
  await button.click();
  await expect(page).toHaveURL(`${REGISTRATION_PATH}/complete`);
  await expect(page.getByRole("heading", { name: "카드를 등록했습니다" })).toBeVisible();
  await expect(page.getByText("43301234****123*", { exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByRole("heading", { name: "카드를 등록했습니다" })).toBeVisible();
  const calls = await (await request.get(`${FIXTURE_URL}/__requests`)).json();
  expect(calls).toHaveLength(1);
  expect(calls[0].body).toEqual({ auth_key: "fixture-auth-key", customer_key: "9a85005e-e1f2-40cd-a6a8-cb3ee3d643a1", consent_document_version: "2026-10-28" });
  expect(calls[0].hasAuthorization).toBe(false);
  expect(await page.evaluate(() => localStorage.length + sessionStorage.length)).toBe(0);
  expect(await page.content()).not.toContain("fixture-auth-key");
  const cookies = await page.context().cookies();
  expect(cookies.every(cookie => cookie.httpOnly && cookie.sameSite === "Lax")).toBe(true);
  expect(JSON.stringify(cookies)).not.toContain("fixture-auth-key");
});

test("동의 요청 없이 돌아온 콜백은 발급하지 않고 민감한 쿼리를 지운다", async ({ page, request }) => {
  await page.goto(`${REGISTRATION_PATH}/success?authKey=fixture-auth-key&customerKey=wrong-customer&state=forged`);
  await expect(page).toHaveURL(`${REGISTRATION_PATH}/complete`);
  await expect(page.getByText("등록 요청을 확인하지 못했습니다.", { exact: false })).toBeVisible();
  expect(await (await request.get(`${FIXTURE_URL}/__requests`)).json()).toHaveLength(0);
});

test("다른 고객의 인증 결과는 서버에 발급 요청을 보내지 않는다", async ({ page, request }) => {
  await mockTossSdk(page, "foreign-customer");
  await page.goto(REGISTRATION_PATH);
  await page.getByRole("checkbox").check();
  await page.getByRole("button", { name: "토스에서 카드 등록하기" }).click();
  await expect(page).toHaveURL(`${REGISTRATION_PATH}/complete`);
  await expect(page.getByText("등록 요청을 확인하지 못했습니다.", { exact: false })).toBeVisible();
  expect(await (await request.get(`${FIXTURE_URL}/__requests`)).json()).toHaveLength(0);
});

test("카드 인증 취소는 오류 원문을 비추지 않고 다시 시작할 수 있다", async ({ page, request }) => {
  await mockTossSdk(page, "canceled");
  await page.goto(REGISTRATION_PATH);
  await page.getByRole("checkbox").check();
  await page.getByRole("button", { name: "토스에서 카드 등록하기" }).click();
  await expect(page.getByRole("heading", { name: "카드 등록을 취소했습니다" })).toBeVisible();
  expect(await page.content()).not.toContain("must-not-be-reflected");
  expect(await (await request.get(`${FIXTURE_URL}/__requests`)).json()).toHaveLength(0);
  await page.getByRole("link", { name: "카드 등록 다시 시작" }).click();
  await expect(page.getByRole("heading", { name: "결제에 사용할 카드를 등록해 주세요" })).toBeVisible();
});

test("발급 결과 미확인은 자동으로 재시도하지 않는다", async ({ page, request }) => {
  await request.post(`${FIXTURE_URL}/__control`, { data: { status: 503 } });
  await mockTossSdk(page);
  await page.goto(REGISTRATION_PATH);
  await page.getByRole("checkbox").check();
  await page.getByRole("button", { name: "토스에서 카드 등록하기" }).click();
  await expect(page.getByText("등록 결과를 확인하지 못했습니다.", { exact: false })).toBeVisible();
  await page.reload();
  expect(await (await request.get(`${FIXTURE_URL}/__requests`)).json()).toHaveLength(1);
});

test("만료 링크와 모바일 화면은 복구 안내를 제공한다", async ({ page, request }) => {
  await mockTossSdk(page);
  await page.goto(REGISTRATION_PATH);
  await page.screenshot({ path: "/tmp/meterengine-payment-registration-desktop.png", fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole("checkbox")).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: "/tmp/meterengine-payment-registration-mobile.png", fullPage: true });
  await request.post(`${FIXTURE_URL}/__control`, { data: { used: true } });
  await page.reload();
  await expect(page.getByRole("heading", { name: "사용할 수 없는 등록 링크입니다" })).toBeVisible();
  await expect(page.getByText("새 카드 등록 링크를 요청해 주세요.", { exact: false })).toBeVisible();
});
