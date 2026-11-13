import { mkdir, readFile, stat } from "node:fs/promises";
import { randomBytes } from "node:crypto";
import path from "node:path";
import { parseArgs } from "node:util";
import { COMPANY, DIMENSION_PROPERTIES, EVENT_TYPE, METRICS, TOKEN_UNIT_SIZE } from "../src/lib/catalog.ts";
import { CUSTOMERS, billingEmail, externalId, type DemoCustomer } from "./demo-data/customers.ts";
import { checkFirstDay, checkUntil, eventsDigest, generateEvents, kstMonth, summarize, usageRows, type DemoEvent, type FirstDayRecord } from "./demo-data/events.ts";
import { checkOutputPath, writePrivate } from "./demo-data/output.ts";
import { estimateCharges, priceRatePlans, type PriceRatePlan } from "./demo-data/prices.ts";
import { checkTarget, SEED_ENVIRONMENT } from "./demo-data/target.ts";

const USAGE = "사용법: node scripts/seed-demo.ts --output <비공개 경로>/openab-config.json [--seed <값>] [--until <ISO 시각>] [--concurrency <1-8>] [--base-url <주소> --confirm-host <호스트>] [--existing-owner --organization-id <조직 ID>] | --dry-run";
const DEFAULT_SEED = "openab-2026";
const BATCH_SIZE = 100;
const RETRY_DELAYS_MS = [1000, 2000, 4000, 8000, 16000, 16000];
// 가입할 때 동의했다고 기록되는 약관 판. 서버는 값을 검사하지 않고 기록만 하므로, 콘솔의 frontend/src/lib/auth/consents.ts와 손으로 맞춘다.
const CONSENT_VERSION = "2026-11-11";
// 초기화 수집 키는 한도 없이 쓰고 수집이 끝나면 폐기한다. 앱에 남는 키 둘에는 분당 한도를 둔다.
const KEY_SPECS = {
  seed: { scopes: ["ingest"], label: `${COMPANY.name} 초기화 수집(끝나면 폐기)`, rateLimitPerMinute: null },
  runtime: { scopes: ["read", "ingest"], label: `${COMPANY.name} 서비스 수집과 조회`, rateLimitPerMinute: 300 },
  billing: { scopes: ["admin"], label: `${COMPANY.name} 카드 등록 서버 전용`, rateLimitPerMinute: 60 },
} as const;
type KeyName = keyof typeof KEY_SPECS;
type KeyRecord = { id: string; secret: string };

type SeedState = {
  baseUrl: string;
  environment: typeof SEED_ENVIRONMENT;
  seed: string;
  ownerEmail: string;
  // 화면에서 가입한 계정을 이어받으면(--existing-owner) 암호를 저장하지 않고 실행할 때마다 환경 변수로 받는다.
  ownerPassword?: string;
  existingOwner?: boolean;
  organizationName: string;
  organizationId?: string;
  keys: Partial<Record<KeyName, KeyRecord>>;
  firstDay?: FirstDayRecord;
};
type JsonObject = Record<string, unknown>;

const { values: options } = parseArgs({
  args: process.argv.slice(2).filter(value => value !== "--"),
  options: {
    output: { type: "string" },
    seed: { type: "string" },
    until: { type: "string" },
    concurrency: { type: "string" },
    "base-url": { type: "string" },
    "confirm-host": { type: "string" },
    "existing-owner": { type: "boolean", default: false },
    "organization-id": { type: "string" },
    "dry-run": { type: "boolean", default: false },
  },
  strict: true,
  allowPositionals: false,
});
const dryRun = options["dry-run"] === true;
const seed = options.seed ?? DEFAULT_SEED;
if (!/^[A-Za-z0-9._-]{1,64}$/.test(seed)) throw new Error("--seed는 영문, 숫자, 점, 밑줄, 붙임표 1~64자입니다");
const concurrency = Number(options.concurrency ?? "4");
if (!Number.isInteger(concurrency) || concurrency < 1 || concurrency > 8) throw new Error("--concurrency는 1~8입니다");
const startedAt = new Date();
const until = options.until ? new Date(options.until) : startedAt;
if (Number.isNaN(until.getTime())) throw new Error("--until은 ISO 시각입니다");
if (!dryRun) checkUntil(until, startedAt);
if (!dryRun && !options.output) throw new Error(USAGE);

const target = checkTarget({
  baseUrlOption: options["base-url"],
  baseUrlEnv: process.env.METERENGINE_API_BASE_URL,
  confirmHost: options["confirm-host"],
  environmentEnv: process.env.METERENGINE_ENVIRONMENT,
});
const environment = SEED_ENVIRONMENT;
const existingOwner = options["existing-owner"] === true;
const organizationIdOption = options["organization-id"]?.toLowerCase();
if (organizationIdOption !== undefined && !existingOwner) throw new Error("--organization-id는 --existing-owner와 같이 줍니다");
if (organizationIdOption !== undefined && !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(organizationIdOption)) throw new Error("--organization-id는 UUID 형식입니다");
if (!dryRun) {
  const hasOwnerEnv = Boolean(process.env.OPENAB_OWNER_EMAIL || process.env.OPENAB_OWNER_PASSWORD);
  if (existingOwner && !(process.env.OPENAB_OWNER_EMAIL && process.env.OPENAB_OWNER_PASSWORD)) throw new Error("--existing-owner에는 OPENAB_OWNER_EMAIL과 OPENAB_OWNER_PASSWORD가 필요합니다");
  if (!existingOwner && hasOwnerEnv) throw new Error("OPENAB_OWNER_EMAIL과 OPENAB_OWNER_PASSWORD는 --existing-owner와 같이 줘야 합니다. 새 조직을 만들려면 두 값을 지우세요");
  // 앱 설정에 들어가는 주소다. 기본값이 localhost라 로컬이 아닌 대상에서는 직접 받는다.
  if (!target.isLocal && !(process.env.OPENAB_APP_ORIGIN && process.env.METERENGINE_CONSOLE_URL)) throw new Error("로컬이 아닌 서버는 OPENAB_APP_ORIGIN과 METERENGINE_CONSOLE_URL을 줘야 합니다");
}

const month = kstMonth(until);
const plans = priceRatePlans(CUSTOMERS);
const events = generateEvents(seed, CUSTOMERS, until);
const plan = summarize(events);
const estimated = estimateCharges(usageRows(events), plans);
const estimatedTotal = [...estimated.values()].reduce((sum, amount) => sum + amount, 0);
const bySize = (sizes: Map<string, number>) => Object.fromEntries((["large", "medium", "small", "new"] as const).map(size => {
  const amounts = CUSTOMERS.filter(customer => customer.size === size).map(customer => sizes.get(customer.slug) ?? 0);
  return [size, { customers: amounts.length, min: Math.min(...amounts), max: Math.max(...amounts), sum: amounts.reduce((sum, amount) => sum + amount, 0) }];
}));

if (dryRun) {
  console.log(JSON.stringify({
    dryRun: true,
    target: { host: target.url.hostname, environment, local: target.isLocal },
    seed, month, until: until.toISOString(),
    organization: COMPANY.name,
    customers: CUSTOMERS.length,
    metrics: METRICS.map(metric => metric.code),
    priceRates: { common: plans.filter(rate => !rate.customerSlug).length, customer: plans.filter(rate => rate.customerSlug).length },
    eventsDigest: eventsDigest(events),
    ...plan,
    estimatedDraft: { total: estimatedTotal, bySize: bySize(estimated) },
  }, null, 2));
  process.exit(0);
}

const baseUrl = target.url.origin;
const output = path.resolve(options.output!);
const statePath = `${output}.seed-state.json`;
await checkOutputPath(output);
await mkdir(path.dirname(output), { recursive: true, mode: 0o700 });
const saved = await readFile(statePath, "utf8").catch((error: NodeJS.ErrnoException) => { if (error.code === "ENOENT") return null; throw error; });
if (!saved && await stat(output).then(() => true, () => false)) throw new Error("이 초기화가 만들지 않은 설정 파일은 덮어쓰지 않습니다. 다른 출력 경로를 쓰세요");
const state: SeedState = saved ? JSON.parse(saved) : existingOwner
  ? { baseUrl, environment, seed, ownerEmail: process.env.OPENAB_OWNER_EMAIL!, existingOwner: true, organizationName: "", keys: {} }
  : {
    baseUrl, environment, seed,
    ownerEmail: `billing-admin-${randomBytes(6).toString("hex")}@${COMPANY.emailDomain}`,
    ownerPassword: randomBytes(24).toString("base64url"),
    organizationName: process.env.OPENAB_ORGANIZATION_NAME ?? COMPANY.name,
    keys: {},
  };
if (state.baseUrl !== baseUrl) throw new Error("진행 중인 초기화의 API 서버가 다릅니다. 다른 출력 경로를 쓰세요");
if (state.environment !== environment) throw new Error("진행 중인 초기화의 업무 환경이 다릅니다. 다른 출력 경로를 쓰세요");
if (state.seed !== seed) throw new Error(`진행 중인 초기화의 씨앗(${state.seed})과 다릅니다. 다른 출력 경로를 쓰세요`);
if (Boolean(state.existingOwner) !== existingOwner) throw new Error("진행 중인 초기화와 --existing-owner 여부가 다릅니다. 다른 출력 경로를 쓰세요");
if (existingOwner && state.ownerEmail.toLowerCase() !== process.env.OPENAB_OWNER_EMAIL!.toLowerCase()) throw new Error("진행 중인 초기화의 대표 계정과 OPENAB_OWNER_EMAIL이 다릅니다. 다른 출력 경로를 쓰세요");
if (existingOwner && state.organizationId && state.organizationId !== organizationIdOption) throw new Error("진행 중인 초기화의 조직과 --organization-id가 다릅니다. 다른 출력 경로를 쓰세요");
const ownerPassword = existingOwner ? process.env.OPENAB_OWNER_PASSWORD! : state.ownerPassword!;
// 키 ID를 남기지 않던 옛 상태 파일의 키는 원문만 있어 추적할 수 없다. 아래 키 단계가 같은 라벨의 키를 폐기하고 다시 발급한다.
state.keys ??= {};
// 달 없이 지문만 남기던 firstDayDigest도 옛 형식이다. 어느 달 것인지 몰라 비교하지 않고 지운다.
for (const legacy of ["runtimeKey", "billingKey", "firstDayDigest"]) delete (state as Record<string, unknown>)[legacy];
state.firstDay = checkFirstDay(state.firstDay, seed, CUSTOMERS, until);

const saveState = () => writePrivate(statePath, JSON.stringify(state, null, 2) + "\n");
await saveState();

class ApiFailure extends Error {
  readonly status: number;
  readonly code: string;
  readonly retryAfterMs: number | null;
  constructor(status: number, code: string, retryAfterMs: number | null = null) {
    super(`API가 거절했습니다: ${status} ${code}`);
    this.status = status;
    this.code = code;
    this.retryAfterMs = retryAfterMs;
  }
}
async function request<T>(method: string, route: string, body?: unknown, token?: string): Promise<T> {
  const response = await fetch(baseUrl + route, {
    method, redirect: "error", signal: AbortSignal.timeout(30_000),
    headers: { Accept: "application/json", "X-MeterEngine-Environment": environment, ...(body ? { "Content-Type": "application/json" } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const data: unknown = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) {
    const candidate = typeof data === "object" && data !== null && "code" in data ? String(data.code) : "request_failed";
    const retryAfter = Number(response.headers.get("Retry-After"));
    throw new ApiFailure(response.status, /^[a-z0-9_]+$/.test(candidate) ? candidate : "request_failed", Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter * 1000 : null);
  }
  return data as T;
}
const isNotFound = (error: unknown) => error instanceof ApiFailure && error.status === 404;
const log = (message: string) => console.log(`[seed] ${message}`);

// --- 조직과 대표 계정 ---
let operatorToken: string | undefined;
async function operator(): Promise<string> {
  if (operatorToken) return operatorToken;
  const email = process.env.METERENGINE_OPERATOR_EMAIL;
  const password = process.env.METERENGINE_OPERATOR_PASSWORD;
  if (!email || !password) throw new Error("가입 응답을 받지 못해 만든 조직을 찾는 데 운영자 계정이 필요합니다. METERENGINE_OPERATOR_EMAIL과 METERENGINE_OPERATOR_PASSWORD를 설정하세요");
  operatorToken = (await request<{ token: string }>("POST", "/v1/sessions", { email, password })).token;
  return operatorToken;
}

if (!state.organizationId && !existingOwner) {
  try {
    const signup = await request<{ organization: { id: string } }>("POST", "/v1/signups", {
      organization_name: state.organizationName,
      name: `${COMPANY.name} 과금 담당`, email: state.ownerEmail, password: ownerPassword,
      consents: ["terms_of_service", "privacy_policy"].map(document => ({ document, document_version: CONSENT_VERSION })),
    });
    state.organizationId = signup.organization.id;
  } catch (error) {
    if (!(error instanceof ApiFailure && error.status === 409 && error.code === "email_already_exists")) throw error;
    const known = await request<{ user_accounts: Array<{ email: string; organizations: Array<{ id: string; name: string }> }> }>("GET", "/v1/user-accounts", undefined, await operator());
    const ownerAccount = known.user_accounts.find(account => account.email.toLowerCase() === state.ownerEmail.toLowerCase());
    if (!ownerAccount || ownerAccount.organizations.length !== 1 || ownerAccount.organizations[0].name !== state.organizationName) {
      throw new Error("가입 결과를 자동 확인하지 못했습니다. 상태 파일과 운영자 화면을 확인하세요");
    }
    state.organizationId = ownerAccount.organizations[0].id;
  }
  await saveState();
  log(`조직을 만들었습니다: ${state.organizationId}`);
}

async function ownerSession(): Promise<string> {
  try {
    return (await request<{ token: string }>("POST", "/v1/sessions", { email: state.ownerEmail, password: ownerPassword })).token;
  } catch (error) {
    if (!(error instanceof ApiFailure && error.status === 403 && error.code === "organization_not_active")) throw error;
  }
  // 가입한 조직은 바로 사용 중이다. 로그인이 막히면 조직이 이용 정지된 것이라 스크립트가 풀지 않는다.
  throw new Error("이 계정이 속한 조직 가운데 사용 중인 조직이 없습니다. 운영자 화면에서 조직 상태를 확인하세요");
}
const owner = await ownerSession();
// 로그인은 소속된 첫 활성 조직으로 세션을 만든다. 이메일만으로는 어느 조직인지 정할 수 없어 --organization-id로 고른 조직에 세션을 옮긴다.
if (existingOwner) {
  const { organizations } = await request<{ organizations: Array<{ id: string; name: string; status: string }> }>("GET", "/v1/sessions/current", undefined, owner);
  const listed = organizations.map(organization => `${organization.name} (${organization.id}, ${organization.status})`).join(", ");
  if (!organizationIdOption) throw new Error(`이어받을 조직을 --organization-id로 고르세요. 이 계정이 속한 조직: ${listed}`);
  if (!organizations.some(organization => organization.id === organizationIdOption)) throw new Error(`--organization-id의 조직에 이 계정이 속해 있지 않습니다. 이 계정이 속한 조직: ${listed}`);
  await request("PUT", "/v1/sessions/current/organization", { organization_id: organizationIdOption }, owner);
}
const current = await request<{ id: string; name?: string }>("GET", "/v1/organization", undefined, owner);
type CustomerRow = { id: string; external_id?: string | null; archived_at?: string | null };
const listCustomers = async () => (await request<{ customers: CustomerRow[] }>("GET", "/v1/customers?status=all", undefined, owner)).customers;
if (!state.organizationId && existingOwner) {
  // 처음 이어받을 때는 테스트 환경에 고객도 미터도 없는 조직만 받는다. 쓰던 조직에 시연 데이터를 섞지 않는다.
  const metrics = (await request<{ billable_metrics: unknown[] }>("GET", "/v1/billable-metrics?status=all", undefined, owner)).billable_metrics;
  if ((await listCustomers()).length > 0 || metrics.length > 0) throw new Error("이 조직의 테스트 환경에 이미 고객이나 미터가 있습니다. 방금 가입한 빈 조직만 이어받습니다");
  state.organizationId = current.id;
  state.organizationName = current.name ?? "";
  await saveState();
  log(`화면에서 가입한 조직을 이어받았습니다: ${state.organizationId}`);
}
if (current.id !== state.organizationId) throw new Error("대표 계정의 조직이 상태 파일과 다릅니다");

// --- 미터 ---
for (const metric of METRICS) {
  let existing: JsonObject | null = null;
  try { existing = await request<JsonObject>("GET", `/v1/billable-metrics/${metric.code}`, undefined, owner); }
  catch (error) { if (!isNotFound(error)) throw error; }
  if (existing) {
    const dimensions = JSON.stringify(existing.dimension_properties);
    if (existing.event_type !== EVENT_TYPE || existing.aggregation !== "sum" || existing.target_property !== metric.property || Number(existing.unit_size) !== TOKEN_UNIT_SIZE
      || dimensions !== JSON.stringify(DIMENSION_PROPERTIES) || existing.archived_at != null) {
      throw new Error(`계산 기준이 다른 미터가 이미 있습니다: ${metric.code}`);
    }
    continue;
  }
  await request("POST", "/v1/billable-metrics", { code: metric.code, name: metric.name, event_type: EVENT_TYPE, aggregation: "sum", target_property: metric.property, dimension_properties: [...DIMENSION_PROPERTIES], unit_size: TOKEN_UNIT_SIZE, rounding_mode: "none", rounding_precision: 0 }, owner);
  log(`미터를 만들었습니다: ${metric.code}`);
}

// --- 고객 ---
const listed = { customers: await listCustomers() };
const customerIds = new Map<string, string>();
let createdCustomers = 0;
for (const customer of CUSTOMERS) {
  const found = listed.customers.find(candidate => candidate.external_id === externalId(customer));
  if (found?.archived_at) throw new Error(`보관된 고객이 있습니다: ${externalId(customer)}`);
  const id = found?.id ?? (await request<{ id: string }>("POST", "/v1/customers", { name: customer.name, billing_email: billingEmail(customer), external_id: externalId(customer) }, owner)).id;
  if (!found) createdCustomers++;
  customerIds.set(customer.slug, id);
}
log(`고객 ${CUSTOMERS.length}곳(새로 만든 곳 ${createdCustomers})`);

// --- 단가 ---
type RateRow = { dimension_values: { model?: string; surface?: string }; unit_price: number | null; effective_from: string; effective_until: string | null };
const applies = (rate: RateRow) => rate.effective_from <= month && (rate.effective_until === null || rate.effective_until > month);
const rateCache = new Map<string, RateRow[]>();
async function existingRates(rate: PriceRatePlan): Promise<RateRow[]> {
  const customerId = rate.customerSlug ? customerIds.get(rate.customerSlug)! : undefined;
  const cacheKey = `${rate.metricCode}|${customerId ?? ""}`;
  if (!rateCache.has(cacheKey)) {
    const query = customerId ? `?customer_id=${customerId}` : "";
    rateCache.set(cacheKey, (await request<{ price_rates: RateRow[] }>("GET", `/v1/billable-metrics/${rate.metricCode}/price-rates${query}`, undefined, owner)).price_rates);
  }
  return rateCache.get(cacheKey)!;
}
let createdRates = 0;
for (const rate of plans) {
  const match = (await existingRates(rate)).find(row => applies(row) && row.dimension_values.model === rate.dimensionValues.model && row.dimension_values.surface === rate.dimensionValues.surface);
  if (match) {
    if (Number(match.unit_price) !== rate.unitPrice) throw new Error(`다른 단가가 이미 있습니다: ${rate.metricCode} ${rate.dimensionValues.model} ${rate.dimensionValues.surface}`);
    continue;
  }
  const customerId = rate.customerSlug ? customerIds.get(rate.customerSlug) : undefined;
  await request("POST", `/v1/billable-metrics/${rate.metricCode}/price-rates`, { dimension_values: rate.dimensionValues, unit_price: rate.unitPrice, model: "per_unit", effective_from: month, ...(customerId ? { customer_id: customerId } : {}) }, owner);
  createdRates++;
}
log(`단가 ${plans.length}개(새로 등록 ${createdRates})`);

// --- 서버 키 ---
const activeKeys = (await request<{ api_keys: Array<{ id: string; label: string }> }>("GET", "/v1/api-keys?status=active", undefined, owner)).api_keys;
const revokeKey = (id: string) => request("DELETE", `/v1/api-keys/${id}`, undefined, owner);
for (const name of Object.keys(KEY_SPECS) as KeyName[]) {
  const spec = KEY_SPECS[name];
  const keptId = activeKeys.some(row => row.id === state.keys[name]?.id) ? state.keys[name]!.id : null;
  // 같은 라벨인데 상태 파일에 없는 키는 발급 응답을 잃어 원문을 모르는 키다. 쓸 수 없으니 폐기한다.
  for (const row of activeKeys) if (row.label === spec.label && row.id !== keptId) {
    await revokeKey(row.id);
    log(`상태 파일에 없는 키를 폐기했습니다: ${spec.label} (${row.id})`);
  }
  if (keptId) continue;
  const issued = await request<{ id: string; secret: string }>("POST", "/v1/api-keys", {
    label: spec.label, mode: environment, scopes: [...spec.scopes],
    ...(spec.rateLimitPerMinute ? { rate_limit_per_minute: spec.rateLimitPerMinute } : {}),
  }, owner);
  state.keys[name] = { id: issued.id, secret: issued.secret };
  await saveState();
  log(`키를 발급했습니다: ${spec.label} (${issued.id})`);
}

// --- 앱 설정 ---
const trial = (customer: DemoCustomer) => ({ id: customer.slug, name: customer.name, email: billingEmail(customer), customerId: customerIds.get(customer.slug)! });
const runtime = {
  OPENAB_APP_ORIGIN: process.env.OPENAB_APP_ORIGIN ?? "http://localhost:3200",
  OPENAB_DATABASE_PATH: path.resolve(process.env.OPENAB_DATABASE_PATH ?? path.join(path.dirname(output), `${COMPANY.id}.sqlite`)),
  OPENAB_ACCOUNTS: JSON.stringify(CUSTOMERS.filter(customer => customer.trial).map(trial)),
  METERENGINE_API_BASE_URL: baseUrl,
  METERENGINE_ENVIRONMENT: environment,
  METERENGINE_API_KEY: state.keys.runtime!.secret,
  METERENGINE_BILLING_API_KEY: state.keys.billing!.secret,
  METERENGINE_CONSOLE_URL: process.env.METERENGINE_CONSOLE_URL ?? "http://localhost:33147",
};
await writePrivate(output, JSON.stringify(runtime, null, 2) + "\n");

// --- 사용량 이벤트 ---
type BatchResult = { results: Array<{ result: "stored" | "duplicate" | "rejected"; error?: { code?: string } }> };
const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));
// 방금 발급한 키는 다른 서버에 퍼질 때까지 401일 수 있다. 한 묶음이라도 받아들여진 뒤의 401은 키가 폐기된 것이라 다시 보내지 않는다.
let isKeyAccepted = false;
async function sendBatch(batch: readonly DemoEvent[]): Promise<BatchResult> {
  const body = { events: batch.map(event => ({ transaction_id: event.transactionId, customer_id: customerIds.get(event.customerSlug)!, type: event.type, timestamp: event.timestamp, properties: event.properties })) };
  for (let attempt = 0; ; attempt++) {
    try {
      const result = await request<BatchResult>("POST", "/v1/event-batches", body, state.keys.seed!.secret);
      isKeyAccepted = true;
      return result;
    } catch (error) {
      const retryable = !(error instanceof ApiFailure) || (error.status === 401 && !isKeyAccepted) || error.status === 429 || error.status >= 500;
      if (!retryable || attempt >= RETRY_DELAYS_MS.length) throw error;
      const wait = error instanceof ApiFailure && error.retryAfterMs ? error.retryAfterMs : RETRY_DELAYS_MS[attempt];
      log(`묶음을 ${wait / 1000}초 뒤 다시 보냅니다(${error instanceof ApiFailure ? `${error.status} ${error.code}` : "연결 실패"})`);
      await sleep(wait);
    }
  }
}
const batches: DemoEvent[][] = [];
for (let start = 0; start < events.length; start += BATCH_SIZE) batches.push(events.slice(start, start + BATCH_SIZE));
const counts = { stored: 0, duplicate: 0, rejected: 0 };
const rejectionCodes: Record<string, number> = {};
const ingestStarted = performance.now();
let nextBatch = 0;
let sentEvents = 0;
let lastReport = 0;
let ingestSeconds = 0;
let isIngestDone = false;
let isIngestStopped = false;
const endSessions = async () => {
  for (const token of [owner, operatorToken]) if (token) await request("DELETE", "/v1/sessions/current", undefined, token).catch(() => log("세션을 끝내지 못했습니다. 만료될 때까지 남습니다"));
};
try {
  await Promise.all(Array.from({ length: Math.min(concurrency, batches.length) }, async () => {
    while (!isIngestStopped && nextBatch < batches.length) {
      const batch = batches[nextBatch++];
      const response = await sendBatch(batch);
      for (const result of response.results) {
        counts[result.result]++;
        if (result.result === "rejected") rejectionCodes[result.error?.code ?? "unknown"] = (rejectionCodes[result.error?.code ?? "unknown"] ?? 0) + 1;
      }
      sentEvents += batch.length;
      if (sentEvents - lastReport >= 5000 || sentEvents === events.length) {
        lastReport = sentEvents;
        log(`수집 ${sentEvents}/${events.length} (저장 ${counts.stored}, 중복 ${counts.duplicate}, 거절 ${counts.rejected})`);
      }
    }
  }));
  ingestSeconds = (performance.now() - ingestStarted) / 1000;
  isIngestDone = true;
} finally {
  // 한도 없는 수집 키는 수집이 끝나든 실패하든 여기서 폐기한다. 폐기 요청이 실패했을 때만 상태 파일에 남고, 다음 실행이 이어 쓴 뒤 폐기한다.
  isIngestStopped = true;
  const seedKeyId = state.keys.seed!.id;
  if (await revokeKey(seedKeyId).then(() => true, () => false)) {
    delete state.keys.seed;
    await saveState();
    log(`초기화 수집 키를 폐기했습니다 (${seedKeyId})`);
  } else {
    log(`초기화 수집 키를 폐기하지 못했습니다 (${seedKeyId}). 같은 명령으로 다시 실행하거나 콘솔에서 폐기하세요`);
  }
  // 수집이 실패하면 여기서 끝나므로 세션도 끝낸다. 성공하면 아래 결과 조회에 쓴 뒤 끝낸다.
  if (!isIngestDone) await endSessions();
}

// --- 결과 ---
type Draft = { total_amount: number; customers: Array<{ customer_id: string; amount: number; unpriced_usages: unknown[] }> };
const draft = await request<Draft>("GET", `/v1/invoices/draft?month=${month}`, undefined, owner);
const slugById = new Map([...customerIds].map(([slug, id]) => [id, slug]));
const actual = new Map(draft.customers.map(row => [slugById.get(row.customer_id) ?? row.customer_id, row.amount]));
const issues = await request<{ total: number; summary: Record<string, number> }>("GET", `/v1/event-issues/search?month=${month}&size=1`, undefined, owner);
const unpricedUsages = draft.customers.reduce((sum, row) => sum + row.unpriced_usages.length, 0);
// 끝까지 돌았어도 아래에 하나라도 걸리면 0이 아닌 코드로 끝낸다. 거절된 이벤트나 남은 수집 키는 1, 금액이나 단가가 기대와 다르면 2다.
const failures = [
  ...(counts.rejected > 0 ? [`거절된 이벤트가 ${counts.rejected}건 있습니다`] : []),
  ...(state.keys.seed ? ["한도 없는 초기화 수집 키가 폐기되지 않고 남았습니다"] : []),
];
const mismatches = [
  ...(draft.total_amount !== estimatedTotal ? [`청구 예정액 ${draft.total_amount}원이 추정값 ${estimatedTotal}원과 다릅니다. 앱에서 요청을 보냈거나 이번 달 인보이스를 확정했으면 다를 수 있습니다`] : []),
  ...(unpricedUsages > 0 ? [`단가 없는 사용량이 ${unpricedUsages}건 있습니다`] : []),
];
const summary = {
  organizationId: state.organizationId,
  environment, month, seed, until: until.toISOString(),
  configPath: output, statePath,
  keys: Object.fromEntries((Object.keys(KEY_SPECS) as KeyName[]).filter(name => state.keys[name]).map(name => [name, { id: state.keys[name]!.id, label: KEY_SPECS[name].label, rateLimitPerMinute: KEY_SPECS[name].rateLimitPerMinute }])),
  customers: CUSTOMERS.length, createdCustomers,
  metrics: METRICS.map(metric => metric.code),
  priceRates: plans.length, createdRates,
  events: { generated: events.length, ...counts, rejectionCodes, eventsDigest: eventsDigest(events) },
  ingestSeconds: Number(ingestSeconds.toFixed(1)),
  elapsedSeconds: Number(((Date.now() - startedAt.getTime()) / 1000).toFixed(1)),
  draft: {
    total: draft.total_amount,
    estimatedTotal,
    bySize: bySize(actual),
    unpricedUsages,
  },
  failures,
  mismatches,
  eventIssues: { total: issues.total, summary: issues.summary },
  trialAccounts: CUSTOMERS.filter(customer => customer.trial).map(customer => customer.slug),
};
// 세션은 메모리에만 있지만 서버에 열린 채 남지 않게 끝낸다. 실패해도 결과는 그대로 낸다.
await endSessions();
console.log(JSON.stringify(summary, null, 2));
for (const message of [...failures, ...mismatches]) console.error(`[seed] ${message}`);
if (failures.length > 0) process.exitCode = 1;
else if (mismatches.length > 0) process.exitCode = 2;
