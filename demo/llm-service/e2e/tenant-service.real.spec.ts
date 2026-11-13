import { expect, test, type APIRequestContext, type BrowserContext, type Page } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { writeFileSync } from "node:fs";
import path from "node:path";
import { METRICS, MODELS } from "../src/lib/catalog";
import type { BatchView, ConversationView, GenerationView, TokenUsage, WorkspaceSnapshot } from "../src/lib/contracts";
import {
  APP_ORIGIN, ARTIFACT_DIR, BASE_URL, assertClientAssetsSafe, assertNoSecrets, awaitGeneration, bannerStaysVisible, expectEventMatches, jsonResponse,
  login, meterEvent, noHorizontalOverflow, rememberSession, runtimeAccounts, runtimeEnvironment,
  safeFailure, screenshot, secretValues, session, workspace,
} from "./helpers";

type MeterUsage = { billable_metric_usages: Array<{ code: string; customers: Array<{ customer_id: string; raw_quantity: number; quantity: number }> }> };
type MeterDraft = { customers: Array<{ customer_id: string; amount?: number; supply_amount?: number; lines: Array<{ amount: number }> }> };
type MeterPrices = { billable_metric_prices: Array<{ billable_metric_code: string; price_rates: Array<{ customer_id: string | null; dimension_values: Record<string, unknown>; unit_price: number; effective_from: string; effective_until: string | null }> }> };

async function generate(request: APIRequestContext, body: Record<string, unknown>): Promise<GenerationView> {
  const response = await request.post("/api/generations", { headers: { Origin: APP_ORIGIN }, data: body });
  expect([200, 202].includes(response.status()), "생성 요청 수락").toBe(true);
  const generation = await response.json() as GenerationView;
  expect(generation.id).toBe(String(body.requestId).toLowerCase());
  return generation.state === "completed" ? generation : awaitGeneration(request, generation.id);
}

async function sendChat(page: Page, prompt: string): Promise<{ generation: GenerationView; body: { requestId: string; conversationId: string; surface: string; modelId: string } }> {
  await page.getByRole("textbox", { name: "메시지", exact: true }).fill(prompt);
  const responsePromise = page.waitForResponse(response => new URL(response.url()).pathname === "/api/generations" && response.request().method() === "POST");
  await page.getByRole("button", { name: "보내기", exact: true }).click();
  const response = await responsePromise;
  expect([200, 202].includes(response.status()), "대화 요청 수락").toBe(true);
  const body = response.request().postDataJSON() as { requestId: string; conversationId: string; surface: string; modelId: string; prompt: string };
  expect(body.prompt).toBe(prompt);
  expect(body.surface).toBe("chat");
  let generation = await response.json() as GenerationView;
  if (generation.state !== "completed") generation = await awaitGeneration(page.request, body.requestId);
  await expect(page.getByRole("log", { name: "대화 내용", exact: true })).toContainText(generation.output);
  return { generation, body };
}

function sumUsage(items: GenerationView[]): TokenUsage {
  return items.reduce((total, item) => ({ input: total.input + item.usage.input, output: total.output + item.usage.output, cacheRead: total.cacheRead + item.usage.cacheRead, cacheWrite: total.cacheWrite + item.usage.cacheWrite }), { input: 0, output: 0, cacheRead: 0, cacheWrite: 0 });
}

test("고객 회사의 대화, API, 일괄 처리가 실제 수집과 청구로 이어지고 계정과 서버가 분리된다", async ({ browser, request }) => {
  const environment = runtimeEnvironment();
  const accounts = runtimeAccounts(environment);
  // 몇 건의 시연 요청은 100만 토큰 단가에서 10원 미만이라, 청구 예정액이 0보다 큰 시드 사용량 계정을 쓴다.
  const account = accounts[0];
  const other = accounts.at(-1)!;
  const secrets = secretValues(environment);
  const contexts: BrowserContext[] = [];
  let failure: Error | undefined;
  const evidence: Record<string, unknown> = { accountCount: accounts.length, meterEngine: "real local API", provider: "local-demo" };
  try {
    const context = await browser.newContext({ baseURL: BASE_URL, viewport: { width: 1280, height: 900 } });
    contexts.push(context);
    const page = await context.newPage();
    let directMeterEngineRequest = false;
    let browserAuthorizationHeader = false;
    page.on("request", outgoing => {
      if (outgoing.url().startsWith(environment.METERENGINE_API_BASE_URL)) directMeterEngineRequest = true;
      if (outgoing.headers().authorization) browserAuthorizationHeader = true;
    });
    await page.goto("/");
    const accountChoice = page.getByRole("radio", { name: new RegExp(account.name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")) });
    await expect(accountChoice).toBeVisible();
    await bannerStaysVisible(page);
    await noHorizontalOverflow(page);
    await screenshot(page, "01-entry-desktop.png", secrets);
    assertNoSecrets(await page.content(), secrets);
    // 첫 계정은 기본으로 골라져 있어 check()가 누르지 않으므로, 눌러서 포커스를 옮긴다.
    await accountChoice.click();
    await expect(accountChoice).toBeChecked();
    await page.keyboard.press("Tab");
    await expect(page.getByRole("button", { name: "이 계정으로 시작", exact: true })).toBeFocused();
    await page.keyboard.press("Enter");
    await expect(page.getByRole("textbox", { name: "메시지", exact: true })).toBeVisible();
    await rememberSession(context, secrets);
    const activeSession = await session(context.request);
    expect(activeSession.user?.id).toBe(account.id);
    expect(activeSession.provider.kind).toBe("local-demo");
    expect(activeSession.provider.tokenMethod).toContain("시연");
    assertNoSecrets(activeSession, secrets);
    evidence.clientAssetsChecked = await assertClientAssetsSafe(page, secrets);
    const baseline = await workspace(context.request);
    if (baseline.billing.invoices.some(invoice => invoice.month === baseline.month)) throw new Error("이번 달 인보이스가 이미 확정된 시드입니다. 새 조직으로 다시 초기화하고 그 설정으로 앱을 다시 띄운 뒤 OPENAB_E2E_ENV_FILE을 바꾸세요.");
    const readHeaders = { Authorization: `Bearer ${environment.METERENGINE_API_KEY}` };
    const commonPrices = await jsonResponse<MeterPrices>(await request.get(`${environment.METERENGINE_API_BASE_URL}/v1/billable-metric-prices?month=${baseline.month}`, { headers: readHeaders }));
    const customerPrices = await jsonResponse<MeterPrices>(await request.get(`${environment.METERENGINE_API_BASE_URL}/v1/billable-metric-prices?month=${baseline.month}&customer_id=${account.customerId}`, { headers: readHeaders }));
    expect(baseline.models.map(model => model.id)).toEqual(MODELS.map(model => model.id));
    for (const model of baseline.models) for (const surface of model.surfaces) for (const metric of METRICS) {
      if (model.kind === "embedding" && metric.key !== "input") continue;
      const candidates = [customerPrices, commonPrices].flatMap(prices => prices.billable_metric_prices.find(item => item.billable_metric_code === metric.code)?.price_rates ?? []);
      const rate = candidates.find(candidate => (candidate.customer_id === null || candidate.customer_id === account.customerId) && candidate.dimension_values.model === model.id && candidate.dimension_values.surface === surface && candidate.effective_from <= baseline.month && (candidate.effective_until === null || candidate.effective_until > baseline.month));
      expect(rate, `실제 단가: ${model.id} ${surface} ${metric.code}`).toBeDefined();
      expect(model.prices[surface]?.[metric.key]).toBe(rate!.unit_price);
    }
    expect(baseline.usage.state).toBe("ready");
    expect(baseline.billing.state).toBe("ready");

    // 대화: 두 차례 이어 보내 캐시 읽기와 쓰기를 확인한다.
    await page.getByLabel("모델", { exact: true }).selectOption("ab-5");
    const marker = `고객 원문 검증 ${randomUUID()}`;
    const firstPrompt = `${marker}. 처음 쓰는 고객에게 보낼 안내 메일을 세 문장으로 써 줘.`;
    const first = await sendChat(page, firstPrompt);
    const second = await sendChat(page, "조금 더 짧게 줄여 줘.");
    expect(second.body.conversationId).toBe(first.body.conversationId);
    expect(first.generation.usage.cacheRead).toBeGreaterThan(first.generation.usage.input);
    expect(second.generation.usage.cacheWrite).toBeGreaterThan(0);
    expect(second.generation.usage.cacheRead).toBeGreaterThanOrEqual(first.generation.usage.cacheRead);
    const conversation = await jsonResponse<ConversationView>(await context.request.get(`/api/conversations/${first.body.conversationId}`));
    expect(conversation.messages.map(message => message.id)).toEqual([first.generation.id, second.generation.id]);
    for (const turn of [first.generation, second.generation]) {
      await expect.poll(async () => (await awaitGeneration(context.request, turn.id)).meteringStatus, { message: "실제 수집 전달 확인", timeout: 30_000 }).toBe("delivered");
      expectEventMatches(await meterEvent(request, environment, turn.id), { ...turn, surface: "chat" }, account.customerId);
    }
    const chatEvent = await meterEvent(request, environment, first.generation.id);
    expect(JSON.stringify(chatEvent).includes(marker), "프롬프트가 수집 속성에 없음").toBe(false);
    const replay = await generate(context.request, { requestId: first.body.requestId, surface: "chat", conversationId: first.body.conversationId, modelId: "ab-5", prompt: firstPrompt });
    expect(replay.output).toBe(first.generation.output);
    const conflicting = await context.request.post("/api/generations", { headers: { Origin: APP_ORIGIN }, data: { requestId: first.body.requestId, surface: "chat", conversationId: first.body.conversationId, modelId: "ab-5", prompt: `${firstPrompt} 내용 변경` } });
    expect(conflicting.status()).toBe(409);
    await bannerStaysVisible(page);
    await noHorizontalOverflow(page);
    await screenshot(page, "02-chat-desktop.png", secrets);

    // API 플레이그라운드: 화면에서 보내고 임베딩은 API로 보낸다.
    await page.getByRole("link", { name: "API", exact: true }).click();
    await expect(page).toHaveURL(/\/playground$/);
    await page.getByLabel("모델", { exact: true }).selectOption("ab-5-mini");
    await page.getByRole("textbox", { name: "메시지", exact: true }).fill("환불 문의에 답하는 짧은 안내문을 써 줘.");
    const apiResponse = page.waitForResponse(response => new URL(response.url()).pathname === "/api/generations" && response.request().method() === "POST");
    await page.getByRole("button", { name: "요청 보내기", exact: true }).click();
    const apiGeneration = await awaitGeneration(page.request, ((await apiResponse).request().postDataJSON() as { requestId: string }).requestId);
    expect(apiGeneration.surface).toBe("api");
    await expect(page.getByLabel("응답 JSON", { exact: true })).toContainText("\"output_text\"");
    const embedding = await generate(context.request, { requestId: randomUUID(), surface: "api", modelId: "ab-embed-3", prompt: "검색 색인에 넣을 문장" });
    expect([embedding.usage.output, embedding.usage.cacheRead, embedding.usage.cacheWrite]).toEqual([0, 0, 0]);
    await noHorizontalOverflow(page);
    await screenshot(page, "03-api-desktop.png", secrets);

    // 일괄 처리: 두 건을 넣고 건마다 이벤트를 확인한다.
    await page.getByRole("link", { name: "일괄 처리", exact: true }).click();
    await expect(page).toHaveURL(/\/batch$/);
    await expect(page.getByText("일괄 처리는 단가가 절반이에요", { exact: true })).toBeVisible();
    await page.getByLabel("모델", { exact: true }).selectOption("ab-5-nano");
    await page.getByRole("textbox", { name: "작업 목록", exact: true }).fill("배송이 늦어요.\n영수증을 다시 받고 싶어요.");
    const batchResponse = page.waitForResponse(response => new URL(response.url()).pathname === "/api/batches" && response.request().method() === "POST");
    await page.getByRole("button", { name: "2건 처리 시작", exact: true }).click();
    const batch = await (await batchResponse).json() as BatchView;
    expect(batch.items.map(item => [item.surface, item.state])).toEqual([["batch", "completed"], ["batch", "completed"]]);
    for (const item of batch.items) {
      await expect.poll(async () => (await awaitGeneration(context.request, item.id)).meteringStatus, { timeout: 30_000 }).toBe("delivered");
      expectEventMatches(await meterEvent(request, environment, item.id), item, account.customerId);
    }
    await noHorizontalOverflow(page);
    await screenshot(page, "04-batch-desktop.png", secrets);

    const created = [first.generation, second.generation, apiGeneration, embedding, ...batch.items];
    for (const item of created) await expect.poll(async () => (await awaitGeneration(context.request, item.id)).meteringStatus, { timeout: 30_000 }).toBe("delivered");
    const current = await workspace(context.request, baseline.month);
    const added = sumUsage(created);
    expect(current.user.id).toBe(account.id);
    expect(current.stats.totalRequests).toBe(baseline.stats.totalRequests + created.length);
    for (const key of ["input", "output", "cacheRead", "cacheWrite"] as const) expect(current.stats.usage[key]).toBe(baseline.stats.usage[key] + added[key]);
    expect(current.usage.state).toBe("ready");
    expect(current.billing.state).toBe("ready");
    const upstreamUsage = await jsonResponse<MeterUsage>(await request.get(`${environment.METERENGINE_API_BASE_URL}/v1/usage?month=${baseline.month}`, { headers: readHeaders }));
    for (const metric of current.usage.metrics) {
      const source = upstreamUsage.billable_metric_usages.find(item => item.code === metric.code)?.customers.find(customer => customer.customer_id === account.customerId);
      expect(source).toBeDefined();
      expect(metric.quantity).toBe(source!.quantity);
      expect(metric.rawQuantity).toBe(source!.raw_quantity);
    }
    const upstreamDraft = await jsonResponse<MeterDraft>(await request.get(`${environment.METERENGINE_API_BASE_URL}/v1/invoices/draft?month=${baseline.month}`, { headers: readHeaders }));
    const expectedDraft = upstreamDraft.customers.find(customer => customer.customer_id === account.customerId);
    expect(expectedDraft).toBeDefined();
    const expectedSupply = expectedDraft!.lines.reduce((total, line) => total + line.amount, 0);
    expect(current.billing.draft?.supplyAmount).toBe(expectedSupply);
    expect(expectedSupply).toBeGreaterThan(0);
    expect(current.billing.draft?.totalAmount).toBe(expectedSupply + Math.floor(expectedSupply / 10));
    assertNoSecrets(current, secrets);
    expect(JSON.stringify(current).includes(other.customerId), "다른 고객 내부 ID가 응답에 없음").toBe(false);

    await page.reload();
    expect((await session(context.request)).user?.id).toBe(account.id);
    await page.getByRole("link", { name: "사용량", exact: true }).click();
    await expect(page).toHaveURL(/\/usage(?:\?.*)?$/);
    await expect(page.getByLabel("조회 월", { exact: true })).toBeVisible();
    await bannerStaysVisible(page);
    await noHorizontalOverflow(page);
    await screenshot(page, "05-usage-desktop.png", secrets);
    await page.getByRole("link", { name: "청구 내역", exact: true }).click();
    await expect(page).toHaveURL(/\/billing(?:\?.*)?$/);
    await expect(page.getByText(/예정/).first()).toBeVisible();
    await noHorizontalOverflow(page);
    await screenshot(page, "06-billing-desktop.png", secrets);
    for (const width of [768, 390]) {
      await page.setViewportSize({ width, height: width === 390 ? 844 : 1024 });
      await bannerStaysVisible(page);
      await noHorizontalOverflow(page);
      await screenshot(page, width === 390 ? "07-billing-mobile.png" : "07-billing-tablet.png", secrets);
    }
    await page.goto("/history");
    await expect(page.getByText(firstPrompt, { exact: true }).first()).toBeVisible();
    const historyEntry = page.locator(".history-record").filter({ has: page.getByText(firstPrompt, { exact: true }) }).first();
    await historyEntry.locator("summary").click();
    await expect(historyEntry).toHaveAttribute("open", "");
    await expect(historyEntry.locator(".history-content")).toContainText(first.generation.output);
    await noHorizontalOverflow(page);
    await screenshot(page, "08-history-mobile.png", secrets);
    await page.goto("/");
    const message = page.getByRole("textbox", { name: "메시지", exact: true });
    await expect(message).toBeVisible();
    await page.getByRole("button", { name: "보내기", exact: true }).click();
    await expect(message).toBeFocused();
    await expect(message).toHaveAttribute("aria-invalid", "true");
    await expect(page.getByText("메시지를 1자부터 4,000자까지 입력해 주세요.", { exact: true })).toBeVisible();
    await bannerStaysVisible(page);
    await noHorizontalOverflow(page);
    await screenshot(page, "09-chat-mobile.png", secrets);
    for (const pathname of ["/playground", "/batch"]) {
      await page.goto(pathname);
      await expect(page.locator("main h1").first()).toBeVisible();
      await bannerStaysVisible(page);
      await noHorizontalOverflow(page);
      await screenshot(page, `10-${pathname.slice(1)}-mobile.png`, secrets);
    }
    const previousMonth = `${Number(baseline.month.slice(0, 4)) - 1}-${baseline.month.slice(5)}`;
    await page.goto(`/usage?month=${previousMonth}`);
    await expect(page.getByLabel("조회 월", { exact: true })).toHaveValue(previousMonth);
    const prior = await workspace(context.request, previousMonth);
    expect(prior.month).toBe(previousMonth);
    expect(prior.usage.metrics.every(metric => metric.quantity === 0)).toBe(true);
    await noHorizontalOverflow(page);
    await screenshot(page, "11-usage-empty-mobile.png", secrets);

    const otherContext = await browser.newContext({ baseURL: BASE_URL, viewport: { width: 1280, height: 900 } });
    contexts.push(otherContext);
    await login(otherContext.request, other.id);
    await rememberSession(otherContext, secrets);
    expect((await otherContext.request.get(`/api/requests/${first.generation.id}`)).status()).toBe(404);
    expect((await otherContext.request.get(`/api/conversations/${first.body.conversationId}`)).status()).toBe(404);
    const otherPrompt = `분리된 고객의 비공개 입력 ${randomUUID()}`;
    const otherGeneration = await generate(otherContext.request, { requestId: randomUUID(), surface: "api", modelId: "ab-5-mini", prompt: otherPrompt });
    expect((await context.request.get(`/api/requests/${otherGeneration.id}`)).status()).toBe(404);
    const selected = await context.request.get(`/api/workspace?month=${baseline.month}&customer_id=${encodeURIComponent(other.customerId)}`);
    expect([200, 400].includes(selected.status())).toBe(true);
    if (selected.status() === 200) {
      const own = await selected.json() as WorkspaceSnapshot;
      expect(own.user.id).toBe(account.id);
      expect(own.requests.some(item => item.id === otherGeneration.id)).toBe(false);
      expect(JSON.stringify(own).includes(otherPrompt)).toBe(false);
    }
    expect(directMeterEngineRequest, "브라우저에서 MeterEngine 직접 호출 없음").toBe(false);
    expect(browserAuthorizationHeader, "브라우저에 서버 API 키 헤더 없음").toBe(false);
    assertNoSecrets(await page.content(), secrets);

    const standaloneURL = process.env.OPENAB_E2E_STANDALONE_URL;
    if (standaloneURL) {
      const standaloneAddress = new URL(standaloneURL);
      expect(["localhost", "127.0.0.1", "[::1]"].includes(standaloneAddress.hostname)).toBe(true);
      expect(standaloneAddress.protocol).toBe("http:");
      const standaloneContext = await browser.newContext({ baseURL: standaloneURL, storageState: await context.storageState(), viewport: { width: 1280, height: 900 } });
      contexts.push(standaloneContext);
      expect((await session(standaloneContext.request)).user?.id).toBe(account.id);
      const persistent = await jsonResponse<GenerationView>(await standaloneContext.request.get(`/api/requests/${first.generation.id}`));
      expect(persistent.output).toBe(first.generation.output);
      expect(persistent.meteringStatus).toBe("delivered");
      const standalonePage = await standaloneContext.newPage();
      await standalonePage.goto("/");
      await standalonePage.getByRole("button", { name: new RegExp(conversation.title.slice(0, 20).replace(/[.*+?^${}()|[\]\\]/g, "\\$&")) }).first().click();
      await expect(standalonePage.getByRole("log", { name: "대화 내용", exact: true })).toContainText(first.generation.output);
      await noHorizontalOverflow(standalonePage);
      await screenshot(standalonePage, "12-standalone-desktop.png", secrets);
      evidence.standalone = { origin: standaloneAddress.origin, existingSession: true, existingConversation: true };
    } else {
      evidence.standalone = { checked: false, reason: "OPENAB_E2E_STANDALONE_URL 미지정" };
    }
    if (!environment.METERENGINE_BILLING_API_KEY) throw new Error("청구서 확정 검증용 관리 API 키가 없습니다.");
    const finalize = await jsonResponse<{ finalized_count: number }>(await request.post(`${environment.METERENGINE_API_BASE_URL}/v1/invoices?month=${baseline.month}`, { headers: { Authorization: `Bearer ${environment.METERENGINE_BILLING_API_KEY}` } }));
    expect(finalize.finalized_count).toBeGreaterThan(0);
    const finalized = await workspace(context.request, baseline.month);
    const ownInvoice = finalized.billing.invoices.find(invoice => invoice.month === baseline.month);
    expect(ownInvoice).toBeDefined();
    expect(ownInvoice!.supplyAmount).toBe(expectedSupply);
    expect(ownInvoice!.totalAmount).toBe(current.billing.draft!.totalAmount);
    expect(ownInvoice!.paidAmount).toBe(0);
    expect(ownInvoice!.canceledAmount).toBe(0);
    expect(ownInvoice!.amountDue).toBe(ownInvoice!.totalAmount);
    expect(ownInvoice!.status).toBe("unpaid");
    expect(ownInvoice!.lines.every(line => line.quantityNumerator != null && line.quantityDenominator != null)).toBe(true);
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto("/billing");
    await expect(page.getByRole("heading", { name: `${baseline.month} 청구서`, exact: true })).toBeVisible();
    await expect(page.getByText("납부 대기", { exact: true })).toBeVisible();
    await page.locator(".invoice-record").first().scrollIntoViewIfNeeded();
    await screenshot(page, "13-invoice-desktop.png", secrets);
    await page.setViewportSize({ width: 390, height: 844 });
    await page.locator(".invoice-record").first().scrollIntoViewIfNeeded();
    await noHorizontalOverflow(page);
    await screenshot(page, "14-invoice-mobile.png", secrets);
    const repeat = await jsonResponse<{ finalized_count: number }>(await request.post(`${environment.METERENGINE_API_BASE_URL}/v1/invoices?month=${baseline.month}`, { headers: { Authorization: `Bearer ${environment.METERENGINE_BILLING_API_KEY}` } }));
    expect(repeat.finalized_count).toBe(0);
    evidence.requests = created.map(item => ({ id: item.id, surface: item.surface, model: item.modelId, usage: item.usage }));
    evidence.invoice = { id: ownInvoice!.id, supplyAmount: expectedSupply, totalAmount: ownInvoice!.totalAmount, repeatedFinalizationCount: repeat.finalized_count };
    writeFileSync(path.join(ARTIFACT_DIR, "real-flow-evidence.json"), JSON.stringify(evidence, null, 2) + "\n");
  } catch (error) {
    failure = safeFailure(error, secrets);
  } finally {
    for (const context of contexts) await context.close();
  }
  if (failure) throw failure;
});

test("서비스의 세션과 요청 경계가 위조와 교차 출처를 거절한다", async ({ browser }) => {
  const environment = runtimeEnvironment();
  const accounts = runtimeAccounts(environment);
  const secrets = secretValues(environment);
  const contexts: BrowserContext[] = [];
  let failure: Error | undefined;
  try {
    const context = await browser.newContext({ baseURL: BASE_URL });
    contexts.push(context);
    expect((await context.request.get("/api/workspace")).status()).toBe(401);
    expect((await context.request.get("/api/requests")).status()).toBe(401);
    expect((await context.request.get("/api/conversations")).status()).toBe(401);
    expect((await context.request.get("/api/session", { headers: { Host: "untrusted.invalid" } })).status()).toBe(403);
    expect((await context.request.get("/api/session", { headers: { Host: "untrusted.invalid", "X-Forwarded-Host": new URL(BASE_URL).host } })).status()).toBe(403);
    expect((await context.request.post("/api/session", { headers: { Origin: "https://untrusted.invalid" }, data: { accountId: accounts[0].id } })).status()).toBe(403);
    await login(context.request, accounts[0].id);
    await rememberSession(context, secrets);
    const cookies = await context.cookies();
    const cookie = cookies.find(item => /session/.test(item.name));
    expect(cookie).toBeDefined();
    expect(cookie!.httpOnly).toBe(true);
    expect(cookie!.sameSite).toBe("Lax");
    const candidate = { requestId: randomUUID(), surface: "api", modelId: "ab-5", prompt: "허용되지 않은 요청" };
    expect((await context.request.post("/api/generations", { headers: { Origin: "https://untrusted.invalid" }, data: candidate })).status()).toBe(403);
    expect((await context.request.post("/api/generations", { data: candidate })).status()).toBe(403);
    expect((await context.request.post("/api/batches", { headers: { Origin: "https://untrusted.invalid" }, data: { batchId: randomUUID(), modelId: "ab-5", items: [] } })).status()).toBe(403);
    expect((await context.request.post("/api/generations", { headers: { Origin: APP_ORIGIN, "Content-Type": "text/plain" }, data: JSON.stringify(candidate) })).status()).toBe(400);
    expect((await context.request.post("/api/generations", { headers: { Origin: APP_ORIGIN, "Content-Type": "application/json" }, data: "{" })).status()).toBe(400);
    for (const invalid of [{ ...candidate, modelId: "unsupported-model" }, { ...candidate, requestId: "not-a-uuid" }, { ...candidate, prompt: " " }, { ...candidate, prompt: "긴".repeat(4001) }, { ...candidate, surface: "chat" }, { ...candidate, surface: "chat", conversationId: randomUUID(), modelId: "ab-embed-3" }]) {
      expect((await context.request.post("/api/generations", { headers: { Origin: APP_ORIGIN }, data: invalid })).status()).toBe(400);
    }
    expect((await context.request.post("/api/batches", { headers: { Origin: APP_ORIGIN }, data: { batchId: randomUUID(), modelId: "ab-5", items: Array.from({ length: 21 }, () => ({ requestId: randomUUID(), prompt: "x" })) } })).status()).toBe(400);
    expect((await context.request.get("/api/workspace?month=2026-13")).status()).toBe(400);
    const forged = await browser.newContext({ baseURL: BASE_URL });
    contexts.push(forged);
    await forged.addCookies([{ ...cookie!, value: "0".repeat(64) }]);
    expect((await forged.request.get("/api/workspace")).status()).toBe(401);
    const logout = await context.request.delete("/api/session", { headers: { Origin: APP_ORIGIN } });
    expect([200, 204].includes(logout.status())).toBe(true);
    expect((await context.request.get("/api/workspace")).status()).toBe(401);
    expect((await session(context.request)).user).toBeNull();
  } catch (error) {
    failure = safeFailure(error, secrets);
  } finally {
    for (const context of contexts) await context.close();
  }
  if (failure) throw failure;
});
