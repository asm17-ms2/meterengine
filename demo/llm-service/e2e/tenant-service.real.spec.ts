import { expect, test, type APIRequestContext, type BrowserContext, type Page } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { writeFileSync } from "node:fs";
import path from "node:path";
import type { GenerationView, WorkspaceSnapshot } from "../src/lib/contracts";
import {
  APP_ORIGIN, ARTIFACT_DIR, BASE_URL, assertClientAssetsSafe, assertNoSecrets, awaitGeneration, jsonResponse,
  login, noHorizontalOverflow, rememberSession, runtimeAccounts, runtimeEnvironment,
  safeFailure, screenshot, secretValues, session, workspace,
} from "./helpers";

type MeterEvent = { transaction_id: string; properties: Record<string, unknown>; customer_id: string };
type MeterEvents = { events: MeterEvent[] };
type MeterUsage = { billable_metric_usages: Array<{ code: string; customers: Array<{ customer_id: string; raw_quantity: number; quantity: number }> }> };
type MeterDraft = { customers: Array<{ customer_id: string; amount?: number; supply_amount?: number; lines: Array<{ amount: number }> }> };
type MeterPrices = { billable_metric_prices: Array<{ billable_metric_code: string; price_rates: Array<{ customer_id: string | null; dimension_values: Record<string, unknown>; unit_price: number; effective_from: string; effective_until: string | null }> }> };

async function meterEvents(request: APIRequestContext, address: string, apiKey: string, customerId: string, requestId: string): Promise<MeterEvent[]> {
  const parameters = new URLSearchParams({ customer_id: customerId, type: "llm_generation", size: "100" });
  const body = await jsonResponse<MeterEvents>(await request.get(`${address}/v1/events?${parameters}`, { headers: { Authorization: `Bearer ${apiKey}` } }));
  return body.events.filter(event => event.transaction_id.endsWith(`:${requestId.toLowerCase()}`));
}

async function generate(request: APIRequestContext, prompt: string, requestId: string = randomUUID()): Promise<GenerationView> {
  const response = await request.post("/api/generations", { headers: { Origin: APP_ORIGIN }, data: { requestId, modelId: "lumen-standard", prompt } });
  expect([200, 202].includes(response.status()), "생성 요청 수락").toBe(true);
  const generation = await response.json() as GenerationView;
  expect(generation.id).toBe(requestId);
  return generation.state === "completed" ? generation : awaitGeneration(request, requestId);
}

async function submitFromPlayground(page: Page, prompt: string): Promise<{ generation: GenerationView; requestId: string }> {
  await page.getByLabel("모델", { exact: true }).selectOption("lumen-standard");
  await page.getByRole("textbox", { name: "메시지", exact: true }).fill(prompt);
  const responsePromise = page.waitForResponse(response => new URL(response.url()).pathname === "/api/generations" && response.request().method() === "POST");
  await page.getByRole("button", { name: /^(응답 생성|다시 생성)$/ }).click();
  const response = await responsePromise;
  expect([200, 202].includes(response.status()), "화면 생성 요청 수락").toBe(true);
  const submitted = response.request().postDataJSON() as { requestId: string; modelId: string; prompt: string };
  expect(submitted.prompt).toBe(prompt);
  expect(submitted.modelId).toBe("lumen-standard");
  let generation = await response.json() as GenerationView;
  expect(generation.id).toBe(submitted.requestId);
  if (generation.state !== "completed") generation = await awaitGeneration(page.request, submitted.requestId);
  await expect(page.getByRole("region", { name: "모델 응답", exact: true })).toContainText(generation.output);
  return { generation, requestId: submitted.requestId };
}

test("도입사 고객의 생성, 실제 수집과 청구, 계정 격리와 독립 서버 조회가 이어진다", async ({ browser, request }) => {
  const environment = runtimeEnvironment();
  const accounts = runtimeAccounts(environment);
  const account = accounts.at(-1)!;
  const other = accounts.at(-2)!;
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
    await expect(page.getByLabel("체험 계정", { exact: true })).toBeVisible();
    await noHorizontalOverflow(page);
    await screenshot(page, "01-entry-desktop.png", secrets);
    assertNoSecrets(await page.content(), secrets);
    await page.getByLabel("체험 계정", { exact: true }).selectOption(account.id);
    await page.getByLabel("체험 계정", { exact: true }).focus();
    await page.keyboard.press("Tab");
    await expect(page.getByRole("button", { name: "체험 시작", exact: true })).toBeFocused();
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
    if (baseline.billing.invoices.some(invoice => invoice.month === baseline.month)) throw new Error("이번 달 인보이스가 이미 확정된 시드입니다. seed-demo.ts를 새 출력 경로로 다시 실행하고 그 설정으로 앱을 다시 띄운 뒤 LUMEN_E2E_ENV_FILE을 바꾸세요.");
    const readHeaders = { Authorization: `Bearer ${environment.METERENGINE_API_KEY}` };
    const commonPrices = await jsonResponse<MeterPrices>(await request.get(`${environment.METERENGINE_API_BASE_URL}/v1/billable-metric-prices?month=${baseline.month}`, { headers: readHeaders }));
    const customerPrices = await jsonResponse<MeterPrices>(await request.get(`${environment.METERENGINE_API_BASE_URL}/v1/billable-metric-prices?month=${baseline.month}&customer_id=${account.customerId}`, { headers: readHeaders }));
    const priceFields = [["llm-input-tokens", "inputPricePerThousand"], ["llm-output-tokens", "outputPricePerThousand"], ["llm-requests", "requestPrice"]] as const;
    expect(baseline.models).toHaveLength(2);
    for (const model of baseline.models) {
      for (const [metricCode, field] of priceFields) {
        const candidates = [customerPrices, commonPrices].flatMap(prices => prices.billable_metric_prices.find(metric => metric.billable_metric_code === metricCode)?.price_rates ?? []);
        const rate = candidates.find(candidate => (candidate.customer_id === null || candidate.customer_id === account.customerId) && candidate.dimension_values.model === model.id && candidate.effective_from <= baseline.month && (candidate.effective_until === null || candidate.effective_until > baseline.month));
        expect(rate, "실제 단가가 등록된 모델").toBeDefined();
        expect(model[field]).toBe(rate!.unit_price);
        expect(model[field]).toBeGreaterThan(0);
      }
    }
    const marker = `고객 원문 검증 ${randomUUID()}`;
    const prompt = `${marker}. 작은 팀이 처음 사용하는 LLM API 서비스의 안내 문구를 세 문장으로 작성해 주세요.`;
    const { generation, requestId } = await submitFromPlayground(page, prompt);
    expect(generation.state).toBe("completed");
    expect(generation.inputTokens).toBeGreaterThan(0);
    expect(generation.outputTokens).toBeGreaterThan(0);
    expect(generation.totalTokens).toBe(generation.inputTokens + generation.outputTokens);
    await expect.poll(async () => (await awaitGeneration(context.request, requestId)).meteringStatus, { message: "실제 수집 전달 확인", timeout: 30_000 }).toBe("delivered");
    await expect.poll(async () => (await meterEvents(request, environment.METERENGINE_API_BASE_URL, environment.METERENGINE_API_KEY, account.customerId, requestId)).length, { message: "단일 원본 수집", timeout: 20_000 }).toBe(1);
    const [event] = await meterEvents(request, environment.METERENGINE_API_BASE_URL, environment.METERENGINE_API_KEY, account.customerId, requestId);
    expect(event.customer_id).toBe(account.customerId);
    expect(event.properties.input_tokens).toBe(generation.inputTokens);
    expect(event.properties.output_tokens).toBe(generation.outputTokens);
    expect(event.properties.model).toBe("lumen-standard");
    expect(JSON.stringify(event.properties).includes(marker), "프롬프트가 수집 속성에 없음").toBe(false);
    expect(Object.hasOwn(event.properties, "prompt")).toBe(false);
    expect(Object.hasOwn(event.properties, "output")).toBe(false);
    const replay = await generate(context.request, prompt, requestId);
    expect(replay.output).toBe(generation.output);
    const conflicting = await context.request.post("/api/generations", { headers: { Origin: APP_ORIGIN }, data: { requestId, modelId: "lumen-standard", prompt: `${prompt} 내용 변경` } });
    expect(conflicting.status()).toBe(409);
    expect((await meterEvents(request, environment.METERENGINE_API_BASE_URL, environment.METERENGINE_API_KEY, account.customerId, requestId))).toHaveLength(1);
    const current = await workspace(context.request, baseline.month);
    expect(current.user.id).toBe(account.id);
    expect(current.stats.totalRequests).toBe(baseline.stats.totalRequests + 1);
    expect(current.stats.inputTokens).toBe(baseline.stats.inputTokens + generation.inputTokens);
    expect(current.stats.outputTokens).toBe(baseline.stats.outputTokens + generation.outputTokens);
    expect(current.usage.state).toBe("ready");
    expect(current.billing.state).toBe("ready");
    const upstreamUsage = await jsonResponse<MeterUsage>(await request.get(`${environment.METERENGINE_API_BASE_URL}/v1/usage?month=${baseline.month}`, { headers: { Authorization: `Bearer ${environment.METERENGINE_API_KEY}` } }));
    for (const metric of current.usage.metrics) {
      const source = upstreamUsage.billable_metric_usages.find(item => item.code === metric.code)?.customers.find(customer => customer.customer_id === account.customerId);
      expect(source).toBeDefined();
      expect(metric.quantity).toBe(source!.quantity);
      expect(metric.rawQuantity).toBe(source!.raw_quantity);
    }
    const upstreamDraft = await jsonResponse<MeterDraft>(await request.get(`${environment.METERENGINE_API_BASE_URL}/v1/invoices/draft?month=${baseline.month}`, { headers: { Authorization: `Bearer ${environment.METERENGINE_API_KEY}` } }));
    const expectedDraft = upstreamDraft.customers.find(customer => customer.customer_id === account.customerId);
    expect(expectedDraft).toBeDefined();
    const expectedSupply = expectedDraft!.lines.reduce((total, line) => total + line.amount, 0);
    expect(current.billing.draft?.supplyAmount).toBe(expectedSupply);
    expect(expectedSupply).toBeGreaterThan(0);
    expect(current.billing.draft?.totalAmount).toBe(expectedSupply + Math.floor(expectedSupply / 10));
    assertNoSecrets(current, secrets);
    expect(JSON.stringify(current).includes(other.customerId), "다른 고객 내부 ID가 응답에 없음").toBe(false);
    await noHorizontalOverflow(page);
    await screenshot(page, "02-playground-desktop.png", secrets);
    await page.reload();
    await expect(page.getByRole("textbox", { name: "메시지", exact: true })).toBeVisible();
    expect((await session(context.request)).user?.id).toBe(account.id);
    expect((await jsonResponse<GenerationView>(await context.request.get(`/api/requests/${requestId}`))).output).toBe(generation.output);
    await page.getByRole("link", { name: "사용량", exact: true }).click();
    await expect(page).toHaveURL(/\/usage(?:\?.*)?$/);
    await expect(page.getByLabel("조회 월", { exact: true })).toBeVisible();
    await noHorizontalOverflow(page);
    await screenshot(page, "03-usage-desktop.png", secrets);
    await page.getByRole("link", { name: "청구 내역", exact: true }).click();
    await expect(page).toHaveURL(/\/billing(?:\?.*)?$/);
    await expect(page.getByLabel("조회 월", { exact: true })).toBeVisible();
    await expect(page.getByText(/예정|미확정/).first()).toBeVisible();
    await noHorizontalOverflow(page);
    await screenshot(page, "04-billing-desktop.png", secrets);
    for (const width of [768, 390]) {
      await page.setViewportSize({ width, height: width === 390 ? 844 : 1024 });
      await noHorizontalOverflow(page);
      await screenshot(page, width === 390 ? "05-billing-mobile.png" : "05-billing-tablet.png", secrets);
    }
    await page.goto("/history");
    await expect(page.getByText(prompt, { exact: true }).first()).toBeVisible();
    const historyEntry = page.locator(".history-record").filter({ has: page.getByText(prompt, { exact: true }) }).first();
    await expect(historyEntry.getByText("내용 보기", { exact: true })).toBeVisible();
    await historyEntry.locator("summary").click();
    await expect(historyEntry).toHaveAttribute("open", "");
    await expect(historyEntry.getByText("내용 접기", { exact: true })).toBeVisible();
    await expect(historyEntry.locator(".history-content")).toContainText(generation.output);
    await noHorizontalOverflow(page);
    await screenshot(page, "06-history-mobile.png", secrets);
    await page.goto("/");
    const message = page.getByRole("textbox", { name: "메시지", exact: true });
    await expect(message).toBeVisible();
    await page.getByRole("button", { name: "응답 생성", exact: true }).click();
    await expect(message).toBeFocused();
    await expect(message).toHaveAttribute("aria-invalid", "true");
    await expect(page.getByText("메시지는 1자부터 4,000자까지 입력하세요.", { exact: true })).toBeVisible();
    await screenshot(page, "07-validation-mobile.png", secrets);
    await message.fill("긴 입력 확인용 ".repeat(160));
    await message.focus();
    await expect(message).toBeFocused();
    await noHorizontalOverflow(page);
    await screenshot(page, "07-playground-mobile.png", secrets);
    await message.fill("");
    const previousMonth = `${Number(baseline.month.slice(0, 4)) - 1}-${baseline.month.slice(5)}`;
    await page.goto(`/usage?month=${previousMonth}`);
    await expect(page.getByLabel("조회 월", { exact: true })).toHaveValue(previousMonth);
    const prior = await workspace(context.request, previousMonth);
    expect(prior.month).toBe(previousMonth);
    expect(prior.usage.metrics.every(metric => metric.quantity === 0)).toBe(true);
    await noHorizontalOverflow(page);
    await screenshot(page, "08-usage-empty-mobile.png", secrets);

    const otherContext = await browser.newContext({ baseURL: BASE_URL, viewport: { width: 1280, height: 900 } });
    contexts.push(otherContext);
    await login(otherContext.request, other.id);
    await rememberSession(otherContext, secrets);
    expect((await otherContext.request.get(`/api/requests/${requestId}`)).status()).toBe(404);
    const otherPrompt = `분리된 고객의 비공개 시연 입력 ${randomUUID()}`;
    const otherGeneration = await generate(otherContext.request, otherPrompt);
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

    const standaloneURL = process.env.LUMEN_E2E_STANDALONE_URL;
    if (standaloneURL) {
      const standaloneAddress = new URL(standaloneURL);
      expect(["localhost", "127.0.0.1", "[::1]"].includes(standaloneAddress.hostname)).toBe(true);
      expect(standaloneAddress.protocol).toBe("http:");
      const standaloneContext = await browser.newContext({ baseURL: standaloneURL, storageState: await context.storageState(), viewport: { width: 1280, height: 900 } });
      contexts.push(standaloneContext);
      expect((await session(standaloneContext.request)).user?.id).toBe(account.id);
      const persistent = await jsonResponse<GenerationView>(await standaloneContext.request.get(`/api/requests/${requestId}`));
      expect(persistent.output).toBe(generation.output);
      expect(persistent.meteringStatus).toBe("delivered");
      const standalonePage = await standaloneContext.newPage();
      await standalonePage.goto("/");
      await expect(standalonePage.getByRole("textbox", { name: "메시지", exact: true })).toBeVisible();
      await standalonePage.getByRole("button", { name: `요청 보기: ${prompt.slice(0, 48)}`, exact: true }).click();
      await expect(standalonePage.getByRole("region", { name: "모델 응답", exact: true })).toContainText(generation.output);
      await noHorizontalOverflow(standalonePage);
      await screenshot(standalonePage, "09-standalone-desktop.png", secrets);
      evidence.standalone = { origin: standaloneAddress.origin, existingSession: true, existingGeneration: true };
    } else {
      evidence.standalone = { checked: false, reason: "LUMEN_E2E_STANDALONE_URL 미지정" };
    }
    if (!environment.METERENGINE_BILLING_API_KEY) throw new Error("전용 tenant 확정 검증용 관리 API 키가 없습니다.");
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
    await screenshot(page, "10-invoice-desktop.png", secrets);
    await page.setViewportSize({ width: 390, height: 844 });
    await page.locator(".invoice-record").first().scrollIntoViewIfNeeded();
    await noHorizontalOverflow(page);
    await screenshot(page, "11-invoice-mobile.png", secrets);
    const repeat = await jsonResponse<{ finalized_count: number }>(await request.post(`${environment.METERENGINE_API_BASE_URL}/v1/invoices?month=${baseline.month}`, { headers: { Authorization: `Bearer ${environment.METERENGINE_BILLING_API_KEY}` } }));
    expect(repeat.finalized_count).toBe(0);
    evidence.request = { id: requestId, inputTokens: generation.inputTokens, outputTokens: generation.outputTokens, eventCount: 1, supplyAmount: expectedSupply };
    evidence.invoice = { id: ownInvoice!.id, totalAmount: ownInvoice!.totalAmount, repeatedFinalizationCount: repeat.finalized_count };
    writeFileSync(path.join(ARTIFACT_DIR, "real-flow-evidence.json"), JSON.stringify(evidence, null, 2) + "\n");
  } catch (error) {
    failure = safeFailure(error, secrets);
  } finally {
    for (const context of contexts) await context.close();
  }
  if (failure) throw failure;
});

test("도입사 서비스의 세션과 요청 경계가 위조와 교차 출처를 거절한다", async ({ browser }) => {
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
    const candidate = { requestId: randomUUID(), modelId: "lumen-standard", prompt: "허용되지 않은 요청" };
    expect((await context.request.post("/api/generations", { headers: { Origin: "https://untrusted.invalid" }, data: candidate })).status()).toBe(403);
    expect((await context.request.post("/api/generations", { data: candidate })).status()).toBe(403);
    expect((await context.request.post("/api/generations", { headers: { Origin: APP_ORIGIN, "Content-Type": "text/plain" }, data: JSON.stringify(candidate) })).status()).toBe(400);
    expect((await context.request.post("/api/generations", { headers: { Origin: APP_ORIGIN, "Content-Type": "application/json" }, data: "{" })).status()).toBe(400);
    for (const invalid of [{ ...candidate, modelId: "unsupported-model" }, { ...candidate, requestId: "not-a-uuid" }, { ...candidate, prompt: " " }, { ...candidate, prompt: "긴".repeat(4001) }]) {
      expect((await context.request.post("/api/generations", { headers: { Origin: APP_ORIGIN }, data: invalid })).status()).toBe(400);
    }
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
