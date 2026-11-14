import { expect, test, type APIRequestContext, type BrowserContext } from "@playwright/test";
import { spawn, type ChildProcess } from "node:child_process";
import { once } from "node:events";
import { mkdirSync, mkdtempSync, writeFileSync } from "node:fs";
import { createServer, type Server } from "node:http";
import { createServer as createPortProbe, type AddressInfo } from "node:net";
import path from "node:path";
import { randomUUID } from "node:crypto";
import { transactionId } from "../src/lib/catalog";
import type { GenerationView, SessionResponse, WorkspaceSnapshot } from "../src/lib/contracts";
import { ARTIFACT_DIR, assertNoSecrets, jsonResponse, meterEvent, noHorizontalOverflow, runtimeAccounts, runtimeEnvironment, safeFailure, screenshot, secretValues } from "./helpers";

type IngestBody = { transaction_id: string; customer_id: string; timestamp: string; properties: Record<string, unknown> };

async function unusedPort(): Promise<number> {
  const probe = createPortProbe();
  probe.listen(0, "127.0.0.1");
  await once(probe, "listening");
  const port = (probe.address() as AddressInfo).port;
  await new Promise<void>((resolve, reject) => probe.close(error => error ? reject(error) : resolve()));
  return port;
}

async function stop(child: ChildProcess | undefined): Promise<void> {
  if (!child || child.exitCode !== null || child.signalCode !== null) return;
  child.kill("SIGTERM");
  const result = await Promise.race([once(child, "exit").then(() => true), new Promise<false>(resolve => setTimeout(() => resolve(false), 5000))]);
  if (!result && child.exitCode === null && child.signalCode === null) {
    child.kill("SIGKILL");
    await once(child, "exit");
  }
}

async function closeProxy(server: Server | undefined): Promise<void> {
  if (!server?.listening) return;
  server.closeAllConnections();
  await new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
}

test("수집 장애와 응답 유실을 자체 DB에 보존하고 재기동 뒤 한 번만 과금한다", async ({ browser, playwright, request }) => {
  const entry = process.env.OPENAB_E2E_SERVER_ENTRY;
  if (!entry || !path.isAbsolute(entry)) throw new Error("복구 검증용 standalone server.js 절대경로를 OPENAB_E2E_SERVER_ENTRY로 지정하세요.");
  const environment = runtimeEnvironment();
  const account = runtimeAccounts(environment)[0];
  const secrets = secretValues(environment);
  mkdirSync(ARTIFACT_DIR, { recursive: true });
  const directory = mkdtempSync(path.join(ARTIFACT_DIR, "recovery-"));
  const database = path.join(directory, "openab.sqlite");
  const appPort = await unusedPort();
  const origin = `http://127.0.0.1:${appPort}`;
  let mode: "offline" | "healthy" | "drop-after-accept" = "offline";
  let failWorkspaceReads = false;
  const attempts: IngestBody[] = [];
  let proxy: Server | undefined;
  let child: ChildProcess | undefined;
  let client: APIRequestContext | undefined;
  let context: BrowserContext | undefined;
  let failure: Error | undefined;
  let childFailed = false;
  const start = async (proxyPort: number) => {
    childFailed = false;
    child = spawn(process.execPath, [entry], {
      cwd: path.dirname(entry),
      env: { ...process.env, ...environment, OPENAB_APP_ORIGIN: origin, OPENAB_DATABASE_PATH: database, OPENAB_AUTO_FLUSH_INTERVAL_MS: "0", METERENGINE_API_BASE_URL: `http://127.0.0.1:${proxyPort}`, PORT: String(appPort), HOSTNAME: "127.0.0.1", NEXT_TELEMETRY_DISABLED: "1" },
      stdio: ["ignore", "ignore", "ignore"],
    });
    child.once("error", () => { childFailed = true; });
    await expect.poll(async () => {
      if (childFailed || child?.exitCode !== null) return "stopped";
      try { return String((await fetch(`${origin}/api/health`)).status); }
      catch { return "starting"; }
    }, { message: "격리된 복구 검증 서버 기동", timeout: 45_000 }).toBe("200");
  };
  try {
    proxy = createServer(async (incoming, outgoing) => {
      try {
        const buffers: Buffer[] = [];
        for await (const chunk of incoming) buffers.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
        const body = Buffer.concat(buffers);
        const requestPath = new URL(incoming.url ?? "/", "http://localhost").pathname;
        const isIngest = incoming.method === "POST" && requestPath === "/v1/events";
        if (isIngest) attempts.push(JSON.parse(body.toString("utf8")) as IngestBody);
        if (failWorkspaceReads && incoming.method === "GET" && ["/v1/usage", "/v1/invoices/draft"].includes(requestPath)) {
          outgoing.writeHead(503, { "Content-Type": "application/json" });
          outgoing.end(JSON.stringify({ code: "fixture_read_unavailable", message: "부분 조회 장애 대역" }));
          return;
        }
        if (isIngest && mode === "offline") {
          outgoing.writeHead(503, { "Content-Type": "application/json" });
          outgoing.end(JSON.stringify({ code: "fixture_unavailable", message: "수집 장애 대역" }));
          return;
        }
        const headers = new Headers();
        for (const [name, value] of Object.entries(incoming.headers)) {
          if (["host", "connection", "content-length", "transfer-encoding"].includes(name) || value === undefined) continue;
          headers.set(name, Array.isArray(value) ? value.join(", ") : value);
        }
        const upstream = await fetch(new URL(incoming.url ?? "/", environment.METERENGINE_API_BASE_URL), { method: incoming.method, headers, body: body.length ? body : undefined, redirect: "error" });
        const result = Buffer.from(await upstream.arrayBuffer());
        if (isIngest && mode === "drop-after-accept") {
          outgoing.writeHead(503, { "Content-Type": "application/json" });
          outgoing.end(JSON.stringify({ code: "fixture_response_lost", message: "저장 뒤 응답 유실 대역" }));
          return;
        }
        outgoing.writeHead(upstream.status, { "Content-Type": upstream.headers.get("content-type") ?? "application/json" });
        outgoing.end(result);
      } catch {
        outgoing.writeHead(502, { "Content-Type": "application/json" });
        outgoing.end(JSON.stringify({ code: "fixture_proxy_failure", message: "검증 대역 연결 오류" }));
      }
    });
    proxy.listen(0, "127.0.0.1");
    await once(proxy, "listening");
    const proxyPort = (proxy.address() as AddressInfo).port;
    await start(proxyPort);
    client = await playwright.request.newContext({ baseURL: origin });
    await jsonResponse(await client.post("/api/session", { headers: { Origin: origin }, data: { accountId: account.id } }));
    for (const cookie of (await client.storageState()).cookies) secrets.add(cookie.value);
    const firstId = randomUUID();
    const privatePrompt = `서버 재기동 뒤에도 보존할 원문 ${randomUUID()}`;
    const first = await jsonResponse<GenerationView>(await client.post("/api/generations", { headers: { Origin: origin }, data: { requestId: firstId, surface: "api", modelId: "ab-5-mini", prompt: privatePrompt } }));
    expect(first.state).toBe("completed");
    expect(["failed", "pending"].includes(first.meteringStatus)).toBe(true);
    expect(first.output).not.toBe("");
    expect(attempts).toHaveLength(1);
    expect(JSON.stringify(attempts).includes(privatePrompt), "원문을 수집 요청에 싣지 않음").toBe(false);
    assertNoSecrets(attempts, secrets);
    context = await browser.newContext({ baseURL: origin, storageState: await client.storageState(), viewport: { width: 1280, height: 900 } });
    const page = await context.newPage();
    await page.goto("/history");
    const record = page.locator(".history-record").filter({ hasText: privatePrompt }).first();
    await record.locator("summary").click();
    await expect(record.locator(".history-content")).toContainText(first.output);
    await page.goto("/");
    await expect(page.getByRole("textbox", { name: "메시지", exact: true })).toBeVisible();
    await expect(page.getByText("사용량 1건을 아직 기록하지 못했어요.", { exact: true })).toBeVisible();
    await page.getByText("사용량 1건을 아직 기록하지 못했어요.", { exact: true }).scrollIntoViewIfNeeded();
    await noHorizontalOverflow(page);
    await screenshot(page, "15-metering-pending-desktop.png", secrets);
    failWorkspaceReads = true;
    await page.reload();
    const warning = page.getByRole("alert").filter({ hasText: "사용량과 청구 정보 일부를 불러오지 못했어요" });
    await expect(warning).toBeVisible();
    await expect(warning).toContainText("대화와 요청 기록은 그대로 있어요");
    await expect(page.locator(".header-bill")).toHaveCount(0);
    const partial = await jsonResponse<WorkspaceSnapshot>(await client.get("/api/workspace"));
    expect(partial.usage.state).toBe("unavailable");
    expect(partial.billing.state).toBe("unavailable");
    expect(partial.billing.draft).toBeNull();
    expect(partial.requests.some(generation => generation.id === firstId)).toBe(true);
    await screenshot(page, "16-partial-workspace-desktop.png", secrets);
    await page.setViewportSize({ width: 390, height: 844 });
    await noHorizontalOverflow(page);
    await screenshot(page, "17-partial-workspace-mobile.png", secrets);
    await page.setViewportSize({ width: 1280, height: 900 });
    failWorkspaceReads = false;
    await warning.getByRole("button", { name: "다시 불러오기", exact: true }).click();
    await expect(warning).toBeHidden();
    await stop(child);
    child = undefined;
    await start(proxyPort);
    const existingSession = await jsonResponse<SessionResponse>(await client.get("/api/session"));
    expect(existingSession.user?.id).toBe(account.id);
    const preserved = await jsonResponse<GenerationView>(await client.get(`/api/requests/${firstId}`));
    expect(preserved.output).toBe(first.output);
    expect(preserved.createdAt).toBe(first.createdAt);
    mode = "healthy";
    await jsonResponse(await client.post("/api/metering/retry", { headers: { Origin: origin }, data: { requestId: firstId.toUpperCase() } }));
    const delivered = await jsonResponse<GenerationView>(await client.get(`/api/requests/${firstId}`));
    expect(delivered.meteringStatus).toBe("delivered");
    await page.goto("/usage");
    await expect(page.getByText("이번 달 요청의 사용량을 모두 기록했어요.", { exact: true })).toBeVisible();
    await page.getByText("이번 달 요청의 사용량을 모두 기록했어요.", { exact: true }).scrollIntoViewIfNeeded();
    await screenshot(page, "18-metering-recovered-desktop.png", secrets);
    const firstAttempts = attempts.filter(event => event.transaction_id === transactionId(firstId));
    expect(firstAttempts).toHaveLength(2);
    expect(firstAttempts[1]).toEqual(firstAttempts[0]);
    mode = "drop-after-accept";
    const secondId = randomUUID();
    const second = await jsonResponse<GenerationView>(await client.post("/api/generations", { headers: { Origin: origin }, data: { requestId: secondId, surface: "api", modelId: "ab-5", prompt: `수신 뒤 응답 유실 ${randomUUID()}` } }));
    expect(["failed", "pending"].includes(second.meteringStatus)).toBe(true);
    mode = "healthy";
    await jsonResponse(await client.post("/api/metering/retry", { headers: { Origin: origin }, data: { requestId: secondId } }));
    expect((await jsonResponse<GenerationView>(await client.get(`/api/requests/${secondId}`))).meteringStatus).toBe("delivered");
    const secondAttempts = attempts.filter(event => event.transaction_id === transactionId(secondId));
    expect(secondAttempts).toHaveLength(2);
    expect(secondAttempts[1]).toEqual(secondAttempts[0]);
    const beforeRepeat = attempts.length;
    await jsonResponse(await client.post("/api/metering/retry", { headers: { Origin: origin }, data: { requestId: secondId } }));
    expect(attempts).toHaveLength(beforeRepeat);
    for (const id of [firstId, secondId]) {
      const stored = await meterEvent(request, environment, id);
      expect(stored?.customer_id).toBe(account.customerId);
    }
    writeFileSync(path.join(ARTIFACT_DIR, "recovery-evidence.json"), JSON.stringify({ localDbPersists: true, sessionPersists: true, originalTimestampPreserved: true, partialReadsReportedUnavailable: true, retryRestoredReads: true, offlineAttempts: firstAttempts.length, responseLossAttempts: secondAttempts.length, requestIds: [firstId, secondId], storedEventsForRequests: 2, requestCount: 2 }, null, 2) + "\n");
  } catch (error) {
    failure = safeFailure(error, secrets);
  } finally {
    await context?.close();
    await client?.dispose();
    await stop(child);
    await closeProxy(proxy);
  }
  if (failure) throw failure;
});
