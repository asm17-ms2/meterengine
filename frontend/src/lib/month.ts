/**
 * 조회 월(`yyyy-MM`) 다루기. 기준 시간대는 KST다 (백엔드의 월 경계 판정과 같다).
 *
 * 디자인 프로토타입은 2026-08 / 07 / 06을 하드코딩했는데, 그대로 두면 다음 달에
 * 틀린 목록이 된다. 현재 KST 월을 기준으로 생성한다.
 */

const MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/;

/** 최근 몇 달을 고를 수 있게 할지. 디자인의 select 옵션 개수와 같다. */
const MONTH_OPTION_COUNT = 3;

/** KST 기준 현재 월을 `yyyy-MM`으로. */
export function currentMonth(now: Date = new Date()): string {
  // sv-SE 로캘이 ISO 형태(YYYY-MM-DD)를 내주므로 앞 7자를 자르면 yyyy-MM이 된다.
  const ymd = new Intl.DateTimeFormat("sv-SE", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(now);
  return ymd.slice(0, 7);
}

/** `yyyy-MM`에서 n개월 뺀 값. */
export function shiftMonth(month: string, delta: number): string {
  const [year, mm] = month.split("-").map(Number);
  // Date.UTC의 월은 0-based. 넘치거나 모자란 월은 알아서 연도로 넘어간다.
  const shifted = new Date(Date.UTC(year, mm - 1 + delta, 1));
  const y = shifted.getUTCFullYear();
  const m = String(shifted.getUTCMonth() + 1).padStart(2, "0");
  return `${y}-${m}`;
}

export type MonthOption = { value: string; label: string };

/** select에 넣을 최근 N개월. 이번 달에는 `(이번 달)`을 붙인다. */
export function monthOptions(now: Date = new Date()): MonthOption[] {
  const current = currentMonth(now);
  return Array.from({ length: MONTH_OPTION_COUNT }, (_, i) => {
    const value = shiftMonth(current, -i);
    return { value, label: i === 0 ? `${value} (이번 달)` : value };
  });
}

/**
 * 선택된 월이 최근 N개월 밖이면(주소창에 직접 넣은 경우) 목록에 끼워 넣는다.
 * 그러지 않으면 select가 아무것도 선택되지 않은 상태로 보인다.
 */
export function monthOptionsFor(
  selected: string,
  now: Date = new Date(),
): MonthOption[] {
  const options = monthOptions(now);
  if (options.some((o) => o.value === selected)) return options;
  return [...options, { value: selected, label: selected }].sort((a, b) =>
    b.value.localeCompare(a.value),
  );
}

/**
 * 쿼리스트링의 month를 읽는다. 형식이 어긋나면 이번 달로 떨어뜨린다.
 * 조회 전용 화면이라 400을 띄우는 것보다 기본값으로 무언가 보여주는 편이 낫다.
 */
export function readMonth(
  raw: string | string[] | undefined,
  now: Date = new Date(),
): string {
  const value = Array.isArray(raw) ? raw[0] : raw;
  return value && MONTH_PATTERN.test(value) ? value : currentMonth(now);
}
