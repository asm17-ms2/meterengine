import { METRICS, MODELS, TOKEN_UNIT_SIZE, type MetricKey, type SurfaceId } from "../../src/lib/catalog.ts";
import type { DemoCustomer } from "./customers.ts";

// 원, 100만 토큰당, 부가세 별도. 일괄 처리는 절반이다. 값이 없는 조합은 이벤트에서 늘 0이라 단가를 두지 않는다.
export const LIST_PRICES: Readonly<Record<string, Partial<Record<MetricKey, number>>>> = {
  "ab-5": { input: 4000, output: 20000, cacheRead: 400, cacheWrite: 5000 },
  "ab-5-mini": { input: 800, output: 4000, cacheRead: 80, cacheWrite: 1000 },
  "ab-5-nano": { input: 150, output: 600, cacheRead: 15, cacheWrite: 190 },
  "ab-embed-3": { input: 30 },
};
export const BATCH_RATIO = 0.5;
export const DISCOUNTED_MODEL = "ab-5";

export type PriceRatePlan = {
  metricCode: string;
  dimensionValues: { model: string; surface: SurfaceId };
  unitPrice: number;
  customerSlug?: string;
};

const roundPrice = (value: number) => Math.round(value * 100) / 100;

function rate(modelId: string, surface: SurfaceId, key: MetricKey, discount = 0): number | undefined {
  const listPrice = LIST_PRICES[modelId]?.[key];
  if (listPrice === undefined) return undefined;
  return roundPrice(listPrice * (surface === "batch" ? BATCH_RATIO : 1) * (1 - discount));
}

export function priceRatePlans(customers: readonly DemoCustomer[]): PriceRatePlan[] {
  const plans: PriceRatePlan[] = [];
  for (const model of MODELS) for (const surface of model.surfaces) for (const metric of METRICS) {
    const unitPrice = rate(model.id, surface, metric.key);
    if (unitPrice !== undefined) plans.push({ metricCode: metric.code, dimensionValues: { model: model.id, surface }, unitPrice });
  }
  const discounted = MODELS.find(model => model.id === DISCOUNTED_MODEL)!;
  for (const customer of customers) {
    if (!customer.discount) continue;
    for (const surface of discounted.surfaces) for (const metric of METRICS) {
      const unitPrice = rate(discounted.id, surface, metric.key, customer.discount);
      if (unitPrice !== undefined) plans.push({ metricCode: metric.code, dimensionValues: { model: discounted.id, surface }, unitPrice, customerSlug: customer.slug });
    }
  }
  return plans;
}

export type UsageRow = { customerSlug: string; metricCode: string; model: string; surface: SurfaceId; quantity: number };

// 백엔드처럼 줄마다 수량 x 단가를 10원 아래에서 버린다. 단가는 소수 둘째 자리까지라 정수로 바꿔 센다.
export function lineAmount(quantity: number, unitPrice: number): number {
  const numerator = BigInt(Math.round(quantity)) * BigInt(Math.round(unitPrice * 100));
  const tens = numerator / (BigInt(TOKEN_UNIT_SIZE) * BigInt(100) * BigInt(10));
  return Number(tens) * 10;
}

export function estimateCharges(rows: readonly UsageRow[], plans: readonly PriceRatePlan[]): Map<string, number> {
  const key = (customer: string | undefined, metric: string, model: string, surface: string) => `${customer ?? ""}|${metric}|${model}|${surface}`;
  const prices = new Map(plans.map(plan => [key(plan.customerSlug, plan.metricCode, plan.dimensionValues.model, plan.dimensionValues.surface), plan.unitPrice]));
  const amounts = new Map<string, number>();
  for (const row of rows) {
    if (row.quantity === 0) continue;
    const price = prices.get(key(row.customerSlug, row.metricCode, row.model, row.surface)) ?? prices.get(key(undefined, row.metricCode, row.model, row.surface));
    if (price === undefined) throw new Error(`단가 없는 조합: ${row.metricCode} ${row.model} ${row.surface}`);
    amounts.set(row.customerSlug, (amounts.get(row.customerSlug) ?? 0) + lineAmount(row.quantity, price));
  }
  return amounts;
}
