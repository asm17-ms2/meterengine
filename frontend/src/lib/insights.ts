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

/** 모든 고객의 청구 항목을 미터별로 더한다. 한도를 넘는 미터는 마지막 한 줄로 모은다. */
export function metricAmounts(customers: readonly { lines: readonly { billable_metric_code: string | null; amount: number }[] }[], limit: number): RankedAmount[] {
  const amountByLabel = new Map<string, number>();
  for (const line of customers.flatMap(customer => customer.lines)) {
    const label = line.billable_metric_code ?? UNMETERED_LABEL;
    amountByLabel.set(label, (amountByLabel.get(label) ?? 0) + line.amount);
  }
  const ranked = Array.from(amountByLabel, ([label, amount]) => ({ label, amount })).filter(row => row.amount > 0).sort(byAmountThenLabel);
  const rest = ranked.slice(limit);
  if (rest.length === 0) return ranked;
  return [...ranked.slice(0, limit), { label: `그 밖의 미터 ${rest.length}개`, amount: rest.reduce((sum, row) => sum + row.amount, 0) }];
}

/** 가장 큰 값을 1로 본 막대 길이. */
export function barRatio(value: number, max: number): number {
  return max > 0 && value > 0 ? value / max : 0;
}
