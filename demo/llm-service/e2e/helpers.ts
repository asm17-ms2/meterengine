import { expect, type APIRequestContext, type APIResponse, type BrowserContext, type Page } from "@playwright/test";
import { readFileSync, mkdirSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { transactionId } from "../src/lib/catalog";
import type { GenerationView, SessionResponse, WorkspaceSnapshot } from "../src/lib/contracts";

export const BASE_URL = process.env.OPENAB_E2E_URL ?? process.env.OPENAB_E2E_BASE_URL ?? "http://localhost:3200";
export const APP_ORIGIN = new URL(BASE_URL).origin;
export const ARTIFACT_DIR = process.env.OPENAB_E2E_ARTIFACT_DIR ?? path.join(os.tmpdir(), "meterengine-openab-service");
export const EVENT_PROPERTY_NAMES = ["cache_read_tokens", "cache_write_tokens", "input_tokens", "latency_ms", "model", "output_tokens", "surface"];

export type RuntimeAccount = { id: string; name: string; email: string; customerId: string };
export type RuntimeEnvironment = Record<string, string>;
export type MeterEvent = { transaction_id: string; customer_id: string; type: string; properties: Record<string, unknown> };

export function runtimeEnvironment(): RuntimeEnvironment {
  const filename = process.env.OPENAB_E2E_ENV_FILE;
  if (!filename) throw new Error("OPENAB_E2E_ENV_FILE로 로컬 검증 서버의 환경 JSON 경로를 지정하세요.");
  const environment = JSON.parse(readFileSync(filename, "utf8")) as RuntimeEnvironment;
  const address = new URL(environment.METERENGINE_API_BASE_URL);
  if (address.protocol !== "http:" || !["localhost", "127.0.0.1", "[::1]"].includes(address.hostname)) {
    throw new Error("실제 과금 서버에는 E2E를 실행하지 않습니다.");
  }
  return environment;
}

export function runtimeAccounts(environment: RuntimeEnvironment): RuntimeAccount[] {
  const accounts = JSON.parse(environment.OPENAB_ACCOUNTS) as RuntimeAccount[];
  if (!Array.isArray(accounts) || accounts.length < 2 || accounts.some(account => !account.customerId || !account.id)) {
    throw new Error("계정 격리 검증을 위해 체험 계정이 두 개 이상 필요합니다.");
  }
  return accounts;
}

export function secretValues(environment: RuntimeEnvironment): Set<string> {
  return new Set(Object.entries(environment).filter(([key]) => /KEY|SECRET|TOKEN|PASSWORD/.test(key)).map(([, value]) => value).filter(Boolean));
}

export function safeFailure(error: unknown, secrets: Set<string>): Error {
  const redact = (value: string) => {
    let result = value;
    for (const secret of secrets) result = result.split(secret).join("[redacted]");
    return result.replace(/me_(?:test|live|reg|invite)_[A-Za-z0-9]+/g, "[redacted token]");
  };
  const safe = new Error(redact(error instanceof Error ? error.message : "OpenAB 서비스 검증에 실패했습니다."));
  if (error instanceof Error && error.stack) safe.stack = redact(error.stack);
  return safe;
}

export async function rememberSession(context: BrowserContext, secrets: Set<string>): Promise<void> {
  for (const cookie of await context.cookies()) if (/session/.test(cookie.name)) secrets.add(cookie.value);
}

export async function jsonResponse<T>(response: APIResponse, status = 200): Promise<T> {
  expect(response.status(), "HTTP 상태").toBe(status);
  return response.json() as Promise<T>;
}

export async function session(request: APIRequestContext): Promise<SessionResponse> {
  return jsonResponse<SessionResponse>(await request.get("/api/session"));
}

export async function login(request: APIRequestContext, accountId: string): Promise<void> {
  await jsonResponse(await request.post("/api/session", { headers: { Origin: APP_ORIGIN }, data: { accountId } }));
}

export async function workspace(request: APIRequestContext, month?: string): Promise<WorkspaceSnapshot> {
  return jsonResponse<WorkspaceSnapshot>(await request.get(`/api/workspace${month ? `?month=${encodeURIComponent(month)}` : ""}`));
}

export async function awaitGeneration(request: APIRequestContext, id: string): Promise<GenerationView> {
  let generation: GenerationView | undefined;
  await expect.poll(async () => {
    const response = await request.get(`/api/requests/${encodeURIComponent(id)}`);
    if (response.status() !== 200) return String(response.status());
    generation = await response.json() as GenerationView;
    return generation.state;
  }, { message: "생성 결과 보존", timeout: 20_000 }).toBe("completed");
  return generation!;
}

// 시연 데이터가 많아도 찾을 수 있게 거래 ID로 한 건을 조회한다. 없으면 null이다.
export async function meterEvent(request: APIRequestContext, environment: RuntimeEnvironment, requestId: string): Promise<MeterEvent | null> {
  const response = await request.get(`${environment.METERENGINE_API_BASE_URL}/v1/events/lookup?${new URLSearchParams({ transaction_id: transactionId(requestId) })}`, { headers: { Authorization: `Bearer ${environment.METERENGINE_API_KEY}` } });
  if (response.status() === 404) return null;
  return jsonResponse<MeterEvent>(response);
}

export function expectEventMatches(event: MeterEvent | null, generation: GenerationView, customerId: string): void {
  expect(event, "수집된 이벤트").not.toBeNull();
  expect(event!.customer_id).toBe(customerId);
  expect(event!.type).toBe("llm_request");
  expect(Object.keys(event!.properties).sort()).toEqual(EVENT_PROPERTY_NAMES);
  expect(event!.properties).toEqual({
    model: generation.modelId, surface: generation.surface,
    input_tokens: generation.usage.input, output_tokens: generation.usage.output,
    cache_read_tokens: generation.usage.cacheRead, cache_write_tokens: generation.usage.cacheWrite,
    latency_ms: generation.latencyMs,
  });
}

export async function screenshot(page: Page, filename: string, secrets: Set<string>): Promise<void> {
  assertNoSecrets(await page.content(), secrets);
  mkdirSync(ARTIFACT_DIR, { recursive: true });
  await page.screenshot({ path: path.join(ARTIFACT_DIR, `${process.env.OPENAB_E2E_CAPTURE_PREFIX ?? ""}${filename}`), animations: "disabled" });
}

export async function noHorizontalOverflow(page: Page): Promise<void> {
  const widths = await page.evaluate(() => ({ viewport: window.innerWidth, document: document.documentElement.scrollWidth }));
  expect(widths.document, "문서의 가로 넘침").toBeLessThanOrEqual(widths.viewport);
}

// 데모 띠가 맨 위에 붙어 있고 스크롤 뒤에도 보이며 본문 첫 제목을 가리지 않는지 본다.
export async function bannerStaysVisible(page: Page): Promise<void> {
  const banner = page.getByRole("note", { name: "데모 안내" });
  await expect(banner).toBeVisible();
  const scrollTo = (position: "top" | "bottom") => page.evaluate(where => {
    for (const element of [document.documentElement, document.querySelector("[data-scroll-root]")]) element?.scrollTo(0, where === "top" ? 0 : element.scrollHeight);
  }, position);
  await scrollTo("top");
  const top = await banner.boundingBox();
  const heading = await page.locator("main h1").first().boundingBox();
  expect(top, "띠 위치").not.toBeNull();
  if (heading) expect(heading.y, "띠가 제목을 가리지 않음").toBeGreaterThanOrEqual(top!.y + top!.height - 1);
  await scrollTo("bottom");
  const scrolled = await banner.boundingBox();
  expect(Math.abs(scrolled!.y), "스크롤 뒤에도 맨 위").toBeLessThanOrEqual(1);
  await expect(banner).toBeInViewport();
  await scrollTo("top");
}

export function assertNoSecrets(value: unknown, secrets: Set<string>): void {
  const text = typeof value === "string" ? value : JSON.stringify(value);
  expect([...secrets].some(secret => secret.length > 8 && text.includes(secret)), "응답에 비밀값 없음").toBe(false);
}

export async function assertClientAssetsSafe(page: Page, secrets: Set<string>): Promise<number> {
  const urls = await page.locator("script[src]").evaluateAll(elements => elements.map(element => (element as HTMLScriptElement).src).filter(value => {
    const url = new URL(value);
    return url.origin === window.location.origin && url.pathname.startsWith("/_next/static/");
  }));
  expect(urls.length, "실제 클라이언트 번들 검사").toBeGreaterThan(0);
  for (const url of new Set(urls)) {
    const response = await page.request.get(url);
    expect(response.status()).toBe(200);
    assertNoSecrets(await response.text(), secrets);
  }
  return new Set(urls).size;
}
