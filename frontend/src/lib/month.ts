const MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/;

const MONTH_OPTION_COUNT = 3;

const FINALIZATION_DAY_OF_MONTH = 1;

const FINALIZATION_HOUR_KST = 14;

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

/** 지난달 분이 아직 자동 확정 시각(매월 1일 14:00 KST) 전인지. */
export function isAwaitingFinalization(month: string, now: Date = new Date()): boolean {
  if (month !== shiftMonth(formatKstMonth(now), -1)) return false;
  const parts: Record<string, string> = {};
  const formatter = new Intl.DateTimeFormat("en-US", {
    timeZone: "Asia/Seoul",
    day: "numeric",
    hour: "numeric",
    hour12: false,
  });
  for (const part of formatter.formatToParts(now)) parts[part.type] = part.value;
  const hour = Number(parts.hour) % 24;
  return Number(parts.day) === FINALIZATION_DAY_OF_MONTH && hour < FINALIZATION_HOUR_KST;
}

/** `yyyy-MM`에서 n개월 뺀 값. */
export function shiftMonth(month: string, delta: number): string {
  const [year, monthOfYear] = month.split("-").map(Number);
  const shiftedDate = new Date(Date.UTC(year, monthOfYear - 1 + delta, 1));
  const shiftedYear = shiftedDate.getUTCFullYear();
  const shiftedMonth = String(shiftedDate.getUTCMonth() + 1).padStart(2, "0");
  return `${shiftedYear}-${shiftedMonth}`;
}

export type MonthOption = { value: string; label: string };

export function buildMonthOptions(now: Date = new Date()): MonthOption[] {
  const currentMonth = formatKstMonth(now);
  return Array.from({ length: MONTH_OPTION_COUNT }, (_, index) => {
    const value = shiftMonth(currentMonth, -index);
    return { value, label: index === 0 ? `${value} (이번 달)` : value };
  });
}

export function buildMonthOptionsFor(
  selected: string,
  now: Date = new Date(),
): MonthOption[] {
  const monthOptions = buildMonthOptions(now);
  if (monthOptions.some((option) => option.value === selected)) return monthOptions;
  return [...monthOptions, { value: selected, label: selected }].sort((a, b) =>
    b.value.localeCompare(a.value),
  );
}

/** 값이 없거나 `yyyy-MM`이 아니면 undefined. 전체 기간이 기본인 화면이 쓴다. */
export function readOptionalMonth(
  raw: string | string[] | undefined,
): string | undefined {
  const monthParam = Array.isArray(raw) ? raw[0] : raw;
  return monthParam && MONTH_PATTERN.test(monthParam) ? monthParam : undefined;
}

export function readMonth(
  raw: string | string[] | undefined,
  now: Date = new Date(),
): string {
  return readOptionalMonth(raw) ?? formatKstMonth(now);
}
