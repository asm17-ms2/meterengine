import { test, expect, type Page } from "@playwright/test";

import { fixtureUrl, frontendOrigin } from "./contract-stack";

const FIXTURE_URL = fixtureUrl();
const FRONTEND_ORIGIN = frontendOrigin();
test.use({ baseURL: FRONTEND_ORIGIN });
const LOGIN_PASSWORD = "contract-password";
const ORGANIZATION_ID = "11111111-1111-4111-8111-111111111111";
const WEBHOOK_ENDPOINT_ID = "99999999-9999-4999-8999-999999999999";

async function expectTenantUrl(page: Page, pathname: string) {
  await expect(page).toHaveURL(url => url.origin === FRONTEND_ORIGIN && url.pathname === pathname && url.searchParams.size === 1 && url.searchParams.get("organization_id") === ORGANIZATION_ID);
}

async function login(page: Page, email = "member@contract.local", returnTo = "/customers") {
  await page.goto(`/login?returnTo=${encodeURIComponent(returnTo)}`);
  await page.getByLabel("이메일", { exact: true }).fill(email);
  await page.getByLabel("비밀번호", { exact: true }).fill(LOGIN_PASSWORD);
  await page.getByRole("button", { name: "로그인", exact: true }).click();
}

test.beforeEach(async ({ request }) => { await request.post(`${FIXTURE_URL}/__control`, { data: { reset: true } }); });

test("overview keeps the selected month and shows unresolved invoices from every month", async ({ page, request }) => {
  const invoiceId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
  await request.post(`${FIXTURE_URL}/__control`, { data: { unresolvedInvoicePayments: [
    { invoice_id: invoiceId, customer_id: "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", customer_name: "지난달 실패 고객", month: "2026-09", status: "failed", mode: "test", amount: 1100, canceled_amount: 0, attempt_count: 1, last_failure_code: "REJECT_CARD_PAYMENT", next_retry_at: null, completed_at: null },
  ] } });
  await login(page, "member@contract.local", "/overview");
  const currentMonth = new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit" }).format(new Date());
  await expect(page.getByLabel("청구 월", { exact: true })).toHaveValue(currentMonth);
  const unresolved = page.locator("section.workspace-panel").filter({ has: page.getByRole("heading", { name: "미해결 결제", exact: true }) });
  await expect(unresolved).toBeVisible();
  await expect(unresolved).toContainText("2026-09");
  const href = await unresolved.getByRole("link", { name: "지난달 실패 고객", exact: true }).getAttribute("href");
  const target = new URL(href!, FRONTEND_ORIGIN);
  expect(target.pathname).toBe(`/payments/${invoiceId}`);
  expect(target.searchParams.get("organization_id")).toBe(ORGANIZATION_ID);
  // 직접 쓴 월은 Enter 없이 조회를 눌러도 반영한다.
  await page.getByLabel("청구 월", { exact: true }).fill("2026-05");
  await page.getByRole("button", { name: "조회", exact: true }).click();
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("2026년 5월 개요");
  await expect(unresolved).toContainText("지난달 실패 고객");
  await expect(page.getByText("선택한 달에 확인할 항목이 없어요.", { exact: true })).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(unresolved.getByRole("link", { name: "지난달 실패 고객", exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  // Enter만 눌러 제출해도 직접 쓴 월로 조회한다.
  await page.getByLabel("청구 월", { exact: true }).fill("2026-04");
  await page.getByLabel("청구 월", { exact: true }).press("Enter");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("2026년 4월 개요");
});

test("the overview setup step says which supplier and payment settings are missing", async ({ page }) => {
  await login(page, "member@contract.local", "/overview");
  const step = page.locator(".setup-list li").filter({ has: page.getByText("공급자와 결제 설정", { exact: true }) });
  await expect(step.getByText("공급자 정보 미입력, 토스페이먼츠 테스트 키 미등록. 현재 결제 모드: 테스트 결제", { exact: true })).toBeVisible();
});

test("public health, gated routes, expired session and return URL", async ({ page, request }) => {
  expect((await request.get("/health")).status()).toBe(200);
  for (const path of ["/customers?q=abc", "/admin/users", "/organizations"]) {
    await page.goto(path);
    await expect(page).toHaveURL(/\/login\?returnTo=/);
  }
  await page.context().addCookies([{ name: "me_session", value: "expired-session", url: FRONTEND_ORIGIN }]);
  await page.goto("/customers");
  await expect(page).toHaveURL(/\/login\?reason=expired&returnTo=%2Fcustomers$/);
  await login(page, "member@contract.local", "/customers");
  await expectTenantUrl(page, "/customers");
  const calls = await (await request.get(`${FIXTURE_URL}/__requests`)).json();
  expect(calls.find((call: { path: string }) => call.path === "/v1/sessions").hasAuthorization).toBe(false);
  expect(calls.some((call: { hasOrganizationHeader: boolean }) => call.hasOrganizationHeader)).toBe(false);
});

test("signup sends displayed consent versions without stale Bearer or a password-bearing follow-up", async ({ page, request }) => {
  await page.context().addCookies([{ name: "me_session", value: "expired-session", url: FRONTEND_ORIGIN }]);
  await page.goto("/signup");
  await page.getByLabel("조직 이름", { exact: true }).fill("새 도입사");
  await page.getByLabel("담당자 이름", { exact: true }).fill("새 담당자");
  await page.getByLabel("이메일", { exact: true }).fill("new@contract.local");
  await page.getByLabel("비밀번호", { exact: true }).fill(LOGIN_PASSWORD);
  await page.getByRole("checkbox").nth(0).check();
  await page.getByRole("checkbox").nth(1).check();
  await page.getByRole("button", { name: "가입 신청", exact: true }).click();
  await expect(page).toHaveURL(/\/pending$/);
  await expect(page.getByRole("heading", { level: 1, name: "가입 신청을 접수했어요", exact: true })).toBeVisible();
  await expect(page.getByRole("complementary", { name: "가입 절차" }).locator('li[aria-current="step"]')).toContainText("가입 승인");
  await expect(page.getByText("운영자", { exact: false })).toHaveCount(0);
  await page.reload();
  expect(await page.evaluate(() => localStorage.length + sessionStorage.length)).toBe(0);
  expect(await page.content()).not.toContain(LOGIN_PASSWORD);
  const calls = await (await request.get(`${FIXTURE_URL}/__requests`)).json();
  expect(calls).toEqual([{ path: "/v1/signups", method: "POST", hasAuthorization: false, hasOrganizationHeader: false, clientIp: "127.0.0.1", consents: [{ document: "terms_of_service", document_version: "2026-11-11" }, { document: "privacy_policy", document_version: "2026-11-11" }] }]);
});

test("signup shows the captcha outage message when the backend answers after the default 5 second limit", async ({ page, request }) => {
  await request.post(`${FIXTURE_URL}/__control`, { data: { captchaDelayMs: 6_000 } });
  await page.goto("/signup");
  await page.getByLabel("조직 이름", { exact: true }).fill("새 도입사");
  await page.getByLabel("담당자 이름", { exact: true }).fill("새 담당자");
  await page.getByLabel("이메일", { exact: true }).fill("slow-captcha@contract.local");
  await page.getByLabel("비밀번호", { exact: true }).fill(LOGIN_PASSWORD);
  await page.getByRole("checkbox").nth(0).check();
  await page.getByRole("checkbox").nth(1).check();
  await page.getByRole("button", { name: "가입 신청", exact: true }).click();
  await expect(page.getByText("확인 서비스가 응답하지 않아 요청을 받지 못했어요. 잠시 뒤 다시 시도해 주세요.")).toBeVisible({ timeout: 15_000 });
  await expect(page).toHaveURL(/\/signup$/);
});

const VERIFICATION_LINK = `/email-verifications/me_verify_${"a".repeat(32)}`;

test("an unusable verification link offers login and a new mail as two separate choices", async ({ page }) => {
  await page.goto(VERIFICATION_LINK);
  await page.getByRole("button", { name: "이메일 인증", exact: true }).click();
  await expect(page.getByText("링크가 만료됐거나 이미 사용됐어요.", { exact: true })).toBeVisible();
  await expect(page.getByText("인증을 마쳤나요?")).toContainText("로그인");
  await expect(page.getByText("아직 인증하지 않았나요?")).toContainText("인증 메일 다시 받기");
  await expect(page.getByRole("link", { name: "로그인", exact: true })).toHaveAttribute("href", "/login");
  await expect(page.getByRole("link", { name: "인증 메일 다시 받기", exact: true })).toHaveAttribute("href", "/email-verifications");
});

test("a verification that failed on the server only asks to press the button again", async ({ page, request }) => {
  await request.post(`${FIXTURE_URL}/__control`, { data: { fail: { "/v1/email-verification-confirmations": 500 } } });
  await page.goto(VERIFICATION_LINK);
  await page.getByRole("button", { name: "이메일 인증", exact: true }).click();
  await expect(page.getByText("인증하지 못했어요. 잠시 뒤 이메일 인증 버튼을 다시 눌러 주세요.", { exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "로그인", exact: true })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "인증 메일 다시 받기", exact: true })).toHaveCount(0);
});

test("safe relative return targets and member admin denial", async ({ page }) => {
  for (const returnTo of ["https://example.com", "//example.com/customers", "//[", "/\\example.com", "/%2f%2fexample.com", "/admin/users", "/login", "javascript:alert(1)"]) {
    await login(page, "member@contract.local", returnTo);
    await expectTenantUrl(page, "/overview");
  }
  await page.goto("/admin/users");
  await expectTenantUrl(page, "/overview");
});

test("a saved legacy address on a server without legacy data signs in to the same screen in the test environment", async ({ page }) => {
  await page.goto(`/customers?environment=legacy&organization_id=${ORGANIZATION_ID}`);
  await expect(page).toHaveURL(url => url.pathname === "/login" && url.searchParams.get("returnTo") === `/customers?organization_id=${ORGANIZATION_ID}`);
  await login(page, "member@contract.local", `/customers?environment=legacy&organization_id=${ORGANIZATION_ID}`);
  await expectTenantUrl(page, "/customers");
  await expect(page.locator(".workspace-environment .ant-select")).toContainText("테스트 환경");
});

test("tenant actions recheck session after revocation, independently of route layout", async ({ page, request }) => {
  await login(page);
  await expectTenantUrl(page, "/customers");
  await page.getByRole("button", { name: "고객 등록", exact: true }).first().click();
  await page.getByRole("dialog").getByLabel(/^고객명/).fill("보내면 안 되는 고객");
  const cookie = (await page.context().cookies()).find(cookie => cookie.name === "me_session")!;
  await request.post(`${FIXTURE_URL}/__control`, { data: { revoke: cookie.value } });
  await page.getByRole("dialog").getByRole("button", { name: "고객 저장", exact: true }).click();
  await expect(page).toHaveURL(/\/login\?reason=expired(&returnTo=[^&]+)?$/);
  const calls = await (await request.get(`${FIXTURE_URL}/__requests`)).json();
  expect(calls.some((call: { path: string; method: string }) => call.path === "/v1/customers" && call.method === "POST")).toBe(false);
});

test("closing a workspace dialog returns focus to its trigger", async ({ page }) => {
  await login(page, "member@contract.local", "/members");
  const trigger = page.getByRole("button", { name: "초대 링크 만들기", exact: true });
  await trigger.focus();
  await page.keyboard.press("Enter");
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(trigger).toBeFocused();
  await page.keyboard.press("Enter");
  await page.getByRole("dialog").getByRole("button", { name: "닫기", exact: true }).last().click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(trigger).toBeFocused();
});

test("an issued secret is shown first and closing before copying asks for confirmation", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page, "member@contract.local", "/integrations");
  await page.getByRole("button", { name: "API 키 발급", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel(/^키 이름/).fill("계약 시험 키");
  await dialog.getByRole("button", { name: "API 키 발급", exact: true }).click();
  const result = dialog.locator(".secret-result");
  await expect(result).toBeFocused();
  await expect(result).toBeInViewport();
  let isAsked = false;
  page.once("dialog", async confirm => { isAsked = true; await confirm.dismiss(); });
  await page.keyboard.press("Escape");
  await expect.poll(() => isAsked).toBe(true);
  await expect(dialog).toBeVisible();
  await page.context().grantPermissions(["clipboard-read", "clipboard-write"]);
  await dialog.getByRole("button", { name: "비밀값 복사", exact: true }).click();
  await expect(dialog.getByRole("status")).toHaveText("복사했어요.");
  await page.keyboard.press("Escape");
  await expect(dialog).toHaveCount(0);
});

test("a server action after the session cookie disappears goes to login instead of the error screen", async ({ page, request }) => {
  await login(page);
  await expectTenantUrl(page, "/customers");
  await page.getByRole("button", { name: "고객 등록", exact: true }).first().click();
  await page.getByRole("dialog").getByLabel(/^고객명/).fill("보내면 안 되는 고객");
  await page.context().clearCookies();
  await page.getByRole("dialog").getByRole("button", { name: /^(?:고객 )?저장$/, exact: true }).click();
  await expect(page).toHaveURL(/\/login\?reason=expired(&returnTo=[^&]+)?$/);
  await expect(page.getByText("화면을 표시하지 못했어요")).toHaveCount(0);
  const calls = await (await request.get(`${FIXTURE_URL}/__requests`)).json();
  expect(calls.some((call: { path: string; method: string }) => call.path === "/v1/customers" && call.method === "POST")).toBe(false);
});

test("a workspace action that throws keeps the dialog and its input", async ({ page, request }) => {
  await login(page, "member@contract.local", "/integrations");
  await page.getByRole("button", { name: "API 키 발급", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel(/^키 이름/).fill("장애 중 발급");
  await request.post(`${FIXTURE_URL}/__control`, { data: { outage: true } });
  await dialog.getByRole("button", { name: "API 키 발급", exact: true }).click();
  await expect(dialog.getByText("요청 결과를 확인하지 못했어요", { exact: false })).toBeVisible();
  await expect(dialog.getByLabel(/^키 이름/)).toHaveValue("장애 중 발급");
  await expect(page.getByText("화면을 표시하지 못했어요")).toHaveCount(0);
});

test("a workspace action still follows the login redirect after its session is revoked", async ({ page, request }) => {
  await login(page, "member@contract.local", "/integrations");
  await page.getByRole("button", { name: "API 키 발급", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel(/^키 이름/).fill("폐기 뒤 발급");
  const cookie = (await page.context().cookies()).find(cookie => cookie.name === "me_session")!;
  await request.post(`${FIXTURE_URL}/__control`, { data: { revoke: cookie.value } });
  await dialog.getByRole("button", { name: "API 키 발급", exact: true }).click();
  await expect(page).toHaveURL(/\/login\?reason=expired(&returnTo=[^&]+)?$/);
});

async function openNewCustomer(page: Page, name: string) {
  await login(page);
  await page.getByRole("button", { name: "고객 등록", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel(/^고객명/).fill(name);
  await dialog.getByRole("button", { name: "고객 저장", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await page.getByRole("link", { name, exact: true }).click();
  await expect(page.getByRole("heading", { name, level: 1 })).toBeVisible();
}

test("archiving a customer shows its success message and opens the archived list for the viewed month", async ({ page }) => {
  await openNewCustomer(page, "보관할 고객");
  const month = new URL(page.url()).searchParams.get("month");
  await page.getByRole("button", { name: "고객 보관", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "고객 보관", exact: true }).click();
  await expect(page.getByText("고객을 보관했어요. 보관 목록에서 확인하세요.", { exact: true })).toBeVisible();
  await expect(page).toHaveURL(url => url.pathname === "/customers" && url.searchParams.get("status") === "archived" && url.searchParams.get("month") === month);
});

test("the customer archive button sits with the other customer actions, not in the navigation link row", async ({ page }) => {
  await openNewCustomer(page, "배치를 볼 고객");
  const actionRow = page.locator(".workspace-actions").filter({ has: page.getByRole("button", { name: "고객 정보 수정", exact: true }) });
  await expect(actionRow.getByRole("button", { name: "고객 보관", exact: true })).toBeVisible();
  const navigationRow = page.locator(".workspace-actions").filter({ has: page.getByRole("link", { name: "수집 로그 확인", exact: true }) });
  await expect(navigationRow.getByRole("button", { name: "고객 보관", exact: true })).toHaveCount(0);
});

test("a customer edit that lost a version race offers to load the latest values and then saves", async ({ page, request }) => {
  await login(page);
  await page.getByRole("button", { name: "고객 등록", exact: true }).click();
  let dialog = page.getByRole("dialog");
  await dialog.getByLabel(/^고객명/).fill("처음 이름");
  await dialog.getByRole("button", { name: "고객 저장", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await page.getByRole("link", { name: "처음 이름", exact: true }).click();
  await page.getByRole("button", { name: "고객 정보 수정", exact: true }).click();
  dialog = page.getByRole("dialog");
  await dialog.getByLabel(/^고객명/).fill("내가 고친 이름");
  await request.post(`${FIXTURE_URL}/__control`, { data: { renameCustomersElsewhere: "다른 사람이 고친 이름" } });
  await dialog.getByRole("button", { name: "저장", exact: true }).click();
  await expect(dialog.getByText("다른 사람이 먼저 고객 정보를 바꿨어요.", { exact: false })).toBeVisible();
  await dialog.getByRole("button", { name: "최신 정보 불러오기", exact: true }).click();
  await expect(dialog.getByLabel(/^고객명/)).toHaveValue("다른 사람이 고친 이름");
  await expect(dialog.getByText("다른 사람이 먼저 고객 정보를 바꿨어요.", { exact: false })).toHaveCount(0);
  await dialog.getByLabel(/^고객명/).fill("다시 고친 이름");
  await dialog.getByRole("button", { name: "저장", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(page.getByRole("heading", { name: "다시 고친 이름", level: 1 })).toBeVisible();
});

test("restoring a customer returns focus to the same button, now offering to archive", async ({ page, request }) => {
  await login(page);
  await page.getByRole("button", { name: "고객 등록", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel(/^고객명/).fill("복원할 고객");
  await dialog.getByRole("button", { name: "고객 저장", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await request.post(`${FIXTURE_URL}/__control`, { data: { archiveCustomers: true } });
  await page.getByRole("link", { name: "복원할 고객", exact: true }).click();
  await page.getByRole("button", { name: "고객 복원", exact: true }).click();
  await dialog.getByRole("button", { name: "고객 복원", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(page.getByRole("button", { name: "고객 보관", exact: true })).toBeFocused();
});

test("a customer address that is not a customer id is treated as not found without asking the server", async ({ page, request }) => {
  await login(page);
  await expectTenantUrl(page, "/customers");
  await page.goto(`/customers/not-a-uuid?organization_id=${ORGANIZATION_ID}`);
  await expect(page.getByRole("heading", { name: "고객을 찾을 수 없어요", level: 1 })).toBeVisible();
  await expect(page.getByRole("button", { name: "다시 시도", exact: true })).toHaveCount(0);
  const paths = (await (await request.get(`${FIXTURE_URL}/__requests`)).json()).map((call: { path: string }) => call.path);
  expect(paths.some((path: string) => path.includes("not-a-uuid"))).toBe(false);
});

test("a payment address that is not an invoice id is treated as not found without asking the server", async ({ page, request }) => {
  await login(page);
  await expectTenantUrl(page, "/customers");
  await page.goto(`/payments/not-a-uuid?organization_id=${ORGANIZATION_ID}`);
  await expect(page.getByRole("heading", { name: "수납 내역을 찾을 수 없어요", level: 1 })).toBeVisible();
  await expect(page.getByRole("button", { name: "다시 시도", exact: true })).toHaveCount(0);
  const paths = (await (await request.get(`${FIXTURE_URL}/__requests`)).json()).map((call: { path: string }) => call.path);
  expect(paths.some((path: string) => path.includes("not-a-uuid"))).toBe(false);
});

test("a missing customer says it was not found and links back to the customer list without a retry", async ({ page }) => {
  await login(page, "member@contract.local", "/customers/cccccccc-cccc-4ccc-8ccc-cccccccccccc");
  await expect(page.getByRole("heading", { name: "고객을 찾을 수 없어요", level: 1 })).toBeVisible();
  await expect(page.getByText("다른 조직이나 다른 업무 환경의 고객일 수 있어요", { exact: false })).toBeVisible();
  await expect(page.getByRole("link", { name: "고객 목록으로", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "다시 시도", exact: true })).toHaveCount(0);
});

test("the sidebar lists page links in plain lists without menu roles", async ({ page }) => {
  await login(page);
  await expectTenantUrl(page, "/customers");
  const nav = page.getByRole("navigation", { name: "주 메뉴" });
  await expect(nav.getByRole("menu")).toHaveCount(0);
  await expect(nav.getByRole("menuitem")).toHaveCount(0);
  await expect(nav.getByRole("list")).toHaveCount(4);
  await expect(nav.getByRole("list", { name: "과금과 연동", exact: true }).getByRole("link")).toHaveCount(4);
});

test("the sidebar lists the meter screen and marks it as the current place", async ({ page }) => {
  await login(page);
  await expectTenantUrl(page, "/customers");
  const nav = page.getByRole("navigation", { name: "주 메뉴" });
  await nav.getByRole("link", { name: "미터", exact: true }).click();
  await expectTenantUrl(page, "/billable-metrics");
  await expect(nav.getByRole("link", { name: "미터", exact: true })).toHaveAttribute("aria-current", "page");
  await expect(nav.locator("[aria-current=page]")).toHaveCount(1);
});

test("each screen has its own document title", async ({ page }) => {
  await page.goto("/login");
  await expect(page).toHaveTitle("로그인 | MeterEngine");
  await page.goto("/signup");
  await expect(page).toHaveTitle("가입 신청 | MeterEngine");
  await login(page);
  await expectTenantUrl(page, "/customers");
  await expect(page).toHaveTitle("고객 | MeterEngine");
  await page.getByRole("navigation", { name: "주 메뉴" }).getByRole("link", { name: "미터", exact: true }).click();
  await expect(page).toHaveTitle("미터 | MeterEngine");
});

test("a workspace load failure offers an in-place retry button", async ({ page, request }) => {
  await login(page, "member@contract.local", "/members");
  await expectTenantUrl(page, "/members");
  await request.post(`${FIXTURE_URL}/__control`, { data: { fail: { "/v1/organization/members": 500 } } });
  await page.reload();
  await expect(page.getByText("데이터를 불러오지 못했어요").first()).toBeVisible();
  await expect(page.getByRole("button", { name: "다시 시도", exact: true }).first()).toBeVisible();
  await expect(page.getByRole("link", { name: "다시 시도", exact: true })).toHaveCount(0);
});

test("a forbidden workspace load says so without a retry", async ({ page, request }) => {
  await login(page, "member@contract.local", "/integrations");
  await expectTenantUrl(page, "/integrations");
  await request.post(`${FIXTURE_URL}/__control`, { data: { fail: { "/v1/api-keys": 403 } } });
  await page.reload();
  await expect(page.getByText("조회 권한이 없어요").first()).toBeVisible();
  await expect(page.getByText("데이터를 불러오지 못했어요")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "다시 시도", exact: true })).toHaveCount(0);
});

test("webhook delivery history names event types, delivery states and failure reasons", async ({ page }) => {
  await login(page, "member@contract.local", `/integrations/${WEBHOOK_ENDPOINT_ID}`);
  const table = page.locator(".workspace-desktop-table");
  for (const label of ["결제 실패", "시험 통보", "재시도 대기", "발송 실패", "연결 실패", "허용되지 않는 주소"]) await expect(table.getByText(label, { exact: true })).toBeVisible();
  for (const raw of ["payment_failed", "webhook_test", "connection_error", "blocked_address", "결과 확인 중"]) await expect(table.getByText(raw, { exact: true })).toHaveCount(0);
});

test("saving a webhook endpoint keeps its existing description", async ({ page, request }) => {
  await login(page, "member@contract.local", `/integrations/${WEBHOOK_ENDPOINT_ID}`);
  await page.getByRole("button", { name: "수신 주소와 통보 수정", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "수신 주소와 통보 수정", exact: true }).click();
  await expect.poll(async () => {
    const calls = await (await request.get(`${FIXTURE_URL}/__requests`)).json();
    const update = calls.find((call: { path: string; method: string }) => call.method === "PUT" && call.path === `/v1/webhook-endpoints/${WEBHOOK_ENDPOINT_ID}`);
    return update ? update.description ?? "설명 없이 보냄" : "보내지 않음";
  }).toBe("운영 알림 수신기");
});

test("the audit list marks rejected requests and keeps entries compact", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await login(page, "member@contract.local", "/audit");
  await expect(page.locator(".audit-entry")).toHaveCount(12);
  const tops = await page.locator(".audit-entry").evaluateAll(entries => entries.map(entry => entry.getBoundingClientRect().top));
  expect(Math.max(...tops.slice(1).map((top, index) => top - tops[index]))).toBeLessThanOrEqual(80);
  await expect(page.locator(".audit-summary .ant-tag", { hasText: "요청 거절/오류 (403)" })).toHaveCount(1);
});

test("a meter form with input asks before Escape throws it away", async ({ page }) => {
  await login(page, "member@contract.local", "/billable-metrics");
  await page.getByRole("button", { name: "미터 등록", exact: true }).first().click();
  const dialog = page.getByRole("dialog", { name: "미터 등록" });
  await dialog.getByLabel("이름", { exact: true }).fill("작성 중인 미터");
  let asked = "";
  page.once("dialog", prompt => { asked = prompt.message(); void prompt.dismiss(); });
  await page.keyboard.press("Escape");
  await expect.poll(() => asked).toBe("작성 중인 미터를 버리고 닫을까요?");
  await expect(dialog).toBeVisible();
});

test("meter number inputs do not expose English stepper buttons", async ({ page }) => {
  await login(page, "member@contract.local", "/billable-metrics");
  await page.getByRole("button", { name: "미터 등록", exact: true }).first().click();
  const dialog = page.getByRole("dialog", { name: "미터 등록" });
  await expect(dialog.getByLabel("과금 단위 크기")).toBeVisible();
  await expect(dialog.getByRole("button", { name: /Increase Value|Decrease Value/ })).toHaveCount(0);
});

test("list screens keep the disclosure marker on the advanced filter summary", async ({ page }) => {
  await login(page, "member@contract.local", "/customers");
  await expectTenantUrl(page, "/customers");
  const summary = page.locator(".resource-advanced > summary");
  await expect(summary).toHaveCSS("display", "list-item");
  const box = await summary.boundingBox();
  expect(box?.height ?? 0).toBeGreaterThanOrEqual(44);
});

test("filter row controls on a list screen share one height", async ({ page }) => {
  await login(page, "member@contract.local", "/usage");
  await expectTenantUrl(page, "/usage");
  const form = page.locator('form[aria-label="조회 조건"]');
  await expect(form.locator(".ant-picker").first()).toBeVisible();
  const heights = await form.evaluate(element => Array.from(element.querySelectorAll(":scope > .workspace-field .ant-picker, :scope > .workspace-field .ant-select, :scope > .workspace-field > select, :scope > .workspace-field > input:not([type=hidden]), :scope > button[type=submit]"))
    .map(control => ({ name: control.className || control.tagName, height: Math.round(control.getBoundingClientRect().height) })));
  expect(heights.length).toBeGreaterThanOrEqual(3);
  for (const control of heights) expect(control.height, control.name).toBe(44);
});

test("the events customer filter matches the 44px filter row at phone and desktop widths", async ({ page }) => {
  await login(page, "member@contract.local", "/events");
  for (const width of [390, 1280]) {
    await page.setViewportSize({ width, height: 900 });
    const customer = page.locator(".event-filter-grid .ant-select").first();
    await expect(customer).toBeVisible();
    expect(Math.round((await customer.boundingBox())!.height), `${width}px`).toBe(44);
  }
});

test("an audit range whose end is not after its start is reported next to the end field without asking the server", async ({ page, request }) => {
  await login(page, "member@contract.local", "/audit?from=2026-10-10T00:00&to=2026-10-01T00:00");
  await expect(page.getByText("종료 일시는 시작 일시보다 뒤여야 해요.", { exact: true })).toBeVisible();
  await expect(page.getByLabel("종료 일시 (KST, 미포함)")).toHaveAttribute("aria-invalid", "true");
  const paths = (await (await request.get(`${FIXTURE_URL}/__requests`)).json()).map((call: { path: string }) => call.path);
  expect(paths.some((path: string) => path.startsWith("/v1/audit-logs") && path.includes("from="))).toBe(false);
});

test("the audit screen sends the server only a whole page number up to the safe limit", async ({ page, request }) => {
  const auditPages = async () => (await (await request.get(`${FIXTURE_URL}/__requests`)).json()).map((call: { path: string }) => call.path).filter((path: string) => path.startsWith("/v1/audit-logs")).map((path: string) => new URL(path, FIXTURE_URL).searchParams.get("page"));
  await login(page, "member@contract.local", "/audit?page=2147483647");
  await expect(page.locator(".audit-list")).toBeVisible();
  expect(new Set(await auditPages())).toEqual(new Set(["100000"]));
  for (const raw of ["-1", "abc", "1.5"]) {
    await page.goto(`/audit?page=${encodeURIComponent(raw)}`);
    await expect(page.locator(".audit-list")).toBeVisible();
  }
  expect(new Set(await auditPages())).toEqual(new Set(["100000", null]));
});

test("list filters submit with a primary 조건 적용 button and reset with 조건 초기화", async ({ page }) => {
  await login(page, "member@contract.local", "/customers");
  await expectTenantUrl(page, "/customers");
  const submit = page.locator('form[aria-label="조회 조건"]').getByRole("button", { name: "조건 적용", exact: true });
  await expect(submit).toBeVisible();
  await expect(submit).toHaveClass(/ant-btn-primary/);
  await page.context().clearCookies();
  await login(page, "member@contract.local", "/audit");
  await expect(page.getByRole("button", { name: "조건 적용", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "조건 초기화", exact: true })).toBeVisible();
});

test("the customer search names every searched field and fits its hint on a phone", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page, "member@contract.local", "/customers");
  await expectTenantUrl(page, "/customers");
  const search = page.getByRole("searchbox", { name: "이름, 외부 ID, 청구 이메일로 찾기", exact: true });
  await expect(search).toBeVisible();
  const fits = await search.evaluate(input => {
    const element = input as HTMLInputElement;
    const style = getComputedStyle(element);
    const context = document.createElement("canvas").getContext("2d")!;
    context.font = `${style.fontSize} ${style.fontFamily}`;
    return context.measureText(element.placeholder).width <= element.clientWidth - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight);
  });
  expect(fits).toBe(true);
});

test("phones do not show expand-all controls that only act on the desktop table", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page, "member@contract.local", "/usage");
  await expectTenantUrl(page, "/usage");
  await expect(page.locator(".data-mobile-records, .ant-empty, [role=status]").first()).toBeVisible();
  await expect(page.getByRole("button", { name: "모두 펼치기", exact: true })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "모두 접기", exact: true })).toHaveCount(0);
});

test("the month picker panel stays inside a phone screen", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page, "member@contract.local", "/usage");
  await expectTenantUrl(page, "/usage");
  await page.locator('form[aria-label="조회 조건"] .ant-picker').first().click();
  const panel = page.locator(".ant-picker-dropdown:visible .ant-picker-panel").first();
  await expect(panel).toBeVisible();
  const box = await panel.boundingBox();
  expect(box?.x ?? -1).toBeGreaterThanOrEqual(0);
  expect((box?.x ?? 0) + (box?.width ?? 0)).toBeLessThanOrEqual(390);
});

test("a webhook receiver whose latest delivery is failing is flagged in the list", async ({ page }) => {
  await login(page, "member@contract.local", "/integrations");
  await expectTenantUrl(page, "/integrations");
  const failing = page.getByRole("listitem").filter({ hasText: "https://hooks.contract.local/meterengine" });
  const healthy = page.getByRole("listitem").filter({ hasText: "https://hooks.contract.local/healthy" });
  await expect(failing).toContainText("최근 발송 실패");
  await expect(healthy).toContainText("발송 중");
  await expect(healthy).not.toContainText("최근 발송 실패");
});

test("the integrations list reads the failure flag from the receiver list without asking each receiver's deliveries", async ({ page, request }) => {
  await login(page, "member@contract.local", "/integrations");
  await expectTenantUrl(page, "/integrations");
  await expect(page.getByRole("listitem").filter({ hasText: "https://hooks.contract.local/meterengine" })).toContainText("최근 발송 실패");
  const paths = (await (await request.get(`${FIXTURE_URL}/__requests`)).json()).map((call: { path: string }) => call.path);
  expect(paths.filter((path: string) => path.includes("/deliveries"))).toEqual([]);
});

test("a workspace table scroll area is a named keyboard stop", async ({ page }) => {
  await login(page, "member@contract.local", "/integrations");
  await expectTenantUrl(page, "/integrations");
  await expect(page.getByRole("region", { name: "API 키 목록" })).toHaveAttribute("tabindex", "0");
});

test("copy results are announced through a status region that is already present", async ({ page }) => {
  await login(page, "member@contract.local", "/integrations");
  await expectTenantUrl(page, "/integrations");
  await page.getByRole("button", { name: "API 키 발급", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel(/^키 이름/).fill("상태 영역 키");
  await dialog.getByRole("button", { name: "API 키 발급", exact: true }).click();
  await expect(dialog.locator(".secret-result")).toBeVisible();
  await expect(dialog.getByRole("status")).toHaveCount(1);
  await page.context().grantPermissions(["clipboard-read", "clipboard-write"]);
  await dialog.getByRole("button", { name: "비밀값 복사", exact: true }).click();
  await expect(dialog.getByRole("status")).toHaveText("복사했어요.");
});

test("the operator user list sends the active organizations once instead of once per user", async ({ page, request }) => {
  await login(page, "operator@contract.local", "/admin/users");
  await expect(page).toHaveURL(/\/admin\/users$/);
  await request.post(`${FIXTURE_URL}/__control`, { data: { bulk: true } });
  const payload = await (await page.request.get("/admin/users", { headers: { RSC: "1" } })).text();
  expect(payload).toContain("66666666-6666-4666-8666-000000000000");
  const perUserReferences = payload.split(":props:organizations:").length - 1;
  expect(perUserReferences).toBeLessThan(100);
});

test("the operator's own card keeps both actions on the same top edge when only one shows a disabled reason", async ({ page, request }) => {
  await login(page, "operator@contract.local", "/admin/users");
  await expect(page).toHaveURL(/\/admin\/users$/);
  await request.post(`${FIXTURE_URL}/__control`, { data: { bulk: true } });
  await page.goto("/admin/users");
  const card = page.getByRole("article").filter({ hasText: "operator@contract.local" });
  const attach = card.getByRole("button", { name: "조직 소속 연결", exact: true });
  const remove = card.getByRole("button", { name: "플랫폼 운영 권한 제거", exact: true });
  await expect(attach).toBeEnabled();
  await expect(remove).toBeDisabled();
  const attachBox = (await attach.boundingBox())!;
  const removeBox = (await remove.boundingBox())!;
  expect(Math.abs(attachBox.y - removeBox.y)).toBeLessThanOrEqual(0.5);
});

test("accepting an invitation as a new account does not send a stale session", async ({ page, request }) => {
  await page.context().addCookies([{ name: "me_session", value: "stale-session", url: FRONTEND_ORIGIN }]);
  await page.goto("/organization-invitations/me_invite_ContractInvitation00000000000001");
  await page.getByLabel(/^이름/).fill("초대 받은 사용자");
  await page.getByLabel(/^비밀번호/).fill("invited-password");
  for (const box of await page.getByRole("checkbox").all()) await box.check();
  await page.getByRole("button", { name: "초대 수락하고 시작", exact: true }).click();
  await expectTenantUrl(page, "/overview");
  const calls = await (await request.get(`${FIXTURE_URL}/__requests`)).json();
  const accept = calls.find((call: { path: string; method: string }) => call.path.endsWith("/accept") && call.method === "POST");
  expect(accept.hasAuthorization).toBe(false);
});

test("the automatic login after accepting an invitation sends the rightmost forwarded address, not one the visitor set", async ({ page, request }) => {
  await page.setExtraHTTPHeaders({ "x-forwarded-for": "203.0.113.9, 198.51.100.77", "x-meterengine-client-ip": "203.0.113.10" });
  await page.goto("/organization-invitations/me_invite_ContractInvitation00000000000001");
  await page.getByLabel(/^이름/).fill("초대 받은 사용자");
  await page.getByLabel(/^비밀번호/).fill("invited-password");
  for (const box of await page.getByRole("checkbox").all()) await box.check();
  await page.getByRole("button", { name: "초대 수락하고 시작", exact: true }).click();
  await expectTenantUrl(page, "/overview");
  const calls = await (await request.get(`${FIXTURE_URL}/__requests`)).json();
  const login = calls.findLast((call: { path: string; method: string }) => call.path === "/v1/sessions" && call.method === "POST");
  expect(login.clientIp).toBe("198.51.100.77");
});

test("a login sends the rightmost forwarded address, not one the visitor set", async ({ page, request }) => {
  await page.setExtraHTTPHeaders({ "x-forwarded-for": "203.0.113.9, 198.51.100.78", "x-meterengine-client-ip": "203.0.113.10" });
  await login(page);
  await expectTenantUrl(page, "/customers");
  const calls = await (await request.get(`${FIXTURE_URL}/__requests`)).json();
  const session = calls.findLast((call: { path: string; method: string }) => call.path === "/v1/sessions" && call.method === "POST");
  expect(session.clientIp).toBe("198.51.100.78");
});

test("a client address the visitor set is dropped when no forwarded address can be trusted", async ({ page, request }) => {
  await page.setExtraHTTPHeaders({ "x-forwarded-for": "203.0.113.9, unknown", "x-meterengine-client-ip": "203.0.113.10" });
  await login(page);
  await expectTenantUrl(page, "/customers");
  const calls = await (await request.get(`${FIXTURE_URL}/__requests`)).json();
  const session = calls.findLast((call: { path: string; method: string }) => call.path === "/v1/sessions" && call.method === "POST");
  expect(session.clientIp).toBeUndefined();
});

test("every page refuses framing and MIME sniffing without naming the framework, and invoice links keep their own policy", async ({ request }) => {
  for (const path of ["/login", "/signup", "/organization-invitations/me_invite_ContractInvitation00000000000001"]) {
    const response = await request.get(path);
    expect(response.headers()["x-frame-options"], path).toBe("DENY");
    expect(response.headers()["content-security-policy"], path).toContain("frame-ancestors 'none'");
    expect(response.headers()["x-content-type-options"], path).toBe("nosniff");
    expect(response.headers()["x-powered-by"], path).toBeUndefined();
  }
  const invoiceLink = await request.get("/invoice-links");
  expect(invoiceLink.headers()["content-security-policy"]).toContain("default-src 'self'");
  expect(invoiceLink.headers()["x-content-type-options"]).toBe("nosniff");
});

test("logging in to a return path of another member organization opens that organization", async ({ page }) => {
  const other = "22222222-2222-4222-8222-222222222222";
  await login(page, "member@contract.local", `/customers?organization_id=${other}&q=abc`);
  await expect(page).toHaveURL(url => url.pathname === "/customers" && url.searchParams.get("organization_id") === other && url.searchParams.get("q") === "abc");
  await expect(page.getByRole("heading", { name: "선택한 조직이 바뀌었어요" })).toHaveCount(0);
  await page.context().clearCookies();
  await login(page, "member@contract.local", "/customers?organization_id=99999999-9999-4999-8999-999999999999&q=abc");
  await expect(page).toHaveURL(url => url.pathname === "/customers" && url.searchParams.get("organization_id") === ORGANIZATION_ID && url.searchParams.get("q") === "abc");
  await expect(page.getByRole("heading", { name: "선택한 조직이 바뀌었어요" })).toHaveCount(0);
});

test("a required choice group error reads without a broken particle", async ({ page }) => {
  await login(page, "member@contract.local", "/integrations");
  await expectTenantUrl(page, "/integrations");
  await page.getByRole("button", { name: "API 키 발급", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel(/^키 이름/).fill("권한 없는 키");
  for (const box of await dialog.getByRole("checkbox").all()) await box.uncheck();
  await dialog.getByRole("button", { name: "API 키 발급", exact: true }).click();
  await expect(dialog.getByText("하나 이상 선택하세요.", { exact: false }).first()).toBeVisible();
  await expect(dialog.getByText("을 하나 이상", { exact: false })).toHaveCount(0);
});

test("a one-time link that opens in a new tab says so", async ({ page }) => {
  await login(page, "member@contract.local", "/members");
  await expectTenantUrl(page, "/members");
  await page.getByRole("button", { name: "초대 링크 만들기", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel(/^초대할 이메일|^이메일/).fill("invitee@contract.local");
  await dialog.getByRole("button", { name: "초대 링크 발급", exact: true }).click();
  await expect(dialog.getByRole("link", { name: "초대 링크 열기", exact: true })).toHaveAccessibleDescription("새 탭에서 열려요.");
});

test("an API key without a rate limit is listed as unlimited", async ({ page }) => {
  await login(page, "member@contract.local", "/integrations");
  await expectTenantUrl(page, "/integrations");
  await page.getByRole("button", { name: "API 키 발급", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel(/^키 이름/).fill("한도 없는 키");
  await dialog.getByRole("button", { name: "API 키 발급", exact: true }).click();
  await expect(dialog.locator(".secret-result")).toBeVisible();
  await page.reload();
  const table = page.locator(".workspace-desktop-table");
  await expect(table.getByRole("row", { name: /한도 없는 키/ })).toContainText("무제한");
});

test("the sign-in frame starts its headings with the page h1", async ({ page }) => {
  for (const path of ["/login", "/signup"]) {
    await page.goto(path);
    expect(await page.evaluate(() => document.querySelector("h1, h2, h3, h4, h5, h6")?.tagName)).toBe("H1");
  }
});

test("an empty workspace table uses the small empty illustration", async ({ page }) => {
  await login(page, "member@contract.local", "/integrations");
  await expectTenantUrl(page, "/integrations");
  await expect(page.locator(".workspace-desktop-table .ant-empty-normal")).toHaveCount(1);
});

test("an invitation opened without a session does not offer the organization picker", async ({ page }) => {
  await page.goto("/organization-invitations/me_invite_ContractInvitation00000000000001");
  await expect(page.getByRole("button", { name: "초대 수락하고 시작", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "조직 선택으로", exact: true })).toHaveCount(0);
});

test("a new-account invitation opened while signed in as someone else says who is signed in", async ({ page }) => {
  await login(page);
  await expectTenantUrl(page, "/customers");
  await page.goto("/organization-invitations/me_invite_ContractInvitation00000000000001");
  const status = page.getByRole("status").filter({ hasText: "member@contract.local" });
  await expect(status).toContainText("invited@contract.local");
  await expect(page.getByRole("button", { name: "로그아웃", exact: true })).toBeVisible();
});

test("logging out on an invitation keeps the invitation open", async ({ page, request }) => {
  await login(page);
  await expectTenantUrl(page, "/customers");
  await page.goto("/organization-invitations/me_invite_ContractExisting0000000000000001");
  await expect(page.getByRole("status").filter({ hasText: "member@contract.local" })).toBeVisible();
  await page.getByRole("button", { name: "로그아웃", exact: true }).click();
  await expect(page.getByText("member@contract.local")).toHaveCount(0);
  await expect(page).toHaveURL(/\/organization-invitations\/me_invite_ContractExisting0000000000000001$/);
  await expect(page.getByRole("button", { name: "초대받은 계정으로 로그인", exact: true })).toBeVisible();
  const calls = await (await request.get(`${FIXTURE_URL}/__requests`)).json();
  expect(calls.some((call: { path: string; method: string }) => call.path === "/v1/sessions/current" && call.method === "DELETE")).toBe(true);
});

test("signup and invitation show the same required consent block", async ({ page }) => {
  const names: string[][] = [];
  for (const path of ["/signup", "/organization-invitations/me_invite_ContractInvitation00000000000001"]) {
    await page.goto(path);
    const checks = page.locator("fieldset.consent-fields label.consent-check");
    await expect(checks).toHaveCount(2);
    names.push(await checks.allTextContents());
  }
  expect(names[1]).toEqual(names[0]);
});

test("an out-of-range rate limit is reported on that field", async ({ page }) => {
  await login(page, "member@contract.local", "/integrations");
  await expectTenantUrl(page, "/integrations");
  await page.getByRole("button", { name: "API 키 발급", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel(/^키 이름/).fill("큰 한도 키");
  await dialog.getByLabel(/^분당 요청 한도/).evaluate(input => input.removeAttribute("max"));
  await dialog.getByLabel(/^분당 요청 한도/).fill("2147483648");
  await dialog.getByRole("button", { name: "API 키 발급", exact: true }).click();
  await expect(dialog.getByText("분당 요청 한도는 1 이상 2,147,483,647 이하의 정수로 입력하세요.").first()).toBeVisible();
  await expect(dialog.getByText("JSON", { exact: false })).toHaveCount(0);
});

test("signup with a blank name asks for that name instead of a length limit", async ({ page, request }) => {
  await page.goto("/signup");
  await page.getByLabel("조직 이름", { exact: true }).fill("   ");
  await page.getByLabel("담당자 이름", { exact: true }).fill("새 담당자");
  await page.getByLabel("이메일", { exact: true }).fill("blank@contract.local");
  await page.getByLabel("비밀번호", { exact: true }).fill(LOGIN_PASSWORD);
  await page.getByRole("checkbox").nth(0).check();
  await page.getByRole("checkbox").nth(1).check();
  await page.getByRole("button", { name: "가입 신청", exact: true }).click();
  await expect(page.getByText("조직 이름을 입력하세요.", { exact: true })).toBeVisible();
  await expect(page.getByText("255자 이내", { exact: false })).toHaveCount(0);
  const calls = await (await request.get(`${FIXTURE_URL}/__requests`)).json();
  expect(calls.some((call: { path: string }) => call.path === "/v1/signups")).toBe(false);
});

test("a dangerous row action is a quiet link and its final confirmation carries the weight", async ({ page }) => {
  await login(page, "member@contract.local", "/integrations");
  await expectTenantUrl(page, "/integrations");
  await page.getByRole("button", { name: "API 키 발급", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel(/^키 이름/).fill("폐기할 키");
  await dialog.getByRole("button", { name: "API 키 발급", exact: true }).click();
  await expect(dialog.locator(".secret-result")).toBeVisible();
  await page.reload();
  const trigger = page.locator(".workspace-desktop-table").getByRole("button", { name: "폐기할 키 키 폐기", exact: true });
  await expect(trigger).toHaveClass(/ant-btn-link/);
  await expect(trigger).toHaveClass(/ant-btn-dangerous/);
  await trigger.click();
  const confirm = page.getByRole("dialog").locator("button.ant-btn-dangerous[type=submit]");
  await expect(confirm).toHaveClass(/ant-btn-primary/);
});

test("the operator organization list shows the application time in the console KST format", async ({ page, request }) => {
  await login(page, "operator@contract.local", "/admin/organizations");
  await expect(page).toHaveURL(/\/admin\/organizations/);
  await request.post(`${FIXTURE_URL}/__control`, { data: { bulk: true } });
  await page.goto("/admin/organizations?status=all");
  await expect(page.getByText(/^신청일 /).first()).toHaveText(/^신청일 \d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2} KST$/);
});

test("the operator organization list asks the backend for the selected status", async ({ page, request }) => {
  await login(page, "operator@contract.local", "/admin/organizations");
  await expect(page).toHaveURL(/\/admin\/organizations/);
  await page.goto("/admin/organizations?status=all");
  await expect(page.getByRole("link", { name: "승인 대기" })).toBeVisible();
  const paths = (await (await request.get(`${FIXTURE_URL}/__requests`)).json()).map((call: { path: string }) => call.path);
  expect(paths).toContain("/v1/organizations?status=pending");
  expect(paths).toContain("/v1/organizations");
});

test("a disabled operator action explains itself through its description", async ({ page }) => {
  await login(page, "operator@contract.local", "/admin/users");
  await expect(page).toHaveURL(/\/admin\/users$/);
  const button = page.getByRole("button", { name: "플랫폼 운영 권한 제거", exact: true });
  await expect(button).toBeDisabled();
  await expect(button).toHaveAccessibleDescription(/.+/);
});

test("operator dialogs report invalid input inline instead of browser bubbles", async ({ page, request }) => {
  await login(page, "operator@contract.local", "/admin/users");
  await expect(page).toHaveURL(/\/admin\/users$/);
  await page.getByRole("button", { name: "운영자 계정 생성", exact: true }).click();
  const create = page.getByRole("dialog", { name: "플랫폼 운영자 계정 생성" });
  await create.getByLabel(/^이메일/).fill("not-an-email");
  await create.getByLabel(/^초기 비밀번호/).fill("short");
  await create.getByRole("button", { name: "운영자 계정 생성", exact: true }).click();
  await expect(create.getByText("값을 입력하세요.")).toBeVisible();
  await expect(create.getByText("이메일 주소 형식으로 입력하세요.")).toBeVisible();
  await expect(create.getByText("8자 이상으로 입력하세요.")).toBeVisible();
  await expect(create.getByLabel(/^초기 비밀번호/)).toHaveAttribute("aria-invalid", "true");
  await request.post(`${FIXTURE_URL}/__control`, { data: { bulk: true } });
  await page.goto("/admin/users");
  const card = page.getByRole("article").filter({ hasText: "operator@contract.local" });
  await card.getByRole("button", { name: "조직 소속 연결", exact: true }).click();
  const attach = page.getByRole("dialog", { name: "조직 소속 연결" });
  await attach.getByRole("button", { name: "소속 연결", exact: true }).click();
  await expect(attach.getByText("항목을 선택하세요.")).toBeVisible();
  await expect(attach.getByLabel(/^연결할 조직/)).toHaveAttribute("aria-invalid", "true");
  const calls = await (await request.get(`${FIXTURE_URL}/__requests`)).json();
  expect(calls.some((call: { path: string; method: string }) => call.method === "POST" && (call.path === "/v1/user-accounts" || /^\/v1\/organizations\/[^/]+\/members$/.test(call.path)))).toBe(false);
});

test("a missing required field is reported inline like other form errors", async ({ page, request }) => {
  await login(page, "member@contract.local", "/integrations");
  await expectTenantUrl(page, "/integrations");
  await page.getByRole("button", { name: "API 키 발급", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByRole("button", { name: "API 키 발급", exact: true }).click();
  await expect(dialog.getByText("값을 입력하세요.").first()).toBeVisible();
  await expect(dialog.getByLabel(/^키 이름/)).toHaveAttribute("aria-invalid", "true");
  const calls = await (await request.get(`${FIXTURE_URL}/__requests`)).json();
  expect(calls.some((call: { path: string; method: string }) => call.path === "/v1/api-keys" && call.method === "POST")).toBe(false);
});

test("operator actions recheck current role after demotion", async ({ page, request }) => {
  await login(page, "operator@contract.local", "/admin/users");
  await expect(page).toHaveURL(/\/admin\/users$/);
  await expect(page.getByRole("button", { name: "플랫폼 운영 권한 제거", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "플랫폼 운영자로 변경", exact: true }).click();
  const cookie = (await page.context().cookies()).find(cookie => cookie.name === "me_session")!;
  await request.post(`${FIXTURE_URL}/__control`, { data: { demote: cookie.value } });
  await page.getByRole("button", { name: "플랫폼 운영자로 변경 확정" }).click();
  await expect(page).toHaveURL(/\/organizations$/);
  const calls = await (await request.get(`${FIXTURE_URL}/__requests`)).json();
  expect(calls.some((call: { path: string; method: string }) => call.path.endsWith("/role") && call.method === "PUT")).toBe(false);
});

test("suspended tenant blocks mutations but permits switching", async ({ page, request }) => {
  await login(page);
  await expectTenantUrl(page, "/customers");
  const cookie = (await page.context().cookies()).find(cookie => cookie.name === "me_session")!;
  await request.post(`${FIXTURE_URL}/__control`, { data: { suspend: cookie.value } });
  await page.goto("/customers");
  await expect(page).toHaveURL(/\/organizations$/);
  await expect(page.getByRole("status")).toContainText("지금은 이 조직의 콘솔을 이용할 수 없어요.");
});

test("network failure is recoverable and an unconfirmed logout clears this browser without claiming success", async ({ page, request }) => {
  await login(page);
  await expectTenantUrl(page, "/customers");
  await request.post(`${FIXTURE_URL}/__control`, { data: { outage: true } });
  await page.reload();
  await expect(page.getByRole("heading", { name: "화면을 불러오지 못했어요" })).toBeVisible();
  await request.post(`${FIXTURE_URL}/__control`, { data: { outage: false } });
  await page.getByRole("button", { name: "다시 시도", exact: true }).click();
  await expect(page.locator(".organization-link")).toContainText("계약 테스트 도입사");
  await request.post(`${FIXTURE_URL}/__control`, { data: { outage: true } });
  await page.getByRole("button", { name: "로그아웃", exact: true }).click();
  await expect(page).toHaveURL(/\/login\?reason=logout_unconfirmed$/);
  expect((await page.context().cookies()).some(cookie => cookie.name === "me_session")).toBe(false);
  await expect(page.getByText("서버에서 로그아웃을 확인하지 못해", { exact: false })).toBeVisible();
  await request.post(`${FIXTURE_URL}/__control`, { data: { outage: false } });
});

test("375px forms and tenant header fit, keyboard focus and menu work", async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto("/login");
  await page.keyboard.press("Tab");
  await expect(page.getByRole("link", { name: "본문으로 이동" })).toBeFocused();
  await page.keyboard.press("Enter");
  await page.keyboard.press("Tab");
  await expect(page.getByLabel("이메일", { exact: true })).toBeFocused();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await login(page);
  await expectTenantUrl(page, "/customers");
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.getByRole("button", { name: "메뉴 열기" }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "메뉴 열기" })).toBeFocused();
  await page.getByRole("button", { name: "메뉴 열기" }).click();
  await page.getByRole("dialog").getByRole("link", { name: "미터", exact: true }).click();
  await expectTenantUrl(page, "/billable-metrics");
  await expect(page.getByRole("dialog")).toHaveCount(0);
});

test("server action replay on a public route cannot create a customer without a session", async ({ page, request }) => {
  await login(page);
  await expectTenantUrl(page, "/customers");
  await page.getByRole("button", { name: "고객 등록", exact: true }).first().click();
  await page.getByRole("dialog").getByLabel(/^고객명/).fill("액션 경계 확인");
  const actionRequest = page.waitForRequest(req => req.method() === "POST" && Boolean(req.headers()["next-action"]));
  await page.getByRole("dialog").getByRole("button", { name: "고객 저장", exact: true }).click();
  const captured = await actionRequest;
  await expect(page.getByRole("dialog")).toHaveCount(0);
  const before = await (await request.get(`${FIXTURE_URL}/__requests`)).json();
  const createsBefore = before.filter((call: { path: string; method: string }) => call.path === "/v1/customers" && call.method === "POST").length;
  expect(createsBefore).toBe(1);
  await page.context().clearCookies();
  expect((await page.context().cookies()).some(cookie => cookie.name === "me_session")).toBe(false);
  await page.request.post("/login", {
    headers: { "Next-Action": captured.headers()["next-action"], "Content-Type": captured.headers()["content-type"], Origin: FRONTEND_ORIGIN },
    data: captured.postDataBuffer()!,
    maxRedirects: 0,
  });
  const after = await (await request.get(`${FIXTURE_URL}/__requests`)).json();
  expect(after.filter((call: { path: string; method: string }) => call.path === "/v1/customers" && call.method === "POST")).toHaveLength(createsBefore);
  await page.goto("/customers");
  await expect(page).toHaveURL(/\/login\?returnTo=/);
});

test("with reduced motion a workspace dialog becomes visible and closing it releases the page", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await login(page, "member@contract.local", "/members");
  const trigger = page.getByRole("button", { name: "초대 링크 만들기", exact: true });
  await trigger.click();
  await expect(page.getByRole("dialog")).toHaveCount(1);
  await expect.poll(() => page.locator(".ant-modal").evaluate(element => getComputedStyle(element).opacity)).toBe("1");
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page.locator(".ant-modal-wrap")).toBeHidden();
  await trigger.click();
  await expect(page.getByRole("dialog")).toHaveCount(1);
});

test("the sidebar fits a short screen and scrolls by itself so its last link stays visible", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 720 });
  await login(page);
  await expectTenantUrl(page, "/customers");
  const sidebar = page.locator(".workspace-sidebar");
  const sidebarBox = (await sidebar.boundingBox())!;
  expect(sidebarBox.y + sidebarBox.height).toBeLessThanOrEqual(720);
  const lastLink = sidebar.locator(".nav-link").last();
  await lastLink.focus();
  const linkBox = (await lastLink.boundingBox())!;
  expect(linkBox.y + linkBox.height).toBeLessThanOrEqual(720);
});

test("an antd button shows a focus ring in the brand color", async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("비밀번호", { exact: true }).focus();
  const button = page.getByRole("button", { name: "로그인", exact: true });
  for (let step = 0; step < 4 && !(await button.evaluate(element => element === document.activeElement)); step++) await page.keyboard.press("Tab");
  await expect(button).toBeFocused();
  await expect(button).toHaveCSS("outline-color", "rgb(87, 70, 173)");
});

test("an unknown address shows a Korean not-found page with a main landmark and a way back", async ({ page }) => {
  const response = await page.goto("/organization-invitations/abc/def");
  expect(response!.status()).toBe(404);
  await expect(page.getByRole("heading", { level: 1, name: "페이지를 찾을 수 없어요" })).toBeVisible();
  await expect(page.locator("main#main-content")).toHaveCount(1);
  await expect(page.getByRole("link", { name: "홈으로", exact: true })).toHaveAttribute("href", "/");
});

test("the root error page shows the error digest so a report can be matched with the server log", async ({ page, request }) => {
  await login(page);
  await expectTenantUrl(page, "/customers");
  await request.post(`${FIXTURE_URL}/__control`, { data: { outage: true } });
  await page.goto(`/customers?organization_id=${ORGANIZATION_ID}`);
  const main = page.getByRole("main");
  await expect(main.getByRole("heading", { name: "화면을 불러오지 못했어요" })).toBeVisible();
  await expect(main).toContainText(/오류 코드: [0-9A-Za-z]+/);
});

for (const viewport of [{ width: 390, height: 844 }, { width: 900, height: 900 }]) {
  test(`the header height variable matches the real header at ${viewport.width}px`, async ({ page }) => {
    await page.setViewportSize(viewport);
    await login(page);
    await expectTenantUrl(page, "/customers");
    await expect(page.locator(".workspace-header")).toBeVisible();
    await expect.poll(() => page.evaluate(() => {
      const header = document.querySelector(".workspace-header")!.getBoundingClientRect().height;
      const variable = parseFloat(getComputedStyle(document.documentElement).getPropertyValue("--workspace-header-height"));
      return Math.abs(header - variable) <= 1;
    })).toBe(true);
  });
}

test("on a phone the sticky header keeps only the brand and environment and the menu drawer holds the account controls", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page);
  await expectTenantUrl(page, "/customers");
  const header = page.locator(".workspace-header");
  await expect(header.getByRole("combobox", { name: "업무 환경" })).toBeVisible();
  for (const name of ["권한 새로 조회", "로그아웃"]) await expect(header.getByRole("button", { name, exact: true })).toBeHidden();
  expect((await header.boundingBox())!.height).toBeLessThan(130);
  await page.getByRole("button", { name: "메뉴 열기" }).click();
  const drawer = page.getByRole("dialog");
  await expect(drawer.getByText("전체 관리", { exact: true })).toBeVisible();
  for (const name of ["권한 새로 조회", "로그아웃"]) await expect(drawer.getByRole("button", { name, exact: true })).toBeVisible();
});

test("between phone and desktop widths the header still leaves out the user name", async ({ page }) => {
  await page.setViewportSize({ width: 900, height: 900 });
  await login(page);
  await expectTenantUrl(page, "/customers");
  await expect(page.locator(".workspace-header .workspace-user-name")).toBeHidden();
  await expect(page.locator(".workspace-header").getByRole("button", { name: "로그아웃", exact: true })).toBeVisible();
});

test("the header shows the access scope apart from a refresh button that confirms when it finishes", async ({ page }) => {
  await login(page);
  await expectTenantUrl(page, "/customers");
  await expect(page.locator(".workspace-scope .ant-tag")).toHaveText("전체 관리");
  const refresh = page.getByRole("button", { name: "권한 새로 조회", exact: true });
  await expect(page.locator(".workspace-header").getByRole("button", { name: "로그아웃", exact: true })).toHaveClass(/ant-btn-text/);
  await expect(refresh).toHaveClass(/ant-btn-text/);
  await refresh.click();
  await expect(page.getByText("권한을 다시 조회했어요.", { exact: true })).toBeVisible();
});

test("an unchecked antd checkbox has a visible border", async ({ page }) => {
  await login(page, "member@contract.local", "/integrations");
  await page.getByRole("button", { name: "API 키 발급", exact: true }).click();
  const unchecked = page.getByRole("dialog").locator(".ant-checkbox:not(.ant-checkbox-checked)").first();
  await expect(unchecked).toHaveCSS("border-top-color", "rgb(124, 135, 153)");
});

test("a link inside a sentence keeps its underline", async ({ page }) => {
  for (const path of ["/login", "/signup"]) {
    await page.goto(path);
    await expect(page.locator(".auth-switch a")).toHaveCSS("text-decoration-line", "underline");
  }
});

test("an account in one organization sees its name in the header without the switch link", async ({ page, request }) => {
  await login(page);
  await expectTenantUrl(page, "/customers");
  await expect(page.locator("a.organization-link .anticon")).toHaveCount(1);
  const cookie = (await page.context().cookies()).find(cookie => cookie.name === "me_session")!;
  await request.post(`${FIXTURE_URL}/__control`, { data: { soleOrganization: cookie.value } });
  await page.reload();
  await expect(page.locator(".organization-link")).toContainText("계약 테스트 도입사");
  await expect(page.locator("a.organization-link")).toHaveCount(0);
  await expect(page.locator(".organization-link .anticon")).toHaveCount(0);
});

test("a long organization name is cut with an ellipsis and keeps the header icon inside the link", async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 700 });
  await login(page);
  await expectTenantUrl(page, "/customers");
  const link = page.locator(".organization-link");
  const linkBox = (await link.boundingBox())!;
  const iconBox = (await link.locator(".anticon").boundingBox())!;
  expect(iconBox.x + iconBox.width).toBeLessThanOrEqual(linkBox.x + linkBox.width + 0.5);
});

test("sidebar group titles line up with the sidebar edge", async ({ page }) => {
  await login(page);
  await expectTenantUrl(page, "/customers");
  const title = page.locator(".workspace-sidebar .nav-group-title").first();
  await expect(title).toHaveCSS("padding-inline-start", "24px");
  await expect(title).toHaveCSS("font-size", "11px");
});

test("the console no longer carries a demo guide", async ({ page }) => {
  await login(page);
  await expectTenantUrl(page, "/customers");
  await expect(page.getByRole("button", { name: "시연 안내 보기", exact: true })).toHaveCount(0);
  await expect(page.getByRole("region", { name: "시연 안내" })).toHaveCount(0);
});

test("the environment notice says what is separate and what is shared, and only the live environment draws a warning box", async ({ page }) => {
  await login(page);
  await expectTenantUrl(page, "/customers");
  const notice = page.locator(".environment-notice");
  await expect(notice).toHaveText("테스트 환경에서는 고객부터 수납까지 실제 환경과 따로 관리해요. 사용자와 업무 권한은 두 환경에서 같아요.");
  await expect(notice).not.toHaveClass(/environment-notice-live/);
  await expect(page.locator(".workspace-environment .ant-select")).toContainText("테스트 환경");
  await expect(page).toHaveURL(url => !url.searchParams.has("environment"));
});

test("the login side panel title is smaller than the task title", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("/login");
  const sizes = await page.evaluate(() => [document.querySelector(".auth-intro-title"), document.querySelector("main h1")].map(element => parseFloat(getComputedStyle(element!).fontSize)));
  expect(sizes[0]).toBeLessThan(sizes[1]);
});

test("Korean text in the console wraps between words", async ({ page }) => {
  await login(page);
  await expectTenantUrl(page, "/customers");
  await expect(page.locator("body")).toHaveCSS("word-break", "keep-all");
});

test("the home trace example is a named group", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("group", { name: "고객이 받는 청구서" })).toBeVisible();
});

test("a focused field in a workspace dialog keeps its whole focus ring inside the dialog body", async ({ page }) => {
  await login(page, "member@contract.local", "/members");
  await page.getByRole("button", { name: "초대 링크 만들기", exact: true }).click();
  const dialog = page.getByRole("dialog");
  // 열림 애니메이션 도중에는 대화상자가 축소된 크기로 재지므로 다 열린 뒤에 잰다.
  await expect.poll(() => page.locator(".ant-modal").evaluate(element => getComputedStyle(element).opacity)).toBe("1");
  await dialog.locator("input:not([type=hidden])").first().focus();
  const room = await dialog.evaluate(element => {
    const body = element.querySelector(".ant-modal-body")!;
    const scroller = [...body.querySelectorAll("*")].find(node => getComputedStyle(node).overflowY === "auto") ?? body;
    const box = scroller.getBoundingClientRect();
    const input = document.activeElement!.getBoundingClientRect();
    return { left: input.left - (box.left + scroller.clientLeft), right: box.left + scroller.clientLeft + scroller.clientWidth - input.right };
  });
  expect(room.left).toBeGreaterThanOrEqual(7);
  expect(room.right).toBeGreaterThanOrEqual(7);
});

test("screen titles use the same size whichever header component draws them", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await login(page, "member@contract.local", "/members");
  const membersTitle = await page.getByRole("heading", { level: 1, name: "사용자 관리" }).evaluate(element => getComputedStyle(element).fontSize);
  await page.goto(`/customers?organization_id=${ORGANIZATION_ID}`);
  const customersTitle = await page.getByRole("heading", { level: 1, name: "고객" }).evaluate(element => getComputedStyle(element).fontSize);
  expect(membersTitle).toBe(customersTitle);
});

test("operator list titles use the shared screen title size", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await login(page, "operator@contract.local", "/admin");
  await expect(page).toHaveURL(url => url.pathname === "/admin");
  for (const path of ["/admin/organizations", "/admin/users"]) {
    await page.goto(path);
    const sizes = await page.getByRole("heading", { level: 1 }).evaluate(element => [getComputedStyle(element).fontSize, getComputedStyle(document.documentElement).getPropertyValue("--screen-title-size").trim()]);
    expect(sizes[0], path).toBe(sizes[1]);
  }
});

test("registering a customer is the primary action in the customer list title row", async ({ page }) => {
  await login(page);
  await expect(page.getByRole("button", { name: "고객 등록", exact: true })).toHaveClass(/ant-btn-primary/);
});
