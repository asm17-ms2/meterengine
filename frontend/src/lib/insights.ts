export type RankedAmount = { label: string; amount: number };

const UNMETERED_LABEL = "기본료와 최소 사용료";
const SHARE_UNDER_ONE_PERCENT = "1% 미만";
const SUPPLY_BASIS = "공급가액 기준이에요.";

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

/** 전체에서 차지하는 몫. 전체나 값이 없으면 0. */
export function shareOfTotal(value: number, total: number): number {
  return total > 0 && value > 0 ? value / total : 0;
}

/** 몫을 정수 퍼센트로. 구하지 못한 몫은 빈 글자로 둔다. */
export function formatShare(share: number): string {
  if (share <= 0) return "";
  const percent = Math.round(share * 100);
  return percent === 0 ? SHARE_UNDER_ONE_PERCENT : `${percent}%`;
}

/** 순위에 나온 고객이 전체에서 차지하는 몫을 한 문장으로. 청구할 고객이 모두 순위에 나오면 몫 대신 고객 수를 적는다. */
export function topCustomerSummary(customers: readonly { customer_name: string; amount: number }[], limit: number, total: number): string {
  const billableCount = customers.filter(customer => customer.amount > 0).length;
  const listedAmount = topCustomerAmounts(customers, limit).reduce((sum, row) => sum + row.amount, 0);
  const share = formatShare(shareOfTotal(listedAmount, total));
  if (billableCount <= limit || !share) return `청구 예정액이 있는 고객은 ${billableCount}곳이에요. ${SUPPLY_BASIS}`;
  return `청구 예정액이 큰 ${limit}곳이 전체의 ${share}${share.endsWith("%") ? "예요" : "이에요"}. ${SUPPLY_BASIS}`;
}
