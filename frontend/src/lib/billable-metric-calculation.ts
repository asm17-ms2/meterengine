export const METRIC_AGGREGATIONS = [
  { value: "sum", label: "합계 (SUM)", description: "숫자 속성의 값을 모두 더합니다." },
  { value: "count", label: "이벤트 수 (COUNT)", description: "해당 이벤트 타입의 모든 이벤트를 셉니다. 속성 값 유무와 관계없습니다." },
  { value: "count_distinct", label: "고유 값 수 (COUNT DISTINCT)", description: "문자열, 숫자, 불리언 속성에서 중복을 뺀 값의 수입니다. 없는 값과 null, 객체, 배열은 제외합니다." },
  { value: "min", label: "최솟값 (MIN)", description: "숫자 속성 중 가장 작은 값입니다." },
  { value: "max", label: "최댓값 (MAX)", description: "숫자 속성 중 가장 큰 값입니다." },
  { value: "avg", label: "평균 (AVG)", description: "숫자 속성 값의 산술평균입니다." },
  { value: "first", label: "첫 값 (FIRST)", description: "발생 시각이 가장 이른 이벤트의 숫자 값입니다. 시각이 같으면 거래 ID 순서로 정합니다." },
  { value: "last", label: "마지막 값 (LAST)", description: "발생 시각이 가장 늦은 이벤트의 숫자 값입니다. 시각이 같으면 거래 ID 순서로 정합니다." },
  { value: "median", label: "중앙값 (MEDIAN)", description: "숫자 속성을 정렬한 가운데 값입니다. 짝수 개면 가운데 두 값의 평균입니다." },
  { value: "percentile", label: "백분위수 (PERCENTILE)", description: "숫자 속성을 정렬해 지정한 백분위의 값을 구합니다. 두 값 사이면 선형 보간합니다." },
] as const;

export type MetricAggregation = (typeof METRIC_AGGREGATIONS)[number]["value"];
export type MetricRoundingMode = "none" | "ceil" | "floor" | "round";

export type MetricCalculation = {
  aggregation: string;
  target_property?: string | null;
  percentile?: number | null;
  unit_size?: number;
  rounding_mode?: MetricRoundingMode;
  rounding_precision?: number;
};

export const METRIC_ROUNDING_OPTIONS = [
  { value: "none", label: "추가 반올림 없음" },
  { value: "ceil", label: "올림 (CEIL)" },
  { value: "floor", label: "내림 (FLOOR)" },
  { value: "round", label: "반올림 (ROUND)" },
] satisfies { value: MetricRoundingMode; label: string }[];

export function isMetricAggregation(value: string): value is MetricAggregation {
  return METRIC_AGGREGATIONS.some((option) => option.value === value);
}

export function isMetricRoundingMode(value: string): value is MetricRoundingMode {
  return METRIC_ROUNDING_OPTIONS.some((option) => option.value === value);
}

export function metricAggregationLabel(aggregation: string, percentile?: number | null): string {
  const label = METRIC_AGGREGATIONS.find((option) => option.value === aggregation)?.label ?? aggregation;
  return aggregation === "percentile" && percentile != null ? `${label} P${percentile}` : label;
}

export function metricCalculationFormula(calculation: MetricCalculation): string {
  const target = calculation.aggregation === "count" ? "events" : calculation.target_property || "속성";
  const aggregate = calculation.aggregation === "percentile"
    ? `P${calculation.percentile ?? "?"}(${target})`
    : `${calculation.aggregation.toUpperCase()}(${target})`;
  const unit = calculation.unit_size ?? 1;
  const scaled = unit === 1 ? aggregate : `${aggregate} / ${unit}`;
  const rounding = calculation.rounding_mode ?? "none";
  return rounding === "none" ? scaled : `${rounding.toUpperCase()}(${scaled}, ${calculation.rounding_precision ?? 0}자리)`;
}

export function metricCalculationExample(calculation: MetricCalculation): string {
  const values: Record<string, number> = { sum: 60, count: 3, count_distinct: 3, min: 10, max: 30, avg: 20, first: 10, last: 30, median: 20 };
  const raw = calculation.aggregation === "percentile" ? 10 + (calculation.percentile ?? 50) / 5 : values[calculation.aggregation];
  const unit = calculation.unit_size ?? 1;
  if (raw == null || !Number.isFinite(unit) || unit <= 0) return "과금 단위 크기를 입력하면 결과를 확인할 수 있습니다.";
  const scaled = raw / unit;
  const factor = 10 ** (calculation.rounding_precision ?? 0);
  const mode = calculation.rounding_mode ?? "none";
  const quantity = mode === "none" ? scaled : Math[mode](scaled * factor) / factor;
  const formatted = new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 12 }).format(quantity);
  return calculation.aggregation === "count" ? `예시: 이벤트 3건 -> 과금 수량 ${formatted}` : `예시: 발생 순서대로 10, 20, 30 -> 과금 수량 ${formatted}`;
}
