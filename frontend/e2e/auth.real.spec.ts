import { test, expect, type Page } from "@playwright/test";

const API_URL = process.env.S1_API_URL ?? "http://127.0.0.1:38147";
const PASSWORD = "local-member-e2e-password";
const OPERATOR_EMAIL = process.env.E2E_OPERATOR_EMAIL ?? "operator@fe-e2e.local";
const OPERATOR_PASSWORD = process.env.E2E_OPERATOR_PASSWORD ?? "local-operator-e2e-password";

async function login(page: Page, email: string, password = PASSWORD, returnTo = "") {
  await page.goto(`/login${returnTo ? `?returnTo=${encodeURIComponent(returnTo)}` : ""}`);
  await page.getByLabel("이메일", { exact: true }).fill(email);
  await page.getByLabel("비밀번호", { exact: true }).fill(password);
  await page.getByRole("button", { name: "로그인", exact: true }).click();
}

async function signup(page: Page, name: string, email: string) {
  await page.goto("/signup");
  await page.getByLabel("조직 이름", { exact: true }).fill(name);
  await page.getByLabel("담당자 이름", { exact: true }).fill("테스트 담당자");
  await page.getByLabel("이메일", { exact: true }).fill(email);
  await page.getByLabel("비밀번호", { exact: true }).fill(PASSWORD);
  await page.getByRole("checkbox").nth(0).check();
  await page.getByRole("checkbox").nth(1).check();
  await page.getByRole("button", { name: "가입", exact: true }).click();
  // 이메일 인증을 끈 로컬 서버에서는 가입하자마자 로그인 화면으로 간다.
  await expect(page).toHaveURL(/\/login\?reason=signed_up$/);
}

test("real Spring: signup, immediate login, locked live environment, tenant actions and isolation, logout", async ({ page, request }) => {
  const suffix = Date.now();
  const email = `member-${suffix}@fe-e2e.local`;
  const orgName = `가입 E2E ${suffix}`;
  await signup(page, orgName, email);
  expect((await page.context().cookies()).some(cookie => cookie.name === "me_session")).toBeFalsy();
  // 운영자 승인 없이 바로 로그인해 테스트 환경을 쓴다.
  await login(page, email, PASSWORD, "/customers");
  await expect(page).toHaveURL(/\/customers(?:\?.*)?$/);
  await expect(page.locator(".organization-link")).toContainText(orgName);
  const cookie = (await page.context().cookies()).find(cookie => cookie.name === "me_session")!;
  expect(cookie.httpOnly).toBeTruthy();
  expect(cookie.secure).toBeTruthy();
  expect(cookie.sameSite).toBe("Lax");
  expect(await page.evaluate(() => document.cookie)).not.toContain("me_session");
  expect(await page.content()).not.toContain(cookie.value);
  await page.reload();
  await expect(page.locator(".organization-link")).toContainText(orgName);
  // 실제 환경은 잠겨 있어 고르면 이용 신청 화면으로 간다.
  await expect(page.locator(".environment-notice")).toContainText("실제 환경은 이용 신청이 승인되면 쓸 수 있어요.");
  await page.goto("/customers?environment=live");
  await expect(page).toHaveURL(url => url.pathname === "/live-environment");
  await expect(page.getByRole("heading", { level: 2, name: "실제 환경은 이용 신청이 승인되면 쓸 수 있어요", exact: true })).toBeVisible();
  await page.goto("/customers");
  await page.getByRole("button", { name: "고객 등록", exact: true }).first().click();
  await page.getByRole("dialog").getByLabel(/^고객명/).fill("E2E 고객");
  await page.getByRole("dialog").getByRole("button", { name: "고객 저장", exact: true }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page.getByRole("cell", { name: "E2E 고객", exact: true })).toBeVisible();
  for (const route of ["/events", "/usage", "/billing", "/invoices", "/billable-metrics", "/billable-metric-prices", "/customers"]) {
    await page.goto(route);
    await expect(page.locator(".organization-link")).toContainText(orgName);
    await expect(page.getByRole("heading", { name: "화면을 불러오지 못했어요" })).toHaveCount(0);
    await expect(page.locator(".ant-alert-error")).toHaveCount(0);
  }

  // 다른 조직에는 그 조직의 대표관리자가 초대해야 들어간다. 운영자가 계정을 붙이는 길은 없다.
  const secondEmail = `second-${suffix}@fe-e2e.local`;
  const second = await request.post(`${API_URL}/v1/signups`, { data: { organization_name: `두 번째 E2E ${suffix}`, name: "추가 담당자", email: secondEmail, password: PASSWORD, consents: [{ document: "terms_of_service", document_version: "e2e" }, { document: "privacy_policy", document_version: "e2e" }] } });
  expect(second.status()).toBe(201);
  const secondOrg = (await second.json()).organization;
  const secondOwner = await request.post(`${API_URL}/v1/sessions`, { data: { email: secondEmail, password: PASSWORD } });
  expect(secondOwner.status()).toBe(201);
  const secondHeaders = { Authorization: `Bearer ${(await secondOwner.json()).token}`, "X-MeterEngine-Environment": "test" };
  const invitation = await request.post(`${API_URL}/v1/organization/invitations`, { headers: secondHeaders, data: { email, scopes: ["read"] } });
  expect(invitation.status()).toBe(201);
  expect((await request.post(`${API_URL}/v1/organization-invitations/${(await invitation.json()).token}/accept`, { headers: { Authorization: `Bearer ${cookie.value}` } })).status()).toBe(201);
  await page.goto("/organizations");
  await page.getByLabel("이동할 조직").selectOption(secondOrg.id);
  await page.getByRole("button", { name: "이동", exact: true }).click();
  await expect(page).toHaveURL(/\/overview(?:\?.*)?$/);
  await expect(page.locator(".organization-link")).toContainText(secondOrg.name);
  await page.goto("/customers");
  await expect(page.getByText("E2E 고객", { exact: true })).toHaveCount(0);
  await page.goto("/admin/users");
  await expect(page).toHaveURL(/\/overview(?:\?.*)?$/);
  await page.getByRole("button", { name: "로그아웃", exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  expect((await page.context().cookies()).some(cookie => cookie.name === "me_session")).toBeFalsy();
  expect((await request.get(`${API_URL}/v1/sessions/current`, { headers: { Authorization: `Bearer ${cookie.value}` } })).status()).toBe(401);
  await page.goto("/customers");
  await expect(page).toHaveURL(/\/login\?returnTo=/);
});

test("real Spring: operator without organization and mobile keyboard access", async ({ page, request }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await login(page, OPERATOR_EMAIL, OPERATOR_PASSWORD);
  await expect(page).toHaveURL(/\/admin$/);
  const cookie = (await page.context().cookies()).find(cookie => cookie.name === "me_session")!;
  const current = await request.get(`${API_URL}/v1/sessions/current`, { headers: { Authorization: `Bearer ${cookie.value}` } });
  expect((await current.json()).organization).toBeNull();
  // 운영자는 조직에 소속되지 않아 조직으로 가는 메뉴가 없고, 조직 선택 화면을 열면 운영 화면으로 돌아온다.
  await expect(page.getByRole("link", { name: "내 조직으로 이동" })).toHaveCount(0);
  await page.goto("/organizations");
  await expect(page).toHaveURL(/\/admin$/);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  await page.goto("/signup");
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  await page.keyboard.press("Tab");
  await expect(page.getByRole("link", { name: "본문으로 이동" })).toBeFocused();
});

test("real Spring: role promotion needs an account without an organization, and the operator reviews live environment applications", async ({ page, request }) => {
  const suffix = Date.now();
  const email = `review-${suffix}@fe-e2e.local`;
  const orgName = `검토 E2E ${suffix}`;
  const signup = await request.post(`${API_URL}/v1/signups`, { data: { organization_name: orgName, name: "검토 담당자", email, password: PASSWORD, consents: [{ document: "terms_of_service", document_version: "e2e" }, { document: "privacy_policy", document_version: "e2e" }] } });
  expect(signup.status()).toBe(201);
  await login(page, OPERATOR_EMAIL, OPERATOR_PASSWORD, "/admin/users");
  await expect(page).toHaveURL(/\/admin\/users$/);
  // 조직에 소속된 계정은 운영자가 되지 못한다.
  const userCard = page.getByRole("article").filter({ has: page.getByText(email, { exact: true }) });
  const promote = userCard.getByRole("button", { name: "플랫폼 운영자로 변경", exact: true });
  await expect(promote).toBeDisabled();
  await expect(promote).toHaveAccessibleDescription("조직에 소속된 계정은 플랫폼 운영자로 바꿀 수 없어요.");
  const cookie = (await page.context().cookies()).find(item => item.name === "me_session")!;
  const operatorHeaders = { Authorization: `Bearer ${cookie.value}` };
  const users = await (await request.get(`${API_URL}/v1/user-accounts`, { headers: operatorHeaders })).json();
  const applicant = users.user_accounts.find((user: { email: string }) => user.email === email);
  expect((await request.put(`${API_URL}/v1/user-accounts/${applicant.id}/role`, { headers: operatorHeaders, data: { role: "operator" } })).status()).toBe(409);
  // 가입한 조직은 승인 없이 사용 중이고 실제 환경만 잠겨 있다.
  await page.goto("/admin/organizations");
  const organization = page.getByRole("article").filter({ has: page.getByRole("heading", { name: orgName, exact: true }) });
  await expect(organization).toContainText("사용 중");
  await expect(organization).toContainText("실제 환경 승인 전");
  await expect(organization.getByRole("button")).toHaveCount(0);
  await page.goto("/admin/live-environment-applications");
  await expect(page.getByRole("heading", { level: 1, name: "실제 환경 이용 신청", exact: true })).toBeVisible();
});
