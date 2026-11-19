import type { Command } from "commander";
import type { BillableMetricResponse, ListBillableMetricPricesResponse, MeterEngineClient } from "@meterengine/sdk";
import { resolveApiKey } from "../credentials.ts";
import type { CliDeps } from "../context.ts";
import { createClient, printJson, profileOf } from "../context.ts";
import { UsageError } from "../exit-code.ts";
import { assertMonth, kstMonthOf } from "../month.ts";

type PriceRate = ListBillableMetricPricesResponse["billable_metric_prices"][number]["price_rates"][number];

// 서버가 이벤트가 들어온 뒤 바꾸지 못하게 막는 계산 정의(name 제외)와 보관 여부다.
const METRIC_FIELDS = ["event_type", "aggregation", "target_property", "dimension_properties", "unit_size", "rounding_mode", "rounding_precision", "percentile", "archived"] as const;
type MetricField = (typeof METRIC_FIELDS)[number];
type MetricSummary = Record<MetricField, unknown>;

interface Warning {
  code: string;
  message: string;
}

interface MetricDiff {
  code: string;
  field: MetricField | null;
  left: unknown;
  right: unknown;
}

interface RateSummary {
  model: string;
  unit_price: number | null;
  tiers: unknown;
}

interface PriceDiff {
  billable_metric_code: string;
  dimension_values: Record<string, unknown>;
  left: RateSummary | null;
  right: RateSummary | null;
}

interface Snapshot {
  metrics: Map<string, MetricSummary>;
  prices: ListBillableMetricPricesResponse;
  customerId?: string | null;
  customerPrices?: ListBillableMetricPricesResponse;
}

// 객체 키 순서와 상관없이 같은 값이면 같은 문자열이 되게 한다.
function canonical(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(canonical).join(",")}]`;
  if (typeof value === "object" && value !== null) {
    const entries = Object.entries(value).sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0));
    return `{${entries.map(([key, item]) => `${JSON.stringify(key)}:${canonical(item)}`).join(",")}}`;
  }
  return JSON.stringify(value ?? null);
}

function summarizeMetric(metric: BillableMetricResponse): MetricSummary {
  return {
    event_type: metric.event_type,
    aggregation: metric.aggregation,
    target_property: metric.target_property ?? null,
    dimension_properties: metric.dimension_properties ?? [],
    unit_size: metric.unit_size ?? 1,
    rounding_mode: metric.rounding_mode ?? "none",
    rounding_precision: metric.rounding_precision ?? 0,
    percentile: metric.percentile ?? null,
    archived: metric.archived_at !== null && metric.archived_at !== undefined,
  };
}

function summarizeRate(rate: PriceRate): RateSummary {
  return { model: rate.model, unit_price: rate.unit_price ?? null, tiers: rate.tiers ?? null };
}

function sortedUnion(left: Iterable<string>, right: Iterable<string>): string[] {
  return [...new Set([...left, ...right])].sort((a, b) => (a < b ? -1 : a > b ? 1 : 0));
}

function diffMetrics(left: Map<string, MetricSummary>, right: Map<string, MetricSummary>): MetricDiff[] {
  const diffs: MetricDiff[] = [];
  for (const code of sortedUnion(left.keys(), right.keys())) {
    const a = left.get(code);
    const b = right.get(code);
    if (a === undefined || b === undefined) {
      diffs.push({ code, field: null, left: a ?? null, right: b ?? null });
      continue;
    }
    for (const field of METRIC_FIELDS) {
      if (canonical(a[field]) !== canonical(b[field])) diffs.push({ code, field, left: a[field], right: b[field] });
    }
  }
  return diffs;
}

function ratesByKey(prices: ListBillableMetricPricesResponse): Map<string, { code: string; dimensionValues: Record<string, unknown>; rate: RateSummary }> {
  const rates = new Map<string, { code: string; dimensionValues: Record<string, unknown>; rate: RateSummary }>();
  for (const metric of prices.billable_metric_prices) {
    for (const rate of metric.price_rates) {
      const dimensionValues = rate.dimension_values ?? {};
      rates.set(`${metric.billable_metric_code}\n${canonical(dimensionValues)}`, { code: metric.billable_metric_code, dimensionValues, rate: summarizeRate(rate) });
    }
  }
  return rates;
}

// 단가는 과금 미터 code와 속성 값 조합으로 짝짓고 모델, 단가, 구간만 비교한다. id와 적용 시작 월은 환경마다 다르다.
function diffPrices(left: ListBillableMetricPricesResponse, right: ListBillableMetricPricesResponse): PriceDiff[] {
  const a = ratesByKey(left);
  const b = ratesByKey(right);
  const diffs: PriceDiff[] = [];
  for (const key of sortedUnion(a.keys(), b.keys())) {
    const x = a.get(key);
    const y = b.get(key);
    const found = x ?? y;
    if (found === undefined || (x !== undefined && y !== undefined && canonical(x.rate) === canonical(y.rate))) continue;
    diffs.push({ billable_metric_code: found.code, dimension_values: found.dimensionValues, left: x?.rate ?? null, right: y?.rate ?? null });
  }
  return diffs;
}

async function snapshot(client: MeterEngineClient, month: string, externalId: string | undefined): Promise<Snapshot> {
  const metrics = (await client.listBillableMetrics({ status: "all" })).billable_metrics;
  const prices = await client.listBillableMetricPrices({ month });
  const result: Snapshot = { metrics: new Map(metrics.map((metric) => [metric.code, summarizeMetric(metric)])), prices };
  if (externalId === undefined) return result;
  const customer = (await client.listCustomers({ external_id: externalId })).customers[0];
  result.customerId = customer?.id ?? null;
  if (customer !== undefined) result.customerPrices = await client.listBillableMetricPrices({ month, customer_id: customer.id });
  return result;
}

const ENVIRONMENT_HEADER = "x-meterengine-environment";
type Environment = "legacy" | "test" | "live";

// 키 접두사가 아니라 서버가 응답 헤더로 알린 업무 환경을 기록한다. 헤더 없이 발급한 키는 접두사와 관계없이 legacy다.
async function recordingClient(deps: CliDeps, profile: string): Promise<{ client: MeterEngineClient; environment: () => Environment | null }> {
  let environment: Environment | null = null;
  const base = deps.clientOptions?.fetch ?? ((input, init) => fetch(input, init));
  const recording: typeof fetch = async (input, init) => {
    const response = await base(input, init);
    const value = response.headers.get(ENVIRONMENT_HEADER);
    if (environment === null && (value === "legacy" || value === "test" || value === "live")) environment = value;
    return response;
  };
  const client = await createClient({ ...deps, clientOptions: { ...deps.clientOptions, fetch: recording } }, profile);
  return { client, environment: () => environment };
}

interface Side {
  profile: string;
  key: string;
  environment: Environment | null;
}

function warningsFor(left: Side, right: Side): Warning[] {
  if (left.key === right.key) {
    return [{ code: "same_api_key", message: `두 프로필이 같은 API 키입니다(${left.profile}, ${right.profile}). 같은 업무 환경을 자기 자신과 비교했습니다` }];
  }
  if (left.environment === "legacy" && right.environment === "legacy") {
    return [{ code: "same_environment", message: "두 키가 모두 환경을 지정하지 않고 발급한 legacy 범위 키입니다. 같은 조직의 키라면 같은 데이터를 자기 자신과 비교했습니다. test와 live를 비교하려면 업무 환경을 지정해 발급한 키를 쓰세요" }];
  }
  if (left.environment !== null && left.environment === right.environment) {
    return [{ code: "same_environment", message: `두 키가 모두 ${left.environment} 업무 환경을 봅니다. test와 live를 비교하려면 서로 다른 업무 환경의 키를 저장한 프로필을 쓰세요` }];
  }
  return [];
}

export function registerDiff(program: Command, deps: CliDeps): void {
  program
    .command("diff")
    .description(
      "두 프로필의 업무 환경에서 과금 미터와 단가를 비교해 다른 것만 보여 준다 (GET /v1/billable-metrics, GET /v1/billable-metric-prices, --external-id를 주면 GET /v1/customers). 비교를 끝까지 하면 달라도 종료 코드 0이고 판정은 identical에 있다. mode는 서버가 알린 업무 환경(legacy, test, live)이다",
    )
    .argument("<left>", "비교할 프로필. 예: test")
    .argument("<right>", "비교할 프로필. 예: live")
    .option("--month <YYYY-MM>", "단가를 비교할 청구 월. 생략하면 이번 달(KST)")
    .option("--external-id <id>", "이 외부 ID 고객의 고객별 단가도 비교한다. 고객 UUID는 환경마다 달라 외부 ID로 짝짓는다")
    .action(async (leftProfile: string, rightProfile: string, options: { month?: string; externalId?: string }, command: Command) => {
      if (profileOf(command) !== undefined) throw new UsageError("invalid_usage", "diff는 --profile 대신 비교할 두 프로필을 인자로 받습니다");
      const month = options.month === undefined ? kstMonthOf(deps.now?.() ?? new Date()) : assertMonth(options.month);
      // 서버는 외부 ID의 앞뒤 공백까지 그대로 저장하고 비교하므로 자르지 않는다.
      const externalId = options.externalId;
      if (externalId === "") throw new UsageError("invalid_external_id", "--external-id에 고객의 외부 ID를 쓰세요");
      // 두 키를 다 확인한 뒤에 서버를 부른다. 한쪽 프로필이 없으면 아무 요청도 보내지 않는다.
      const leftKey = await resolveApiKey(deps.env, deps.configDir, leftProfile);
      const rightKey = await resolveApiKey(deps.env, deps.configDir, rightProfile);
      const leftClient = await recordingClient(deps, leftProfile);
      const rightClient = await recordingClient(deps, rightProfile);
      const a = await snapshot(leftClient.client, month, externalId);
      const b = await snapshot(rightClient.client, month, externalId);
      const left: Side = { profile: leftProfile, key: leftKey, environment: leftClient.environment() };
      const right: Side = { profile: rightProfile, key: rightKey, environment: rightClient.environment() };

      const warnings = warningsFor(left, right);
      const billableMetrics = diffMetrics(a.metrics, b.metrics);
      const prices = diffPrices(a.prices, b.prices);
      const output: Record<string, unknown> = {
        identical: false,
        month,
        left: { profile: left.profile, mode: left.environment },
        right: { profile: right.profile, mode: right.environment },
        warnings,
        billable_metrics: billableMetrics,
        prices,
      };
      let isCustomerIdentical = true;
      if (externalId !== undefined) {
        output.customer = { external_id: externalId, left: a.customerId ?? null, right: b.customerId ?? null };
        if (a.customerPrices !== undefined && b.customerPrices !== undefined) {
          const customerPrices = diffPrices(a.customerPrices, b.customerPrices);
          output.customer_prices = customerPrices;
          isCustomerIdentical = customerPrices.length === 0;
        } else {
          const missing = [a.customerId === null ? left.profile : undefined, b.customerId === null ? right.profile : undefined].filter((profile) => profile !== undefined);
          warnings.push({ code: "customer_not_found", message: `고객별 단가를 비교하지 않았습니다. 외부 ID ${externalId} 고객이 없는 프로필: ${missing.join(", ")}` });
          output.customer_prices = null;
          isCustomerIdentical = a.customerId === null && b.customerId === null;
        }
      }
      output.identical = billableMetrics.length === 0 && prices.length === 0 && isCustomerIdentical;
      printJson(deps.stdout, output);
    });
}
