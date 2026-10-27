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
  await page.getByLabel("도입사 이름", { exact: true }).fill(name);
  await page.getByLabel("담당자 이름", { exact: true }).fill("테스트 담당자");
  await page.getByLabel("이메일", { exact: true }).fill(email);
  await page.getByLabel("비밀번호", { exact: true }).fill(PASSWORD);
  await page.getByRole("checkbox").nth(0).check();
  await page.getByRole("checkbox").nth(1).check();
  await page.getByRole("button", { name: "가입 신청", exact: true }).click();
  await expect(page).toHaveURL(/\/pending$/);
}

test("real Spring: signup, approval, login, tenant actions and isolation, logout", async ({ page, browser, request }) => {
  const suffix = Date.now();
  const email = `member-${suffix}@fe-e2e.local`;
  const orgName = `가입 E2E ${suffix}`;
  await signup(page, orgName, email);
  expect((await page.context().cookies()).some(cookie => cookie.name === "me_session")).toBeFalsy();
  await login(page, email);
  await expect(page.getByRole("alert").filter({ hasText: "사용 가능한 도입사가 없습니다" })).toContainText("사용 가능한 도입사가 없습니다");
  await expect(page).toHaveURL(/\/login/);

  const adminContext = await browser.newContext();
  const admin = await adminContext.newPage();
  await login(admin, OPERATOR_EMAIL, OPERATOR_PASSWORD, "/admin/organizations");
  await expect(admin).toHaveURL(/\/admin\/organizations$/);
  await expect(admin.locator(".operator-badge")).toBeVisible();
  const card = admin.getByRole("article").filter({ has: admin.getByRole("heading", { name: orgName, exact: true }) });
  await expect(card).toContainText(email);
  await card.getByRole("button", { name: "가입 승인", exact: true }).click();
  await expect(admin.getByRole("dialog")).toContainText(orgName);
  await admin.getByRole("button", { name: "가입 승인 확정" }).click();
  await expect(card).toHaveCount(0);

  await login(page, email, PASSWORD, "/customers");
  await expect(page).toHaveURL(/\/customers(?:\?.*)?$/);
  await expect(page.getByRole("link", { name: orgName, exact: true })).toContainText(orgName);
  const cookie = (await page.context().cookies()).find(cookie => cookie.name === "me_session")!;
  expect(cookie.httpOnly).toBeTruthy();
  expect(cookie.secure).toBeTruthy();
  expect(cookie.sameSite).toBe("Lax");
  expect(await page.evaluate(() => document.cookie)).not.toContain("me_session");
  expect(await page.content()).not.toContain(cookie.value);
  await page.reload();
  await expect(page.getByRole("link", { name: orgName, exact: true })).toContainText(orgName);
  await page.getByRole("button", { name: "고객 등록", exact: true }).first().click();
  await page.getByRole("dialog").getByLabel(/^고객명/).fill("E2E 고객");
  await page.getByRole("dialog").getByRole("button", { name: /^(?:고객 )?저장$/, exact: true }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page.getByRole("cell", { name: "E2E 고객", exact: true })).toBeVisible();
  for (const route of ["/events", "/usage", "/billing", "/invoices", "/billable-metrics", "/billable-metric-prices", "/customers"]) {
    await page.goto(route);
    await expect(page.getByRole("link", { name: orgName, exact: true })).toContainText(orgName);
    await expect(page.getByRole("heading", { name: "화면을 불러오지 못했습니다" })).toHaveCount(0);
    await expect(page.locator(".ant-alert-error")).toHaveCount(0);
  }

  const operatorCookie = (await adminContext.cookies()).find(cookie => cookie.name === "me_session")!;
  const operatorHeaders = { Authorization: `Bearer ${operatorCookie.value}` };
  const second = await request.post(`${API_URL}/v1/signups`, { data: { organization_name: `두 번째 E2E ${suffix}`, name: "추가 담당자", email: `second-${suffix}@fe-e2e.local`, password: PASSWORD, consents: [{ document: "terms_of_service", document_version: "e2e" }, { document: "privacy_policy", document_version: "e2e" }] } });
  expect(second.status()).toBe(201);
  const secondOrg = (await second.json()).organization;
  expect((await request.put(`${API_URL}/v1/organizations/${secondOrg.id}/status`, { headers: operatorHeaders, data: { status: "active" } })).ok()).toBeTruthy();
  const current = await request.get(`${API_URL}/v1/sessions/current`, { headers: { Authorization: `Bearer ${cookie.value}` } });
  const user = (await current.json()).user_account;
  expect((await request.post(`${API_URL}/v1/organizations/${secondOrg.id}/members`, { headers: operatorHeaders, data: { user_account_id: user.id } })).status()).toBe(201);
  await page.goto("/organizations");
  await page.getByLabel("접근할 도입사").selectOption(secondOrg.id);
  await page.getByRole("button", { name: "도입사로 이동" }).click();
  await expect(page).toHaveURL(/\/(?:customers|overview)(?:\?.*)?$/);
  await expect(page.getByRole("link", { name: secondOrg.name, exact: true })).toBeVisible();
  await page.goto("/customers");
  await expect(page.getByText("E2E 고객", { exact: true })).toHaveCount(0);
  await page.goto("/admin/users");
  await expect(page).toHaveURL(/\/(?:customers|overview)(?:\?.*)?$/);
  await page.getByRole("button", { name: "로그아웃", exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  expect((await page.context().cookies()).some(cookie => cookie.name === "me_session")).toBeFalsy();
  expect((await request.get(`${API_URL}/v1/sessions/current`, { headers: { Authorization: `Bearer ${cookie.value}` } })).status()).toBe(401);
  await page.goto("/customers");
  await expect(page).toHaveURL(/\/login\?returnTo=/);
  await adminContext.close();
});

test("real Spring: operator without organization can manage the platform", async ({ page, request }) => {
  await login(page, OPERATOR_EMAIL, OPERATOR_PASSWORD);
  await expect(page).toHaveURL(/\/admin\/organizations$/);
  const cookie = (await page.context().cookies()).find(cookie => cookie.name === "me_session")!;
  const current = await request.get(`${API_URL}/v1/sessions/current`, { headers: { Authorization: `Bearer ${cookie.value}` } });
  expect((await current.json()).organization).toBeNull();
  await page.getByRole("link", { name: "내 도입사로 이동" }).click();
  await expect(page.getByText("소속된 도입사가 없습니다.", { exact: false })).toBeVisible();
  await page.getByRole("link", { name: "운영자 화면으로" }).click();
  await expect(page).toHaveURL(/\/admin\/organizations$/);

});

test("real Spring: role promotion and demotion, irreversible signup rejection", async ({ page, browser, request }) => {
  const suffix = Date.now();
  const email = `review-${suffix}@fe-e2e.local`;
  const orgName = `거절 E2E ${suffix}`;
  const applicantContext = await browser.newContext();
  const applicant = await applicantContext.newPage();
  await signup(applicant, orgName, email);
  await login(page, OPERATOR_EMAIL, OPERATOR_PASSWORD, "/admin/users");
  await expect(page).toHaveURL(/\/admin\/users$/);
  const userCard = page.getByRole("article").filter({ has: page.getByText(email, { exact: true }) });
  await userCard.getByRole("button", { name: "플랫폼 운영자로 변경", exact: true }).click();
  await page.getByRole("button", { name: "플랫폼 운영자로 변경 확정", exact: true }).click();
  await expect(userCard.getByRole("button", { name: "플랫폼 운영 권한 제거", exact: true })).toBeVisible();
  await userCard.getByRole("button", { name: "플랫폼 운영 권한 제거", exact: true }).click();
  await page.getByRole("button", { name: "플랫폼 운영 권한 제거 확정", exact: true }).click();
  await expect(userCard.getByRole("button", { name: "플랫폼 운영자로 변경", exact: true })).toBeVisible();
  const cookie = (await page.context().cookies()).find(item => item.name === "me_session")!;
  const users = await request.get(`${API_URL}/v1/user-accounts`, { headers: { Authorization: `Bearer ${cookie.value}` } });
  expect((await users.json()).user_accounts.find((user: { email: string }) => user.email === email).role).toBe("member");
  await page.goto("/admin/organizations");
  const organization = page.getByRole("article").filter({ has: page.getByRole("heading", { name: orgName, exact: true }) });
  await organization.getByRole("button", { name: "가입 거절", exact: true }).click();
  await expect(page.getByRole("dialog")).toContainText("거절한 가입은 다시 승인할 수 없습니다");
  await page.getByRole("button", { name: "가입 거절 확정", exact: true }).click();
  await expect(organization).toHaveCount(0);
  await page.getByRole("link", { name: "가입 거절", exact: true }).click();
  await expect(organization).toContainText("가입 거절");
  await expect(organization.getByRole("button")).toHaveCount(0);
  await login(applicant, email);
  await expect(applicant.getByRole("alert").filter({ hasText: "사용 가능한 도입사가 없습니다" })).toBeVisible();
  await applicantContext.close();
});
