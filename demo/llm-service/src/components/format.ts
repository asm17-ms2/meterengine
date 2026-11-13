import { METRICS, MODELS, SURFACES } from "@/lib/catalog";
import type { MetricKey, SurfaceId } from "@/lib/catalog";

export const MONEY = new Intl.NumberFormat("ko-KR", { style: "currency", currency: "KRW", maximumFractionDigits: 2 });
const QUANTITY = new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 12 });
const INTEGER = new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 0 });
const DATE_TIME = new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", month: "long", day: "numeric", hour: "2-digit", minute: "2-digit", hour12: false });
const SHORT_DATE = new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", month: "numeric", day: "numeric" });

export const METRIC_KEYS: readonly MetricKey[] = METRICS.map(metric => metric.key);
export const PAYMENT_LABELS: Record<string, string> = { not_required: "낼 금액 없음", unpaid: "납부 대기", pending: "결제 확인 중", paid: "납부 완료", failed: "결제 실패", partially_canceled: "일부 취소", canceled: "전액 취소" };

export function formatQuantity(value: number): string {
  if (value !== 0 && Math.abs(value) < 0.0000000000005) return value.toExponential(6);
  return QUANTITY.format(value);
}

export function formatTokens(value: number): string {
  return INTEGER.format(value);
}

export function formatDate(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "시각 확인 필요" : DATE_TIME.format(date);
}

export function formatShortDate(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "" : SHORT_DATE.format(date);
}

export function formatPrice(value: number | null | undefined): string {
  return value == null ? "미확인" : MONEY.format(value);
}

// 모델 설명 옆에 붙는 한 줄 단가. 요금표를 못 읽었으면 그렇다고만 쓴다.
export function priceSummary(prices: Partial<Record<MetricKey, number | null>> | undefined, keys: readonly MetricKey[]): string {
  if (!prices || keys.some(key => prices[key] == null)) return "단가는 요금표를 불러온 뒤 보여 드려요.";
  return `100만 토큰당 ${keys.map(key => `${metricShortName(key)} ${MONEY.format(prices[key]!)}`).join(", ")}`;
}

export function modelName(modelId: string): string {
  return MODELS.find(model => model.id === modelId)?.name ?? modelId;
}

export function surfaceName(surface: SurfaceId | string): string {
  return SURFACES.find(item => item.id === surface)?.name ?? surface;
}

export function metricName(key: MetricKey): string {
  return METRICS.find(metric => metric.key === key)?.name ?? key;
}

export function metricShortName(key: MetricKey): string {
  return metricName(key).replace(/ 토큰$/, "");
}
