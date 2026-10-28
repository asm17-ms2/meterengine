import { test, expect, type Page } from "@playwright/test";

const FIXTURE_URL = "http://127.0.0.1:38148";
const LOGIN_PASSWORD = "contract-password";
const ORGANIZATION_ID = "11111111-1111-4111-8111-111111111111";

async function expectTenantUrl(page: Page, pathname: string) {
  await expect(page).toHaveURL(url => url.origin === "http://localhost:33148" && url.pathname === pathname && url.searchParams.size === 1 && url.searchParams.get("organization_id") === ORGANIZATION_ID);
}

async function login(page: Page, email = "member@contract.local", returnTo = "/customers") {
  await page.goto(`/login?returnTo=${encodeURIComponent(returnTo)}`);
  await page.getByLabel("이메일", { exact: true }).fill(email);
  await page.getByLabel("비밀번호", { exact: true }).fill(LOGIN_PASSWORD);
  await page.getByRole("button", { name: "로그인", exact: true }).click();
}

test.beforeEach(async ({ request }) => { await request.post(`${FIXTURE_URL}/__control`, { data: { reset: true } }); });

test("public health, gated routes, expired session and return URL", async ({ page, request }) => {
  expect((await request.get("/health")).status()).toBe(200);
  for (const path of ["/customers?q=abc", "/admin/users", "/organizations"]) {
    await page.goto(path);
    await expect(page).toHaveURL(/\/login\?returnTo=/);
  }
  await page.context().addCookies([{ name: "me_session", value: "expired-session", url: "http://localhost:33148" }]);
  await page.goto("/customers");
  await expect(page).toHaveURL(/\/login\?reason=expired&returnTo=%2Fcustomers$/);
  await login(page, "member@contract.local", "/customers");
  await expectTenantUrl(page, "/customers");
  const calls = await (await request.get(`${FIXTURE_URL}/__requests`)).json();
  expect(calls.find((call: { path: string }) => call.path === "/v1/sessions").hasAuthorization).toBe(false);
  expect(calls.some((call: { hasOrganizationHeader: boolean }) => call.hasOrganizationHeader)).toBe(false);
});

test("signup sends displayed consent versions without stale Bearer or a password-bearing follow-up", async ({ page, request }) => {
  await page.context().addCookies([{ name: "me_session", value: "expired-session", url: "http://localhost:33148" }]);
  await page.goto("/signup");
  await page.getByLabel("도입사 이름", { exact: true }).fill("새 도입사");
  await page.getByLabel("담당자 이름", { exact: true }).fill("새 담당자");
  await page.getByLabel("이메일", { exact: true }).fill("new@contract.local");
  await page.getByLabel("비밀번호", { exact: true }).fill(LOGIN_PASSWORD);
  await page.getByRole("checkbox").nth(0).check();
  await page.getByRole("checkbox").nth(1).check();
  await page.getByRole("button", { name: "가입 신청", exact: true }).click();
  await expect(page).toHaveURL(/\/pending$/);
  await expect(page.getByRole("heading", { level: 1, name: "가입 신청을 접수했습니다", exact: true })).toBeVisible();
  await expect(page.getByRole("complementary", { name: "가입 절차" }).locator('li[aria-current="step"]')).toContainText("운영자 승인");
  await expect(page.getByText("승인 여부는 운영자에게 확인하세요")).toHaveCount(0);
  await page.reload();
  expect(await page.evaluate(() => localStorage.length + sessionStorage.length)).toBe(0);
  expect(await page.content()).not.toContain(LOGIN_PASSWORD);
  const calls = await (await request.get(`${FIXTURE_URL}/__requests`)).json();
  expect(calls).toEqual([{ path: "/v1/signups", method: "POST", hasAuthorization: false, hasOrganizationHeader: false, consents: [{ document: "terms_of_service", document_version: "2026-10-26-preview" }, { document: "privacy_policy", document_version: "2026-10-26-preview" }] }]);
});

test("safe relative return targets and member admin denial", async ({ page }) => {
  for (const returnTo of ["https://example.com", "//example.com/customers", "//[", "/\\example.com", "/%2f%2fexample.com", "/admin/users", "/login", "javascript:alert(1)"]) {
    await login(page, "member@contract.local", returnTo);
    await expectTenantUrl(page, "/overview");
  }
  await page.goto("/admin/users");
  await expectTenantUrl(page, "/overview");
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
  await expect(dialog.getByRole("status")).toHaveText("복사했습니다.");
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
  await expect(page.getByText("화면을 그리지 못했습니다")).toHaveCount(0);
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
  await expect(dialog.getByText("요청 결과를 확인하지 못했습니다", { exact: false })).toBeVisible();
  await expect(dialog.getByLabel(/^키 이름/)).toHaveValue("장애 중 발급");
  await expect(page.getByText("화면을 그리지 못했습니다")).toHaveCount(0);
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

test("the sidebar menu list is not a separate tab stop", async ({ page }) => {
  await login(page);
  await expectTenantUrl(page, "/customers");
  await expect(page.getByRole("navigation", { name: "도입사 업무" }).getByRole("menu")).toHaveAttribute("tabindex", "-1");
});

test("the sidebar lists the meter screen and marks it as the current place", async ({ page }) => {
  await login(page);
  await expectTenantUrl(page, "/customers");
  const nav = page.getByRole("navigation", { name: "도입사 업무" });
  await nav.getByRole("link", { name: "미터", exact: true }).click();
  await expectTenantUrl(page, "/billable-metrics");
  await expect(nav.locator(".ant-menu-item-selected")).toHaveText("미터");
});

test("each screen has its own document title", async ({ page }) => {
  await page.goto("/login");
  await expect(page).toHaveTitle("로그인 | MeterEngine");
  await page.goto("/signup");
  await expect(page).toHaveTitle("도입사 가입 신청 | MeterEngine");
  await login(page);
  await expectTenantUrl(page, "/customers");
  await expect(page).toHaveTitle("고객 | MeterEngine");
  await page.getByRole("navigation", { name: "도입사 업무" }).getByRole("link", { name: "미터", exact: true }).click();
  await expect(page).toHaveTitle("미터 | MeterEngine");
});

test("a workspace load failure offers an in-place retry button", async ({ page, request }) => {
  await login(page, "member@contract.local", "/members");
  await expectTenantUrl(page, "/members");
  await request.post(`${FIXTURE_URL}/__control`, { data: { fail: { "/v1/organization/members": 500 } } });
  await page.reload();
  await expect(page.getByText("데이터를 불러오지 못했습니다").first()).toBeVisible();
  await expect(page.getByRole("button", { name: "다시 시도", exact: true }).first()).toBeVisible();
  await expect(page.getByRole("link", { name: "다시 시도", exact: true })).toHaveCount(0);
});

test("a forbidden workspace load says so without a retry", async ({ page, request }) => {
  await login(page, "member@contract.local", "/integrations");
  await expectTenantUrl(page, "/integrations");
  await request.post(`${FIXTURE_URL}/__control`, { data: { fail: { "/v1/api-keys": 403 } } });
  await page.reload();
  await expect(page.getByText("조회 권한이 없습니다").first()).toBeVisible();
  await expect(page.getByText("데이터를 불러오지 못했습니다")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "다시 시도", exact: true })).toHaveCount(0);
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
  await expect(dialog.getByRole("status")).toHaveText("복사했습니다.");
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
  const attach = card.getByRole("button", { name: "도입사 소속 연결", exact: true });
  const remove = card.getByRole("button", { name: "플랫폼 운영 권한 제거", exact: true });
  await expect(attach).toBeEnabled();
  await expect(remove).toBeDisabled();
  const attachBox = (await attach.boundingBox())!;
  const removeBox = (await remove.boundingBox())!;
  expect(Math.abs(attachBox.y - removeBox.y)).toBeLessThanOrEqual(0.5);
});

test("accepting an invitation as a new account does not send a stale session", async ({ page, request }) => {
  await page.context().addCookies([{ name: "me_session", value: "stale-session", url: "http://localhost:33148" }]);
  await page.goto("/organization-invitations/me_invite_ContractInvitation00000000000001");
  await page.getByLabel(/^이름/).fill("초대 받은 사용자");
  await page.getByLabel(/^비밀번호/).fill("invited-password");
  for (const box of await page.getByRole("checkbox").all()) await box.check();
  await page.getByRole("button", { name: "초대 수락하고 도입사로 이동", exact: true }).click();
  await expectTenantUrl(page, "/overview");
  const calls = await (await request.get(`${FIXTURE_URL}/__requests`)).json();
  const accept = calls.find((call: { path: string; method: string }) => call.path.endsWith("/accept") && call.method === "POST");
  expect(accept.hasAuthorization).toBe(false);
});

test("logging in to a return path of another member organization opens that organization", async ({ page }) => {
  const other = "22222222-2222-4222-8222-222222222222";
  await login(page, "member@contract.local", `/customers?organization_id=${other}&q=abc`);
  await expect(page).toHaveURL(url => url.pathname === "/customers" && url.searchParams.get("organization_id") === other && url.searchParams.get("q") === "abc");
  await expect(page.getByRole("heading", { name: "도입사 선택이 바뀌었습니다" })).toHaveCount(0);
  await page.context().clearCookies();
  await login(page, "member@contract.local", "/customers?organization_id=99999999-9999-4999-8999-999999999999&q=abc");
  await expect(page).toHaveURL(url => url.pathname === "/customers" && url.searchParams.get("organization_id") === ORGANIZATION_ID && url.searchParams.get("q") === "abc");
  await expect(page.getByRole("heading", { name: "도입사 선택이 바뀌었습니다" })).toHaveCount(0);
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
  await expect(dialog.getByRole("link", { name: "초대 링크 열기", exact: true })).toHaveAccessibleDescription("새 탭에서 열립니다.");
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
  await expect(page.getByRole("button", { name: "초대 수락하고 도입사로 이동", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "도입사 선택으로", exact: true })).toHaveCount(0);
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
  await expect(page.getByRole("button", { name: "해당 계정으로 로그인", exact: true })).toBeVisible();
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
  await page.getByLabel("도입사 이름", { exact: true }).fill("   ");
  await page.getByLabel("담당자 이름", { exact: true }).fill("새 담당자");
  await page.getByLabel("이메일", { exact: true }).fill("blank@contract.local");
  await page.getByLabel("비밀번호", { exact: true }).fill(LOGIN_PASSWORD);
  await page.getByRole("checkbox").nth(0).check();
  await page.getByRole("checkbox").nth(1).check();
  await page.getByRole("button", { name: "가입 신청", exact: true }).click();
  await expect(page.getByText("도입사 이름을 입력하세요.", { exact: true })).toBeVisible();
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
  const trigger = page.locator(".workspace-desktop-table").getByRole("button", { name: "키 폐기", exact: true });
  await expect(trigger).toHaveClass(/ant-btn-link/);
  await expect(trigger).toHaveClass(/ant-btn-dangerous/);
  await trigger.click();
  const confirm = page.getByRole("dialog").locator("button.ant-btn-dangerous[type=submit]");
  await expect(confirm).toHaveClass(/ant-btn-primary/);
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
  await expect(page.getByRole("status")).toContainText("이용 가능 여부");
});

test("network failure is recoverable and logout does not claim success before revocation", async ({ page, request }) => {
  await login(page);
  await expectTenantUrl(page, "/customers");
  await request.post(`${FIXTURE_URL}/__control`, { data: { outage: true } });
  await page.getByRole("button", { name: "로그아웃", exact: true }).click();
  await expect(page.getByRole("alert").filter({ hasText: "로그아웃을 완료하지 못했습니다" })).toContainText("로그아웃을 완료하지 못했습니다");
  expect((await page.context().cookies()).some(cookie => cookie.name === "me_session")).toBe(true);
  await page.reload();
  await expect(page.getByRole("heading", { name: "화면을 불러오지 못했습니다" })).toBeVisible();
  await request.post(`${FIXTURE_URL}/__control`, { data: { outage: false } });
  await page.getByRole("button", { name: "다시 시도", exact: true }).click();
  await expect(page.locator(".organization-link")).toContainText("계약 테스트 도입사");
  await page.getByRole("button", { name: "로그아웃", exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
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
  await page.getByRole("button", { name: "업무 메뉴 열기" }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "업무 메뉴 열기" })).toBeFocused();
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
    headers: { "Next-Action": captured.headers()["next-action"], "Content-Type": captured.headers()["content-type"], Origin: "http://localhost:33148" },
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
  const lastLink = sidebar.locator(".workspace-sidebar-note a").first();
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
  await expect(page.getByRole("heading", { level: 1, name: "페이지를 찾을 수 없습니다" })).toBeVisible();
  await expect(page.locator("main#main-content")).toHaveCount(1);
  await expect(page.getByRole("link", { name: "홈으로", exact: true })).toHaveAttribute("href", "/");
});

test("the root error page shows the error digest so a report can be matched with the server log", async ({ page, request }) => {
  await login(page);
  await expectTenantUrl(page, "/customers");
  await request.post(`${FIXTURE_URL}/__control`, { data: { outage: true } });
  await page.goto(`/customers?organization_id=${ORGANIZATION_ID}`);
  const main = page.getByRole("main");
  await expect(main.getByRole("heading", { name: "화면을 불러오지 못했습니다" })).toBeVisible();
  await expect(main).toContainText(/\([0-9A-Za-z]+\)/);
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

test("the header shows the access scope apart from a refresh button that confirms when it finishes", async ({ page }) => {
  await login(page);
  await expectTenantUrl(page, "/customers");
  await expect(page.locator(".workspace-scope")).toHaveText("전체 관리");
  const refresh = page.getByRole("button", { name: "권한 새로 조회", exact: true });
  await refresh.click();
  await expect(page.getByText("권한을 다시 조회했습니다.", { exact: true })).toBeVisible();
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

test("a long organization name is cut with an ellipsis and keeps the header icon inside the link", async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 700 });
  await login(page);
  await expectTenantUrl(page, "/customers");
  const link = page.locator(".organization-link");
  const linkBox = (await link.boundingBox())!;
  const iconBox = (await link.locator(".anticon").boundingBox())!;
  expect(iconBox.x + iconBox.width).toBeLessThanOrEqual(linkBox.x + linkBox.width + 0.5);
});

test("sidebar group titles start on the same line as the sidebar label", async ({ page }) => {
  await login(page);
  await expectTenantUrl(page, "/customers");
  const title = page.locator(".workspace-sidebar .ant-menu-item-group-title").first();
  await expect(title).toHaveCSS("padding-inline-start", "24px");
  await expect(title).toHaveCSS("font-size", "11px");
});

test("the guide toggle keeps a name that says what it does instead of a pressed state", async ({ page }) => {
  await login(page);
  await expectTenantUrl(page, "/customers");
  await page.getByRole("button", { name: "시연 안내 보기", exact: true }).click();
  const toggles = page.getByRole("button", { name: "업무 화면으로", exact: true });
  await expect(toggles).toHaveCount(2);
  for (const toggle of await toggles.all()) await expect(toggle).not.toHaveAttribute("aria-pressed");
});

test("the sidebar calls the integrations screen by its menu name", async ({ page }) => {
  await login(page);
  await expectTenantUrl(page, "/customers");
  await expect(page.locator(".workspace-sidebar-note").getByRole("link")).toHaveText("API 키와 통보");
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
  await expect(page.getByRole("group", { name: "한 고객, 한 청구 월" })).toBeVisible();
});

test("a focused field in a workspace dialog keeps its whole focus ring inside the dialog body", async ({ page }) => {
  await login(page, "member@contract.local", "/members");
  await page.getByRole("button", { name: "초대 링크 만들기", exact: true }).click();
  const dialog = page.getByRole("dialog");
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
