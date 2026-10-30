import { expect, test, type BrowserContext, type Page } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { CONSENT_DOCUMENTS, CONSENT_VERSION } from "../src/lib/auth/consents";

const API_URL = process.env.S1_API_URL ?? "http://127.0.0.1:38147";
const FRONTEND_URL = "http://localhost:33147";
const OPERATOR_EMAIL = process.env.E2E_OPERATOR_EMAIL ?? "operator@fe-e2e.local";
const OPERATOR_PASSWORD = process.env.E2E_OPERATOR_PASSWORD ?? "local-operator-e2e-password";
const CREATED_PASSWORD = " operator-create-e2e-password ";
const CHANGED_PASSWORD = "operator-create-e2e-own-password";
const DUPLICATE_PASSWORD = "changed-operator-e2e-password";
const TENANT_PASSWORD = "operator-tenant-e2e-password";

test.use({ trace: "off", screenshot: "off", video: "off" });

let previousCopyPrompt: string | undefined;
test.beforeAll(() => {
  previousCopyPrompt = process.env.PLAYWRIGHT_NO_COPY_PROMPT;
  // 실패 DOM에도 입력한 초기 비밀번호를 보존하지 않는다.
  process.env.PLAYWRIGHT_NO_COPY_PROMPT = "1";
});
test.afterAll(() => {
  if (previousCopyPrompt === undefined) delete process.env.PLAYWRIGHT_NO_COPY_PROMPT;
  else process.env.PLAYWRIGHT_NO_COPY_PROMPT = previousCopyPrompt;
});

async function login(page: Page, email: string, password: string) {
  await page.goto("/login?returnTo=%2Fadmin%2Fusers");
  await page.getByLabel("이메일", { exact: true }).fill(email);
  await page.getByLabel("비밀번호", { exact: true }).fill(password);
  await page.getByRole("button", { name: "로그인", exact: true }).click();
}

function safeFailure(error: unknown, secrets: Set<string>) {
  let message = error instanceof Error ? error.message : "운영자 계정 생성 검증을 마치지 못했습니다.";
  for (const secret of secrets) if (secret) message = message.split(secret).join("[redacted]");
  return new Error(message);
}

test("운영자 신규 생성과 첫 동의, 중복 보호와 생성자 감사가 실제 화면으로 이어진다", async ({ browser, request }) => {
  const suffix = randomUUID();
  const createdEmail = `created-operator-${suffix}@fe-e2e.local`;
  const createdName = `신규 운영자 ${suffix.slice(0, 8)}`;
  const deniedEmail = `denied-operator-${suffix}@fe-e2e.local`;
  const contexts: BrowserContext[] = [];
  const secrets = new Set([OPERATOR_PASSWORD, CREATED_PASSWORD, CHANGED_PASSWORD, DUPLICATE_PASSWORD, TENANT_PASSWORD]);
  let tenantToken = "";
  let tenantApiKeyId = "";
  let failure: unknown;

  async function newPage() {
    const context = await browser.newContext({ baseURL: FRONTEND_URL, viewport: { width: 1280, height: 900 } });
    contexts.push(context);
    return context.newPage();
  }

  try {
    const operator = await newPage();
    await login(operator, OPERATOR_EMAIL, OPERATOR_PASSWORD);
    await expect(operator).toHaveURL(/\/admin\/users$/);
    const operatorToken = (await operator.context().cookies()).find(cookie => cookie.name === "me_session")?.value;
    if (!operatorToken) throw new Error("기존 운영자 검증 세션을 받지 못했습니다.");
    secrets.add(operatorToken);
    const operatorHeaders = { Authorization: `Bearer ${operatorToken}` };
    const current = await request.get(`${API_URL}/v1/sessions/current`, { headers: operatorHeaders });
    expect(current.status()).toBe(200);
    const operatorId = (await current.json()).user_account.id;

    await test.step("모바일에서 새 운영자 계정 생성", async () => {
      await operator.setViewportSize({ width: 390, height: 844 });
      await operator.getByRole("button", { name: "운영자 계정 생성", exact: true }).click();
      const dialog = operator.getByRole("dialog", { name: "플랫폼 운영자 계정 생성", exact: true });
      await expect(dialog).toBeVisible();
      expect(await operator.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
      const bounds = await dialog.boundingBox();
      expect(bounds).not.toBeNull();
      expect(bounds!.width).toBeLessThanOrEqual(390);
      await dialog.getByLabel(/^이름/).fill(createdName);
      await dialog.getByLabel(/^이메일/).fill(createdEmail);
      await dialog.getByLabel(/^초기 비밀번호/).fill(CREATED_PASSWORD);
      await dialog.getByRole("button", { name: "운영자 계정 생성", exact: true }).click();
      await expect(dialog).toHaveCount(0);
      const card = operator.getByRole("article").filter({ has: operator.getByRole("heading", { name: createdName, exact: true }) });
      await expect(card).toContainText(createdEmail);
      await expect(card.getByText("플랫폼 운영자", { exact: true })).toBeVisible();
      await expect(card).toContainText("소속 도입사 없음");
      expect(await operator.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
      if (process.env.OPERATOR_CREATE_SCREENSHOT_PATH) {
        await card.scrollIntoViewIfNeeded();
        await operator.screenshot({ path: process.env.OPERATOR_CREATE_SCREENSHOT_PATH });
      }
    });

    await test.step("중복 이메일은 입력을 유지하고 기존 계정을 바꾸지 않는다", async () => {
      await operator.getByRole("button", { name: "운영자 계정 생성", exact: true }).click();
      const dialog = operator.getByRole("dialog", { name: "플랫폼 운영자 계정 생성", exact: true });
      await dialog.getByLabel(/^이름/).fill("덮어쓰면 안 되는 이름");
      await dialog.getByLabel(/^이메일/).fill(createdEmail);
      await dialog.getByLabel(/^초기 비밀번호/).fill(DUPLICATE_PASSWORD);
      await dialog.getByRole("button", { name: "운영자 계정 생성", exact: true }).click();
      await expect(dialog).toContainText("이미 가입한 이메일입니다. 기존 계정은 사용자 목록에서 플랫폼 운영자로 변경하세요.");
      await expect(dialog.getByLabel(/^이름/)).toHaveValue("덮어쓰면 안 되는 이름");
      await expect(dialog.getByLabel(/^이메일/)).toHaveValue(createdEmail);
      await expect(dialog.getByLabel(/^이메일/)).toBeFocused();
      operator.once("dialog", confirmation => confirmation.accept());
      await dialog.getByRole("button", { name: "닫기", exact: true }).last().click();
      await expect(dialog).toHaveCount(0);
      const denied = await request.post(`${API_URL}/v1/sessions`, { data: { email: createdEmail, password: DUPLICATE_PASSWORD } });
      expect(denied.status()).toBe(401);
    });

    let createdId = "";
    await test.step("새 운영자도 본인 동의 전에는 운영 화면과 API에 접근할 수 없다", async () => {
      const created = await newPage();
      await login(created, createdEmail, CREATED_PASSWORD);
      await expect(created).toHaveURL(/\/account\/consents(?:\?|$)/);
      const createdToken = (await created.context().cookies()).find(cookie => cookie.name === "me_session")?.value;
      if (!createdToken) throw new Error("새 운영자 검증 세션을 받지 못했습니다.");
      secrets.add(createdToken);
      const headers = { Authorization: `Bearer ${createdToken}` };
      const session = await request.get(`${API_URL}/v1/sessions/current`, { headers });
      expect(session.status()).toBe(200);
      const account = await session.json();
      createdId = account.user_account.id;
      expect(account).toMatchObject({ consent_required: true, organization: null, organizations: [], user_account: { name: createdName, role: "operator" } });
      const blocked = await request.get(`${API_URL}/v1/operations`, { headers });
      expect(blocked.status()).toBe(403);
      expect((await blocked.json()).code).toBe("consent_required");
      await created.goto("/admin/users");
      await expect(created).toHaveURL(/\/account\/consents(?:\?|$)/);
      await expect(created.getByRole("checkbox")).toHaveCount(2);
      await created.getByLabel(/^새 비밀번호 \(필수\)/).fill(CHANGED_PASSWORD);
      await created.getByLabel(/^새 비밀번호 확인/).fill(CHANGED_PASSWORD);
      for (const checkbox of await created.getByRole("checkbox").all()) await checkbox.check();
      await created.getByRole("button", { name: "동의하고 운영 시작", exact: true }).click();
      await expect(created).toHaveURL(/\/admin(?:\/users)?$/);
      expect((await request.post(`${API_URL}/v1/sessions`, { data: { email: createdEmail, password: CREATED_PASSWORD } })).status()).toBe(401);
      await created.goto("/admin/users");
      await expect(created.getByRole("heading", { name: "사용자와 플랫폼 권한", exact: true })).toBeVisible();
      const allowed = await request.get(`${API_URL}/v1/operations`, { headers });
      expect(allowed.status()).toBe(200);
    });

    await test.step("도입사 대표관리자와 admin API 키에는 운영자 생성 권한이 없다", async () => {
      const tenantEmail = `operator-tenant-${suffix}@fe-e2e.local`;
      const signup = await request.post(`${API_URL}/v1/signups`, { data: {
        organization_name: `운영자 생성 권한 검증 ${suffix}`, name: "도입사 대표관리자", email: tenantEmail, password: TENANT_PASSWORD,
        consents: CONSENT_DOCUMENTS.map(({ document }) => ({ document, document_version: CONSENT_VERSION })),
      } });
      expect(signup.status()).toBe(201);
      const organizationId = (await signup.json()).organization.id;
      expect((await request.put(`${API_URL}/v1/organizations/${organizationId}/status`, { headers: operatorHeaders, data: { status: "active" } })).status()).toBe(200);
      const tenant = await request.post(`${API_URL}/v1/sessions`, { data: { email: tenantEmail, password: TENANT_PASSWORD } });
      expect(tenant.status()).toBe(201);
      const session = await tenant.json();
      expect(session.organization_role).toBe("owner");
      tenantToken = session.token;
      secrets.add(tenantToken);
      const tenantHeaders = { Authorization: `Bearer ${tenantToken}` };
      const duplicate = await request.post(`${API_URL}/v1/user-accounts`, { headers: operatorHeaders, data: { name: "기존 회원을 바꾸면 안 됨", email: tenantEmail, password: DUPLICATE_PASSWORD } });
      expect(duplicate.status()).toBe(409);
      expect((await duplicate.json()).code).toBe("email_already_exists");
      const retained = await request.post(`${API_URL}/v1/sessions`, { data: { email: tenantEmail, password: TENANT_PASSWORD } });
      expect(retained.status()).toBe(201);
      const retainedSession = await retained.json();
      secrets.add(retainedSession.token);
      expect(retainedSession.user_account.role).toBe("member");
      expect(retainedSession.user_account.name).toBe("도입사 대표관리자");
      expect(retainedSession.organization_role).toBe("owner");
      const body = { name: "생성되면 안 되는 운영자", email: deniedEmail, password: CREATED_PASSWORD };
      expect((await request.post(`${API_URL}/v1/user-accounts`, { headers: tenantHeaders, data: body })).status()).toBe(403);
      const key = await request.post(`${API_URL}/v1/api-keys`, { headers: tenantHeaders, data: { label: "운영자 생성 거절 검증", mode: "test", scopes: ["admin"] } });
      expect(key.status()).toBe(201);
      const issued = await key.json();
      tenantApiKeyId = issued.id;
      secrets.add(issued.secret);
      expect((await request.post(`${API_URL}/v1/user-accounts`, { headers: { Authorization: `Bearer ${issued.secret}` }, data: body })).status()).toBe(403);
      const users = await request.get(`${API_URL}/v1/user-accounts`, { headers: operatorHeaders });
      expect(users.status()).toBe(200);
      expect((await users.json()).user_accounts.some((user: { email: string }) => user.email === deniedEmail)).toBe(false);
    });

    await test.step("운영자 생성은 생성자와 새 계정을 감사로 연결한다", async () => {
      const query = new URLSearchParams({ action: "user_account_created", q: createdId, size: "100" });
      const result = await request.get(`${API_URL}/v1/operations/audit-logs?${query}`, { headers: operatorHeaders });
      expect(result.status()).toBe(200);
      const logs = (await result.json()).audit_logs;
      expect(logs).toHaveLength(1);
      expect(logs[0]).toMatchObject({ action: "user_account_created", actor_type: "user_account", actor_id: operatorId, target_type: "user_account", target_id: createdId, detail: { role: "operator" } });
      expect(logs[0].organization_id ?? null).toBeNull();
      for (const secret of secrets) expect(JSON.stringify(logs)).not.toContain(secret);
      await operator.setViewportSize({ width: 1280, height: 900 });
      await operator.goto(`/admin/audit?${query}`);
      await expect(operator.locator(".audit-summary strong")).toHaveText(["운영자 계정 생성"]);
      await operator.locator(".audit-summary").click();
      await expect(operator.locator(".audit-detail")).toContainText(OPERATOR_EMAIL);
      await expect(operator.locator(".audit-detail")).toContainText(createdId);
    });
  } catch (error) {
    failure = safeFailure(error, secrets);
  } finally {
    if (tenantApiKeyId && tenantToken) await request.delete(`${API_URL}/v1/api-keys/${tenantApiKeyId}`, { headers: { Authorization: `Bearer ${tenantToken}` } }).catch(() => undefined);
    await Promise.allSettled(contexts.map(context => context.close()));
  }
  if (failure) throw failure;
});
