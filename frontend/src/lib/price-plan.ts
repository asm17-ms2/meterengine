type PlanVersion = { effective_from: string; effective_until: string | null; archived_from?: string | null };

// 청구(ChargeService)와 같은 규칙: 그 달에 걸린 가장 늦은 버전이 보관 효력으로 멈췄으면 이전 버전으로 돌아가지 않는다.
export function appliedPricePlan<T extends PlanVersion>(plans: T[], month: string): T | undefined {
  for (const plan of [...plans].sort((left, right) => right.effective_from.localeCompare(left.effective_from))) {
    if (plan.effective_from > month || (plan.effective_until && plan.effective_until <= month)) continue;
    if (!plan.archived_from || plan.archived_from > month) return plan;
    if (plan.effective_from < plan.archived_from) return undefined;
  }
  return undefined;
}
