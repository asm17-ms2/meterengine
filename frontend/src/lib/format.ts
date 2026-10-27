const KST = "Asia/Seoul";

const KST_DATE_TIME_FORMAT = new Intl.DateTimeFormat("ko-KR", {
  timeZone: KST,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hour12: false,
});

function toDateTimeParts(value: Date): Record<string, string> {
  const parts: Record<string, string> = {};
  for (const part of KST_DATE_TIME_FORMAT.formatToParts(value)) parts[part.type] = part.value;
  if (parts.hour === "24") parts.hour = "00";
  return parts;
}

/** `2026-08-09 14:11:02` - 표 셀에 쓰는 형태. */
export function formatKstDateTime(iso: string): string {
  const parts = toDateTimeParts(new Date(iso));
  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}`;
}

export function formatKstDate(iso: string): string {
  const parts = toDateTimeParts(new Date(iso));
  return `${parts.year}-${parts.month}-${parts.day}`;
}

/** `2026-08-09 14:12:04 KST` - 필터 행의 조회 시각. */
export function formatKstStamp(value: Date): string {
  return `${formatKstDateTime(value.toISOString())} KST`;
}

export function formatKoreanMonth(month: string): string {
  const [year, monthOfYear] = month.split("-");
  return `${year}년 ${Number(monthOfYear)}월`;
}

export function formatDecimal(value: number): string {
  if (value !== 0 && Math.abs(value) < 0.00000000005) return value.toExponential(10);
  return value.toLocaleString("ko-KR", { maximumFractionDigits: 10 });
}

export function formatQuantityBasis(numerator?: number | null, denominator?: number | null): string | undefined {
  if (numerator == null || denominator == null || denominator <= 0 || denominator === 1 || Number.isInteger(numerator / denominator)) return undefined;
  return `계산 기준: ${numerator} / ${denominator}`;
}

export function formatKrw(value: number): string {
  return `${formatDecimal(value)}원`;
}

/** `model=claude-opus-5, region=kr` - 조합을 한 줄 글로 읽을 때 쓰는 형태. */
export function formatDimensionValues(dimensionValues: Record<string, unknown>): string {
  return Object.entries(dimensionValues)
    .map(([key, value]) => `${key}=${value}`)
    .join(", ");
}

export type DimensionValueSlot = {
  value: string;
  hint: string;
  isMissing: boolean;
};

/** 조합을 키마다 한 칸으로 나눈다. 칸에는 값만, 툴팁에는 키와 값을 쓴다. */
export function toDimensionValueSlots(dimensionValues: Record<string, unknown>): DimensionValueSlot[] {
  return Object.entries(dimensionValues).map(([key, value]) => {
    const isMissing = value === null || value === undefined;
    return {
      value: isMissing ? "(값 없음)" : String(value),
      hint: `${key}: ${isMissing ? "값 없음" : String(value)}`,
      isMissing,
    };
  });
}

export function formatTargetPropertyTooltip(targetProperty: string | null): string | undefined {
  return targetProperty ? `집계 대상 속성: ${targetProperty}` : undefined;
}
