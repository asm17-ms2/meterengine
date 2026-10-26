const MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/;

export const ALL_MONTHS = "all";

export function isMonth(value: string): boolean {
  return MONTH_PATTERN.test(value);
}

/** KST 기준 현재 월을 `yyyy-MM`으로. */
export function formatKstMonth(now: Date = new Date()): string {
  const isoDate = new Intl.DateTimeFormat("sv-SE", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(now);
  return isoDate.slice(0, 7);
}

/** `yyyy-MM`에서 n개월 뺀 값. */
export function shiftMonth(month: string, delta: number): string {
  const [year, monthOfYear] = month.split("-").map(Number);
  const shiftedDate = new Date(Date.UTC(year, monthOfYear - 1 + delta, 1));
  const shiftedYear = shiftedDate.getUTCFullYear();
  const shiftedMonth = String(shiftedDate.getUTCMonth() + 1).padStart(2, "0");
  return `${shiftedYear}-${shiftedMonth}`;
}

export function readMonth(
  raw: string | string[] | undefined,
  now: Date = new Date(),
): string {
  const monthParam = Array.isArray(raw) ? raw[0] : raw;
  return monthParam && MONTH_PATTERN.test(monthParam)
    ? monthParam
    : formatKstMonth(now);
}

export function readMonthOrAll(
  raw: string | string[] | undefined,
  now: Date = new Date(),
): string {
  const monthParam = Array.isArray(raw) ? raw[0] : raw;
  return monthParam === ALL_MONTHS ? ALL_MONTHS : readMonth(raw, now);
}
