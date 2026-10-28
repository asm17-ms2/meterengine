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
  await expect(page).toHaveURL(/\/login\?reason=expired$/);
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
  await page.reload();
  expect(await page.evaluate(() => localStorage.length + sessionStorage.length)).toBe(0);
  expect(await page.content()).not.toContain(LOGIN_PASSWORD);
  const calls = await (await request.get(`${FIXTURE_URL}/__requests`)).json();
  expect(calls).toEqual([{ path: "/v1/signups", method: "POST", hasAuthorization: false, hasOrganizationHeader: false, consents: [{ document: "terms_of_service", document_version: "2026-10-26-preview" }, { document: "privacy_policy", document_version: "2026-10-26-preview" }] }]);
});

test("safe relative return targets and member admin denial", async ({ page }) => {
  for (const returnTo of ["https://example.com", "//example.com/customers", "//[", "/\\example.com", "/%2f%2fexample.com", "/admin/users", "/login", "javascript:alert(1)"]) {
    await login(page, "member@contract.local", returnTo);
    await expect(page).toHaveURL(url => url.origin === "http://localhost:33148" && ["/customers", "/overview"].includes(url.pathname) && url.searchParams.get("organization_id") === ORGANIZATION_ID);
  }
  await page.goto("/admin/users");
  await expect(page).toHaveURL(url => url.origin === "http://localhost:33148" && ["/customers", "/overview"].includes(url.pathname) && url.searchParams.get("organization_id") === ORGANIZATION_ID);
});

test("tenant actions recheck session after revocation, independently of route layout", async ({ page, request }) => {
  await login(page);
  await expectTenantUrl(page, "/customers");
  await page.getByRole("button", { name: "고객 등록", exact: true }).first().click();
  await page.getByRole("dialog").getByLabel(/^고객명/).fill("보내면 안 되는 고객");
  const cookie = (await page.context().cookies()).find(cookie => cookie.name === "me_session")!;
  await request.post(`${FIXTURE_URL}/__control`, { data: { revoke: cookie.value } });
  await page.getByRole("dialog").getByRole("button", { name: /^(?:고객 )?저장$/, exact: true }).click();
  await expect(page).toHaveURL(/\/login\?reason=expired$/);
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
  await expect(page).toHaveURL(/\/login\?reason=expired$/);
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
  await expect(page).toHaveURL(/\/login\?reason=expired$/);
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

test("accepting an invitation as a new account does not send a stale session", async ({ page, request }) => {
  await page.context().addCookies([{ name: "me_session", value: "stale-session", url: "http://localhost:33148" }]);
  await page.goto("/organization-invitations/me_invite_ContractInvitation00000000000001");
  await page.getByLabel(/^이름/).fill("초대 받은 사용자");
  await page.getByLabel(/^비밀번호/).fill("invited-password");
  for (const box of await page.getByRole("checkbox").all()) await box.check();
  await page.getByRole("button", { name: "초대 수락하고 도입사로 이동", exact: true }).click();
  await expectTenantUrl(page, "/customers");
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

test("logout keeps the session after a revocation failure and supports retry", async ({ page, request }) => {
  await login(page);
  await expectTenantUrl(page, "/customers");
  await request.post(`${FIXTURE_URL}/__control`, { data: { outage: true } });
  await page.getByRole("button", { name: "로그아웃", exact: true }).click();
  await expect(page.getByRole("alert").filter({ hasText: "로그아웃을 완료하지 못했습니다" })).toContainText("로그아웃을 완료하지 못했습니다");
  expect((await page.context().cookies()).some(cookie => cookie.name === "me_session")).toBe(true);
  await request.post(`${FIXTURE_URL}/__control`, { data: { outage: false } });
  await page.goto(`/customers?organization_id=${ORGANIZATION_ID}`);
  await expectTenantUrl(page, "/customers");
  await page.getByRole("button", { name: "로그아웃", exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
});

test("server action replay on a public route cannot create a customer without a session", async ({ page, request }) => {
  await login(page);
  await expectTenantUrl(page, "/customers");
  await page.getByRole("button", { name: "고객 등록", exact: true }).first().click();
  await page.getByRole("dialog").getByLabel(/^고객명/).fill("액션 경계 확인");
  const actionRequest = page.waitForRequest(req => req.method() === "POST" && Boolean(req.headers()["next-action"]));
  await page.getByRole("dialog").getByRole("button", { name: /^(?:고객 )?저장$/, exact: true }).click();
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
