import { chmod, mkdir, readFile, rename, stat, writeFile } from "node:fs/promises";
import { randomBytes, randomUUID } from "node:crypto";
import path from "node:path";

type SeedState = {
  baseUrl: string;
  ownerEmail: string;
  ownerPassword: string;
  organizationName?: string;
  organizationId?: string;
  runtimeKey?: string;
  billingKey?: string;
};
type JsonObject = Record<string, unknown>;

const args = process.argv.slice(2).filter(value => value !== "--");
if (args.length !== 2 || args[0] !== "--output") throw new Error("사용법: node scripts/seed-demo.ts --output /비공개/경로/lumen-config.json");
const output = path.resolve(args[1]);
if (await stat(output).then(() => true, () => false)) throw new Error("기존 설정 파일은 덮어쓰지 않습니다");
const baseUrl = (process.env.METERENGINE_API_BASE_URL ?? "http://127.0.0.1:38147").replace(/\/+$/, "");
const parsedBase = new URL(baseUrl);
if (!new Set(["localhost", "127.0.0.1", "[::1]"]).has(parsedBase.hostname) || !["http:", "https:"].includes(parsedBase.protocol) || parsedBase.username || parsedBase.password) {
  throw new Error("초기화는 로컬 MeterEngine 시연 서버에서만 실행할 수 있습니다");
}
const operatorEmail = process.env.METERENGINE_OPERATOR_EMAIL;
const operatorPassword = process.env.METERENGINE_OPERATOR_PASSWORD;
if (!operatorEmail || !operatorPassword) throw new Error("METERENGINE_OPERATOR_EMAIL과 METERENGINE_OPERATOR_PASSWORD를 설정하세요");
const statePath = `${output}.seed-state.json`;
await mkdir(path.dirname(output), { recursive: true, mode: 0o700 });
const saved = await readFile(statePath, "utf8").catch((error: NodeJS.ErrnoException) => { if (error.code === "ENOENT") return null; throw error; });
const state: SeedState = saved ? JSON.parse(saved) : {
  baseUrl,
  ownerEmail: process.env.LUMEN_OWNER_EMAIL ?? `lumen-owner-${randomUUID()}@example.test`,
  ownerPassword: process.env.LUMEN_OWNER_PASSWORD ?? randomBytes(24).toString("base64url"),
};
if (state.baseUrl !== baseUrl) throw new Error("진행 중인 초기화의 API 서버가 다릅니다");
state.organizationName ??= process.env.LUMEN_ORGANIZATION_NAME ?? "Lumen LLM 예제 서비스";
async function saveState() {
  const temporary = `${statePath}.tmp`;
  await writeFile(temporary, JSON.stringify(state, null, 2) + "\n", { mode: 0o600 });
  await chmod(temporary, 0o600);
  await rename(temporary, statePath);
}
await saveState();

class ApiFailure extends Error {
  readonly status: number;
  readonly code: string;
  constructor(status: number, code: string) {
    super(`초기화 API가 거절됐습니다: ${status} ${code}`);
    this.status = status;
    this.code = code;
  }
}
async function request<T>(method: string, route: string, body?: JsonObject, token?: string): Promise<T> {
  const response = await fetch(baseUrl + route, {
    method, redirect: "error", signal: AbortSignal.timeout(15_000),
    headers: { Accept: "application/json", ...(body ? { "Content-Type": "application/json" } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const data: unknown = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) {
    const candidate = typeof data === "object" && data !== null && "code" in data ? String(data.code) : "request_failed";
    throw new ApiFailure(response.status, /^[a-z0-9_]+$/.test(candidate) ? candidate : "request_failed");
  }
  return data as T;
}

const operator = await request<{ token: string }>("POST", "/v1/sessions", { email: operatorEmail, password: operatorPassword });
if (!state.organizationId) {
  try {
    const signup = await request<{ organization: { id: string } }>("POST", "/v1/signups", {
      organization_name: state.organizationName,
      name: "Lumen 예제 대표관리자", email: state.ownerEmail, password: state.ownerPassword,
      consents: ["terms_of_service", "privacy_policy"].map(document => ({ document, document_version: "2026-10-26-preview" })),
    });
    state.organizationId = signup.organization.id;
  } catch (error) {
    if (error instanceof ApiFailure && !(error.status === 409 && error.code === "email_already_exists")) throw error;
    const known = await request<{ user_accounts: Array<{ email: string; organizations: Array<{ id: string; name: string }> }> }>("GET", "/v1/user-accounts", undefined, operator.token);
    const ownerAccount = known.user_accounts.find(account => account.email.toLowerCase() === state.ownerEmail.toLowerCase());
    if (!ownerAccount || ownerAccount.organizations.length !== 1 || ownerAccount.organizations[0].name !== state.organizationName) {
      throw new Error("가입 결과를 자동 확인하지 못했습니다. 저장된 초기화 상태와 운영자 화면을 확인하세요");
    }
    try { await request("POST", "/v1/sessions", { email: state.ownerEmail, password: state.ownerPassword }); }
    catch (verification) {
      if (!(verification instanceof ApiFailure && verification.status === 403 && verification.code === "organization_not_active")) throw verification;
    }
    state.organizationId = ownerAccount.organizations[0].id;
  }
  await saveState();
}
const organizations = await request<{ organizations: Array<{ id: string; status: string }> }>("GET", "/v1/organizations", undefined, operator.token);
const organization = organizations.organizations.find(candidate => candidate.id === state.organizationId);
if (!organization || !["pending", "active"].includes(organization.status)) throw new Error("도입사 상태를 자동 변경할 수 없습니다. 운영자 화면에서 확인하세요");
if (organization.status === "pending") await request("PUT", `/v1/organizations/${state.organizationId}/status`, { status: "active" }, operator.token);
const owner = await request<{ token: string }>("POST", "/v1/sessions", { email: state.ownerEmail, password: state.ownerPassword });

const models = [
  { id: "lumen-fast", input: 500, output: 1000, requests: 10 },
  { id: "lumen-standard", input: 1000, output: 2000, requests: 20 },
];
const metrics = [
  { code: "llm-input-tokens", name: "입력 토큰", target_property: "input_tokens", aggregation: "sum", unit_size: 1000, rate: "input" },
  { code: "llm-output-tokens", name: "출력 토큰", target_property: "output_tokens", aggregation: "sum", unit_size: 1000, rate: "output" },
  { code: "llm-requests", name: "생성 요청", target_property: null, aggregation: "count", unit_size: 1, rate: "requests" },
] as const;
const month = new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit" }).format(new Date());
for (const metric of metrics) {
  let existing: JsonObject | null = null;
  try { existing = await request<JsonObject>("GET", `/v1/billable-metrics/${metric.code}`, undefined, owner.token); }
  catch (error) { if (!(error instanceof ApiFailure) || error.status !== 404) throw error; }
  if (existing) {
    if (existing.event_type !== "llm_generation" || existing.aggregation !== metric.aggregation || existing.target_property !== metric.target_property || existing.unit_size !== metric.unit_size || existing.archived_at != null) {
      throw new Error(`예제와 다른 계산 기준의 미터가 있습니다: ${metric.code}`);
    }
  } else await request("POST", "/v1/billable-metrics", { code: metric.code, name: metric.name, event_type: "llm_generation", aggregation: metric.aggregation, target_property: metric.target_property, dimension_properties: ["model"], unit_size: metric.unit_size, rounding_mode: "none", rounding_precision: 0 }, owner.token);
}
const currentPrices = await request<{ billable_metric_prices: Array<{ billable_metric_code: string; price_rates: Array<{ dimension_values: JsonObject }> }> }>("GET", `/v1/billable-metric-prices?month=${month}`, undefined, owner.token);
for (const metric of metrics) for (const model of models) {
  const existing = currentPrices.billable_metric_prices.find(row => row.billable_metric_code === metric.code)?.price_rates.some(rate => rate.dimension_values.model === model.id);
  if (!existing) await request("POST", `/v1/billable-metrics/${metric.code}/price-rates`, { dimension_values: { model: model.id }, unit_price: model[metric.rate], model: "per_unit", effective_from: month }, owner.token);
}
const people = [
  { id: "seoyun", name: "서윤의 작업실", email: "seoyun@lumen.example" },
  { id: "jimin", name: "지민의 스튜디오", email: "jimin@lumen.example" },
  { id: "new", name: "새 워크스페이스", email: "new@lumen.example" },
];
const accounts = [];
for (const person of people) {
  const customer = await request<{ id: string }>("POST", "/v1/customers", { name: person.name, billing_email: person.email, external_id: `lumen-demo:${person.id}` }, owner.token);
  accounts.push({ ...person, customerId: customer.id });
}
for (const [field, scopes, label] of [["runtimeKey", ["read", "ingest"], "Lumen 서비스 수집과 조회"], ["billingKey", ["admin"], "Lumen 카드 등록 서버 전용"]] as const) {
  if (state[field]) {
    try { await request("GET", "/v1/organization", undefined, state[field]); continue; }
    catch (error) { if (!(error instanceof ApiFailure) || ![401, 403].includes(error.status)) throw error; }
  }
  const key = await request<{ secret: string }>("POST", "/v1/api-keys", { label, mode: "test", scopes: [...scopes] }, owner.token);
  state[field] = key.secret;
  await saveState();
}
const runtime = {
  LUMEN_APP_ORIGIN: process.env.LUMEN_APP_ORIGIN ?? "http://localhost:3200",
  LUMEN_DATABASE_PATH: path.resolve(process.env.LUMEN_DATABASE_PATH ?? path.join(path.dirname(output), "lumen.sqlite")),
  LUMEN_ACCOUNTS: JSON.stringify(accounts),
  METERENGINE_API_BASE_URL: baseUrl,
  METERENGINE_API_KEY: state.runtimeKey!,
  METERENGINE_BILLING_API_KEY: state.billingKey!,
  METERENGINE_CONSOLE_URL: process.env.METERENGINE_CONSOLE_URL ?? "http://localhost:33147",
};
await writeFile(output, JSON.stringify(runtime, null, 2) + "\n", { flag: "wx", mode: 0o600 });
console.log(JSON.stringify({ organizationId: state.organizationId, configPath: output, ownerStatePath: statePath, accounts: accounts.map(({ id, name }) => ({ id, name })), month }));
