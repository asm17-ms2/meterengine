export type UsageEmptyKind = "no_customers" | "no_matching_customers" | "no_metric_lines" | "no_metrics" | "has_data";

// 고객이나 미터가 아직 없는 도입사에는 조건 초기화 대신 등록할 곳을 보여 준다.
export function usageEmptyKind({ customerCount, totalUnfiltered, metricLineCount, metricCode, hasFilter }: { customerCount: number; totalUnfiltered: number; metricLineCount: number; metricCode?: string; hasFilter: boolean }): UsageEmptyKind {
  if (customerCount === 0) return totalUnfiltered === 0 || !hasFilter ? "no_customers" : "no_matching_customers";
  if (metricLineCount === 0) return metricCode ? "no_metric_lines" : "no_metrics";
  return "has_data";
}
