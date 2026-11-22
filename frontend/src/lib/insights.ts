export type RankedAmount = { label: string; amount: number };

const UNMETERED_LABEL = "기본료와 최소 사용료";

function byAmountThenLabel(a: RankedAmount, b: RankedAmount) {
  return b.amount - a.amount || a.label.localeCompare(b.label, "ko");
}

/** 청구 예정액이 큰 고객부터. 청구할 금액이 없는 고객은 뺀다. */
export function topCustomerAmounts(customers: readonly { customer_name: string; amount: number }[], limit: number): RankedAmount[] {
  return customers
    .filter(customer => customer.amount > 0)
    .map(customer => ({ label: customer.customer_name, amount: customer.amount }))
    .sort(byAmountThenLabel)
    .slice(0, limit);
}

/** 모든 고객의 청구 항목을 과금 미터별로 더한다. 한도를 넘는 과금 미터는 마지막 한 줄로 모은다. */
export function metricAmounts(customers: readonly { lines: readonly { billable_metric_code: string | null; amount: number }[] }[], limit: number): RankedAmount[] {
  const amountByLabel = new Map<string, number>();
  for (const line of customers.flatMap(customer => customer.lines)) {
    const label = line.billable_metric_code ?? UNMETERED_LABEL;
    amountByLabel.set(label, (amountByLabel.get(label) ?? 0) + line.amount);
  }
  const ranked = Array.from(amountByLabel, ([label, amount]) => ({ label, amount })).filter(row => row.amount > 0).sort(byAmountThenLabel);
  const rest = ranked.slice(limit);
  if (rest.length === 0) return ranked;
  return [...ranked.slice(0, limit), { label: `그 밖의 과금 미터 ${rest.length}개`, amount: rest.reduce((sum, row) => sum + row.amount, 0) }];
}

/** 가장 큰 값을 1로 본 막대 길이. */
export function barRatio(value: number, max: number): number {
  return max > 0 && value > 0 ? value / max : 0;
}

/** 그래프의 왼쪽 절반에 있는 기둥은 "start", 오른쪽 절반은 "end". 글자와 말풍선을 그래프 안쪽으로 붙일 쪽이다. */
export function edgeSide(index: number, count: number): "start" | "end" {
  return index < count / 2 ? "start" : "end";
}

/** 한 달 매출의 세 조각(원, 공급가액). 받은 금액, 받을 금액, 아직 발행하지 않은 청구 예정액이다. */
export type MonthlyRevenue = { month: string; collected: number; outstanding: number; draft: number };

export function revenueTotal(revenue: MonthlyRevenue): number {
  return revenue.collected + revenue.outstanding + revenue.draft;
}

function monthNumber(month: string): number {
  return Number(month.slice(5));
}

/**
 * 달별 매출을 두 물음에 답하는 문장으로 적는다. 마지막 달이 앞 달에 견줘 얼마인가, 받을 금액이 남은 달이 있는가.
 * inProgressMonth는 아직 끝나지 않은 달(yyyy-MM)이고, 없으면 빈 글자다.
 */
export function monthlyRevenueSummary(revenues: readonly MonthlyRevenue[], inProgressMonth: string, formatValue: (value: number) => string): string {
  if (revenues.length === 0) return "";
  const last = revenues[revenues.length - 1];
  const previousTotal = revenues.length > 1 ? revenueTotal(revenues[revenues.length - 2]) : 0;
  const soFar = last.month === inProgressMonth ? "지금까지 " : "";
  const comparison = previousTotal > 0 ? `이고 ${monthNumber(revenues[revenues.length - 2].month)}월의 ${Math.round(revenueTotal(last) / previousTotal * 100)}%예요.` : "이에요.";
  const owed = revenues.filter(revenue => revenue.outstanding > 0).map(revenue => `${monthNumber(revenue.month)}월 ${formatValue(revenue.outstanding)}`);
  return `${monthNumber(last.month)}월은 ${soFar}${formatValue(revenueTotal(last))}${comparison} ${owed.length > 0 ? `받을 금액은 ${owed.join(", ")}이에요.` : "받을 금액은 없어요."}`;
}
