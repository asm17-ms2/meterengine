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
  return value.toLocaleString("ko-KR", { maximumFractionDigits: 10 });
}

export function formatKrw(value: number): string {
  return `${formatDecimal(value)}원`;
}
