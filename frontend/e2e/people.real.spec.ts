import { expect, test, type BrowserContext, type Page } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { CONSENT_DOCUMENTS, CONSENT_VERSION } from "../src/lib/auth/consents";

const API_URL = process.env.S1_API_URL ?? "http://127.0.0.1:38147";
const FRONTEND_URL = "http://localhost:33147";
const PASSWORD = "local-people-e2e-password";
const OPERATOR_EMAIL = process.env.E2E_OPERATOR_EMAIL ?? "operator@fe-e2e.local";
const OPERATOR_PASSWORD = process.env.E2E_OPERATOR_PASSWORD ?? "local-operator-e2e-password";

test.use({ trace: "off", screenshot: "off", video: "off" });

let previousCopyPrompt: string | undefined;
test.beforeAll(() => {
  previousCopyPrompt = process.env.PLAYWRIGHT_NO_COPY_PROMPT;
  // 실패 시 자동 DOM 저장에도 일회용 초대 링크를 남기지 않는다.
  process.env.PLAYWRIGHT_NO_COPY_PROMPT = "1";
});
test.afterAll(() => {
  if (previousCopyPrompt === undefined) delete process.env.PLAYWRIGHT_NO_COPY_PROMPT;
  else process.env.PLAYWRIGHT_NO_COPY_PROMPT = previousCopyPrompt;
});

async function login(page: Page, email: string, returnTo = "/members") {
  await page.goto(`/login?returnTo=${encodeURIComponent(returnTo)}`);
  await page.getByLabel("이메일", { exact: true }).fill(email);
  await page.getByLabel("비밀번호", { exact: true }).fill(PASSWORD);
  await page.getByRole("button", { name: "로그인", exact: true }).click();
}

async function agree(page: Page) {
  await page.getByRole("checkbox", { name: /^이용 안내/ }).check();
  await page.getByRole("checkbox", { name: /^개인정보 처리 안내/ }).check();
}

async function expectNoPeopleActions(page: Page) {
  await expect(page.getByRole("heading", { name: "사용자 관리", exact: true })).toBeVisible();
  for (const name of ["계정 직접 생성", "초대 링크 만들기", "권한 변경", "역할 변경", "소속 보관", "초대 취소"]) {
    await expect(page.getByRole("button", { name, exact: true })).toHaveCount(0);
  }
  await expect(page.getByText("사용자 추가와 권한 변경은 대표관리자에게 요청하세요.", { exact: true })).toBeVisible();
}

function safeFailure(error: unknown, secrets: Set<string>) {
  let message = error instanceof Error ? error.message : "사람 관리 E2E 검증을 마치지 못했습니다.";
  for (const secret of secrets) if (secret) message = message.split(secret).join("[redacted]");
  return new Error(message.replace(/me_invite_[A-Za-z0-9]+/g, "[redacted invitation]"));
}

test("직접 생성과 초대, 본인 동의와 권한, 감사 조회와 고객 복원이 실제 화면으로 이어진다", async ({ browser, request }) => {
  const suffix = randomUUID();
  const ownerEmail = `people-owner-${suffix}@fe-e2e.local`;
  const adminEmail = `people-admin-${suffix}@fe-e2e.local`;
  const invitedEmail = `people-invited-${suffix}@fe-e2e.local`;
  const organizationName = `사람 관리 E2E ${suffix}`;
  const customerName = `복원 확인 고객 ${suffix}`;
  const secrets = new Set([PASSWORD, OPERATOR_PASSWORD]);
  const contexts: BrowserContext[] = [];
  let failure: unknown;

  async function newPage() {
    const context = await browser.newContext({ baseURL: FRONTEND_URL, viewport: { width: 1280, height: 900 } });
    contexts.push(context);
    return context.newPage();
  }

  try {
    await test.step("새 도입사와 대표관리자 준비", async () => {
      const signup = await request.post(`${API_URL}/v1/signups`, { data: {
        organization_name: organizationName, name: "대표관리자 E2E", email: ownerEmail, password: PASSWORD,
        consents: CONSENT_DOCUMENTS.map(({ document }) => ({ document, document_version: CONSENT_VERSION })),
      } });
      expect(signup.status()).toBe(201);
      const { organization } = await signup.json();
      const operator = await request.post(`${API_URL}/v1/sessions`, { data: { email: OPERATOR_EMAIL, password: OPERATOR_PASSWORD } });
      expect(operator.status()).toBe(201);
      const { token } = await operator.json();
      if (typeof token !== "string" || !token) throw new Error("운영자 검증 세션을 받지 못했습니다.");
      secrets.add(token);
      const approval = await request.put(`${API_URL}/v1/organizations/${organization.id}/status`, {
        headers: { Authorization: `Bearer ${token}` }, data: { status: "active" },
      });
      expect(approval.status()).toBe(200);
    });

    const owner = await newPage();
    await test.step("대표관리자가 관리자 계정 직접 생성", async () => {
      await login(owner, ownerEmail);
      await expect(owner).toHaveURL(/\/members(?:\?.*)?$/);
      await owner.getByRole("button", { name: "계정 직접 생성", exact: true }).click();
      const dialog = owner.getByRole("dialog");
      await dialog.getByLabel(/^사용자 이메일/).fill(adminEmail);
      await dialog.getByLabel(/^이름/).fill("직접 생성 관리자");
      await dialog.getByLabel(/^초기 비밀번호/).fill(PASSWORD);
      await dialog.getByRole("checkbox", { name: /전체 관리 \(admin\)/ }).check();
      await dialog.getByRole("button", { name: "사용자 계정 생성", exact: true }).click();
      await expect(dialog).toHaveCount(0);
      await expect(owner.getByRole("row").filter({ hasText: adminEmail })).toContainText("관리자");
    });

    await test.step("첫 로그인 본인 동의와 관리자 행동 제한", async () => {
      const admin = await newPage();
      await login(admin, adminEmail);
      await expect(admin).toHaveURL(/\/account\/consents(?:\?|$)/);
      await expect(admin.getByRole("heading", { name: "계정 이용 안내 확인", exact: true })).toBeVisible();
      await admin.goto("/customers");
      await expect(admin).toHaveURL(/\/account\/consents(?:\?|$)/);
      const returnTo = new URL(admin.url()).searchParams.get("returnTo");
      expect(returnTo).not.toBeNull();
      const destination = new URL(returnTo!, FRONTEND_URL);
      expect(destination.pathname).toBe("/customers");
      const expectedOrganizationId = destination.searchParams.get("organization_id");
      expect(expectedOrganizationId).toMatch(/^[0-9a-f-]{36}$/);
      await agree(admin);
      await admin.getByRole("button", { name: "동의하고 업무 시작", exact: true }).click();
      await expect(admin).toHaveURL(url => url.pathname === "/customers" && url.searchParams.get("organization_id") === expectedOrganizationId);
      await admin.goto("/members");
      await expectNoPeopleActions(admin);
    });

    await test.step("대표관리자 초대 링크와 새 계정 수락", async () => {
      await owner.getByRole("button", { name: "초대 링크 만들기", exact: true }).click();
      const dialog = owner.getByRole("dialog");
      await dialog.getByLabel(/^초대할 이메일/).fill(invitedEmail);
      await dialog.getByRole("checkbox", { name: /전체 관리 \(admin\)/ }).check();
      await dialog.getByRole("button", { name: "초대 링크 발급", exact: true }).click();
      const link = dialog.getByRole("link", { name: "초대 링크 열기", exact: true });
      await expect(link).toBeVisible();
      const invitationPath = await link.getAttribute("href");
      if (!invitationPath || !/^\/organization-invitations\/me_invite_[A-Za-z0-9]{32}$/.test(invitationPath)) {
        throw new Error("로컬 초대 경로를 받지 못했습니다.");
      }
      secrets.add(invitationPath);
      secrets.add(invitationPath.split("/").pop()!);
      await dialog.getByRole("button", { name: "닫기", exact: true }).last().click();
      const invited = await newPage();
      await invited.goto(invitationPath);
      await expect(invited.getByRole("heading", { name: `${organizationName}에 참여`, exact: true })).toBeVisible();
      await invited.getByLabel(/^이름/).fill("초대 수락 관리자");
      await invited.getByLabel(/^비밀번호/).fill(PASSWORD);
      await agree(invited);
      await invited.getByRole("button", { name: "초대 수락하고 도입사로 이동", exact: true }).click();
      await expect(invited).toHaveURL(/\/overview(?:\?.*)?$/);
      await expect(invited.locator(".organization-link")).toContainText(organizationName);
      await owner.reload();
      const invitations = owner.locator("section.workspace-panel").filter({ has: owner.getByRole("heading", { name: "초대 이력", exact: true }) });
      await expect(invitations.getByRole("row").filter({ hasText: invitedEmail })).toContainText("수락 완료");
    });

    await test.step("보관한 고객을 목록에서 찾아 복원", async () => {
      await owner.goto("/customers");
      await owner.getByRole("button", { name: "고객 등록", exact: true }).click();
      let dialog = owner.getByRole("dialog");
      await dialog.getByLabel(/^고객명/).fill(customerName);
      await dialog.getByLabel(/^외부 고객 ID/).fill(`people-customer-${suffix}`);
      await dialog.getByRole("button", { name: "고객 저장", exact: true }).click();
      await expect(dialog).toHaveCount(0);
      await owner.getByRole("link", { name: customerName, exact: true }).click();
      await owner.getByRole("button", { name: "고객 보관", exact: true }).click();
      dialog = owner.getByRole("dialog");
      await dialog.getByRole("button", { name: "고객 보관", exact: true }).click();
      await expect(dialog).toHaveCount(0);
      await expect(owner).toHaveURL(url => url.pathname === "/customers" && url.searchParams.get("status") === "archived");
      await expect(owner.getByRole("link", { name: customerName, exact: true })).toBeVisible();
      const status = owner.getByRole("combobox", { name: "관리 상태", exact: true });
      await status.selectOption("active");
      await owner.getByRole("button", { name: "조건 적용", exact: true }).click();
      await expect(owner.getByRole("link", { name: customerName, exact: true })).toHaveCount(0);
      await status.selectOption("archived");
      await owner.getByRole("button", { name: "조건 적용", exact: true }).click();
      await owner.getByRole("link", { name: customerName, exact: true }).click();
      await expect(owner.getByText("보관된 고객입니다.", { exact: false })).toBeVisible();
      await owner.getByRole("button", { name: "고객 복원", exact: true }).click();
      dialog = owner.getByRole("dialog");
      await dialog.getByRole("button", { name: "고객 복원", exact: true }).click();
      await expect(dialog).toHaveCount(0);
      await expect(owner.getByText("보관된 고객입니다.", { exact: false })).toHaveCount(0);
      await owner.goto("/customers");
      await expect(owner.getByRole("link", { name: customerName, exact: true })).toBeVisible();
    });

    await test.step("감사 행동 필터와 API 경로 검색", async () => {
      await owner.goto("/audit");
      await owner.getByLabel("행동", { exact: true }).selectOption("organization_invitation_accepted");
      await owner.getByRole("button", { name: "조건 적용", exact: true }).click();
      await expect(owner.locator(".audit-summary strong")).toHaveText(["초대 수락"]);
      await owner.locator(".audit-summary").click();
      await expect(owner.locator(".audit-detail")).toContainText(invitedEmail);
      await owner.getByLabel("행동", { exact: true }).selectOption("api_request_completed");
      await owner.getByLabel("행동 또는 대상 검색", { exact: true }).fill("/v1/organization/members");
      await owner.getByRole("button", { name: "조건 적용", exact: true }).click();
      await expect(owner.locator(".audit-summary strong").first()).toHaveText("API 요청");
      await owner.locator(".audit-summary").first().click();
      const detail = owner.locator(".audit-detail").first();
      await expect(detail.getByText("api_request_completed", { exact: true })).toBeVisible();
      await expect(detail.locator("pre")).toContainText('"route": "/v1/organization/members"');
      await expect(detail.locator("pre")).toContainText('"method": "GET"');
    });

    await test.step("모바일 사용자 목록과 감사, 고객 목록 너비", async () => {
      await owner.setViewportSize({ width: 390, height: 844 });
      for (const route of ["/members", "/audit?action=api_request_completed&q=%2Fv1%2Forganization%2Fmembers", "/customers"]) {
        await owner.goto(route);
        await expect(owner.getByRole("main")).toBeVisible();
        await expect(owner.getByText("데이터를 불러오지 못했습니다", { exact: true })).toHaveCount(0);
        expect(await owner.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
      }
      await expect(owner.locator(".workspace-mobile-records").getByRole("link", { name: customerName, exact: true })).toBeVisible();
    });
  } catch (error) {
    failure = safeFailure(error, secrets);
  } finally {
    await Promise.allSettled(contexts.map(context => context.close()));
  }
  if (failure) throw failure;
});
