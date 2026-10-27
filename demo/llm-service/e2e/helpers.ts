import { expect, type APIRequestContext, type APIResponse, type BrowserContext, type Page } from "@playwright/test";
import { readFileSync, mkdirSync } from "node:fs";
import path from "node:path";
import type { GenerationView, SessionResponse, WorkspaceSnapshot } from "../src/lib/contracts";

export const BASE_URL = process.env.LUMEN_E2E_URL ?? process.env.LUMEN_E2E_BASE_URL ?? "http://localhost:3200";
export const APP_ORIGIN = new URL(BASE_URL).origin;
export const ARTIFACT_DIR = process.env.LUMEN_E2E_ARTIFACT_DIR ?? "/private/tmp/meterengine-llm-tenant-2026-10-27";

export type RuntimeAccount = { id: string; name: string; email: string; customerId: string };
export type RuntimeEnvironment = Record<string, string>;

export function runtimeEnvironment(): RuntimeEnvironment {
  const filename = process.env.LUMEN_E2E_ENV_FILE;
  if (!filename) throw new Error("LUMEN_E2E_ENV_FILE로 로컬 검증 서버의 환경 JSON 경로를 지정하세요.");
  const environment = JSON.parse(readFileSync(filename, "utf8")) as RuntimeEnvironment;
  const address = new URL(environment.METERENGINE_API_BASE_URL);
  if (address.protocol !== "http:" || !["localhost", "127.0.0.1", "[::1]"].includes(address.hostname)) {
    throw new Error("실제 과금 서버에는 E2E를 실행하지 않습니다.");
  }
  return environment;
}

export function runtimeAccounts(environment: RuntimeEnvironment): RuntimeAccount[] {
  const accounts = JSON.parse(environment.LUMEN_ACCOUNTS) as RuntimeAccount[];
  if (!Array.isArray(accounts) || accounts.length < 2 || accounts.some(account => !account.customerId || !account.id)) {
    throw new Error("계정 격리 검증을 위해 시연 계정이 두 개 이상 필요합니다.");
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
  const safe = new Error(redact(error instanceof Error ? error.message : "도입사 서비스 검증에 실패했습니다."));
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

export async function screenshot(page: Page, filename: string, secrets: Set<string>): Promise<void> {
  assertNoSecrets(await page.content(), secrets);
  mkdirSync(ARTIFACT_DIR, { recursive: true });
  await page.screenshot({ path: path.join(ARTIFACT_DIR, `${process.env.LUMEN_E2E_CAPTURE_PREFIX ?? ""}${filename}`), animations: "disabled" });
}

export async function noHorizontalOverflow(page: Page): Promise<void> {
  const widths = await page.evaluate(() => ({ viewport: window.innerWidth, document: document.documentElement.scrollWidth }));
  expect(widths.document, "문서의 가로 넘침").toBeLessThanOrEqual(widths.viewport);
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
