// 라우트마다 1440x900, 390x844 전체 화면 캡처와 axe-core 위반, 콘솔 오류, 가로 넘침을 남긴다.
// 사용: node .stack456-team/tools/screens.mjs <input.json> <출력 폴더>   (clone 루트에서, frontend 의존성을 설치한 뒤)
import { createRequire } from "node:module";
import { existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const FRONTEND = fileURLToPath(new URL("../../frontend/", import.meta.url));
const require = createRequire(join(FRONTEND, "package.json"));
const { chromium } = require("@playwright/test");

function findAxe() {
  const store = join(FRONTEND, "node_modules", ".pnpm");
  if (!existsSync(store)) return null;
  const dir = readdirSync(store).filter(name => name.startsWith("axe-core@")).sort().pop();
  const file = dir ? join(store, dir, "node_modules", "axe-core", "axe.min.js") : null;
  return file && existsSync(file) ? readFileSync(file, "utf8") : null;
}

const [inputPath, outArg] = process.argv.slice(2);
if (!inputPath || !outArg) throw new Error("사용: node screens.mjs <input.json> <출력 폴더>");
const OUT = resolve(outArg);
const input = JSON.parse(readFileSync(inputPath, "utf8"));
const BASE = input.base_url ?? "http://localhost:33147";
const baseHost = new URL(BASE);
if (!["127.0.0.1", "localhost", "[::1]"].includes(baseHost.hostname) || baseHost.protocol !== "http:") throw new Error("로컬 HTTP 프론트만 찍는다");
const AXE = findAxe();
const SECRETS = (input.secrets ?? []).filter(Boolean);
const VIEWPORTS = input.viewports ?? [
  { key: "desktop", width: 1440, height: 900 },
  { key: "mobile", width: 390, height: 844 },
];
mkdirSync(OUT, { recursive: true });

function redact(value) {
  if (typeof value !== "string") return value;
  let text = value;
  for (const secret of SECRETS) text = text.split(secret).join("<token>");
  return text.replace(/me_(?:invite|session|invoice|test|live)_[A-Za-z0-9_-]+/g, "<token>");
}

async function login(context, account) {
  const page = await context.newPage();
  await page.goto(`${BASE}/login${account.returnTo ? `?returnTo=${encodeURIComponent(account.returnTo)}` : ""}`);
  await page.getByLabel("이메일", { exact: true }).fill(account.email);
  await page.getByLabel("비밀번호", { exact: true }).fill(account.password);
  await page.getByRole("button", { name: "로그인", exact: true }).click();
  await page.waitForURL(url => !url.pathname.startsWith("/login"), { timeout: 20_000 }).catch(() => {});
  const landed = new URL(page.url()).pathname;
  await page.close();
  return landed;
}

async function captureOne(context, route, viewport) {
  const page = await context.newPage();
  await page.setViewportSize({ width: viewport.width, height: viewport.height });
  const consoleErrors = [];
  page.on("console", message => { if (message.type() === "error") consoleErrors.push(message.text().slice(0, 500)); });
  page.on("pageerror", error => consoleErrors.push(`pageerror: ${String(error.message).slice(0, 500)}`));
  let status = null;
  let navError = null;
  try {
    const response = await page.goto(`${BASE}${route.path}`, { waitUntil: "networkidle", timeout: 45_000 });
    status = response ? response.status() : null;
  } catch (error) {
    navError = String(error.message).split("\n")[0];
  }
  await page.waitForTimeout(800);
  const file = join(OUT, `${route.name}-${viewport.key}.png`);
  try { await page.screenshot({ path: file, fullPage: true, animations: "disabled" }); } catch (error) { navError = `${navError ? `${navError}; ` : ""}screenshot: ${String(error.message).split("\n")[0]}`; }
  let violations = [];
  let axeError = AXE ? null : "axe-core를 frontend/node_modules/.pnpm에서 찾지 못했다";
  if (AXE) {
    try {
      await page.evaluate(AXE);
      violations = await page.evaluate(async () => {
        const result = await window.axe.run(document, { resultTypes: ["violations"] });
        return result.violations.map(v => ({ id: v.id, impact: v.impact, nodes: v.nodes.length, help: v.help, targets: v.nodes.slice(0, 5).map(n => n.target.join(" ")) }));
      });
    } catch (error) {
      axeError = String(error.message).split("\n")[0];
    }
  }
  const finalPath = new URL(page.url()).pathname;
  const title = await page.title().catch(() => "");
  const h1 = await page.locator("h1").first().textContent({ timeout: 1000 }).catch(() => null);
  const horizontalOverflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth).catch(() => null);
  await page.close();
  return { viewport: viewport.key, status, navError: redact(navError), finalPath: redact(finalPath), title, h1, horizontalOverflow, screenshot: file, consoleErrors: consoleErrors.map(redact), violations, axeError };
}

const results = [];
const browser = await chromium.launch();
try {
  for (const group of input.groups) {
    const reducedMotion = group.reducedMotion ?? input.reducedMotion;
    const context = await browser.newContext({ locale: "ko-KR", timezoneId: "Asia/Seoul", ...(reducedMotion ? { reducedMotion } : {}) });
    const landed = group.login ? await login(context, group.login) : null;
    for (const route of group.routes) {
      const perViewport = [];
      for (const viewport of VIEWPORTS) perViewport.push(await captureOne(context, route, viewport));
      results.push({ group: group.name, account: group.accountLabel ?? "anonymous", loginLanded: redact(landed), reducedMotion: reducedMotion ?? null, name: route.name, path: redact(route.path.replace(/#.*/, "#<fragment>")), note: route.note ?? null, viewports: perViewport });
      console.log(`${group.name} ${route.name} ${perViewport.map(v => `${v.viewport}:${v.status ?? v.navError} axe=${v.violations.length} console=${v.consoleErrors.length} overflow=${v.horizontalOverflow}`).join(" ")}`);
    }
    await context.close();
  }
} finally {
  await browser.close();
}
writeFileSync(join(OUT, "screens.json"), JSON.stringify(results, null, 2));
console.log(`wrote ${results.length} routes to ${OUT}`);
