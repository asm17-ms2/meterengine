import { UsageError } from "./exit-code.ts";

const MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/;
const KST_OFFSET_MS = 9 * 60 * 60 * 1000;

// 서버는 청구 월을 KST로 나눈다.
export function kstMonthOf(instant: Date): string {
  return new Date(instant.getTime() + KST_OFFSET_MS).toISOString().slice(0, 7);
}

export function previousMonth(month: string): string {
  const [year, value] = month.split("-").map(Number);
  return value === 1 ? `${year - 1}-12` : `${year}-${String(value - 1).padStart(2, "0")}`;
}

export function assertMonth(month: string): string {
  if (!MONTH_PATTERN.test(month)) throw new UsageError("invalid_month", "월은 YYYY-MM 형식입니다. 예: 2026-11");
  return month;
}
