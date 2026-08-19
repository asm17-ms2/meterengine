/**
 * 표시용 포맷터.
 *
 * 전부 KST(Asia/Seoul) 고정이다. 백엔드가 월 경계를 KST 자정으로 판정하므로
 * (MS2-121 팀 정책) 화면도 같은 기준으로 보여야 숫자와 날짜가 어긋나지 않는다.
 *
 * 이 함수들은 Server Component에서만 부른다. 클라이언트에서 부르면 서버와
 * 브라우저의 시간대가 달라 하이드레이션이 어긋난다. 클라이언트 컴포넌트에는
 * 포맷이 끝난 문자열을 넘긴다.
 */

const KST = "Asia/Seoul";

const dateTimeParts = new Intl.DateTimeFormat("ko-KR", {
  timeZone: KST,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hour12: false,
});

function partsOf(value: Date): Record<string, string> {
  const out: Record<string, string> = {};
  for (const p of dateTimeParts.formatToParts(value)) out[p.type] = p.value;
  // Intl은 자정을 '24'로 낼 수 있다 (hour12: false + hourCycle 기본값).
  if (out.hour === "24") out.hour = "00";
  return out;
}

/** `2026-08-09 14:11:02` - 표 셀에 쓰는 형태. */
export function formatKstDateTime(iso: string): string {
  const p = partsOf(new Date(iso));
  return `${p.year}-${p.month}-${p.day} ${p.hour}:${p.minute}:${p.second}`;
}

/** `2026-08-09 14:12:04 KST` - 필터 행의 조회 시각. */
export function formatKstStamp(value: Date): string {
  return `${formatKstDateTime(value.toISOString())} KST`;
}

/** `2026년 8월` - 화면 제목용. `yyyy-MM`을 받는다. */
export function formatKoreanMonth(month: string): string {
  const [year, mm] = month.split("-");
  return `${year}년 ${Number(mm)}월`;
}

/** 천 단위 구분. 표의 수량 칸은 tabular-nums와 함께 쓴다. */
export function formatNumber(value: number): string {
  return value.toLocaleString("ko-KR");
}

/**
 * 소수가 올 수 있는 값(집계 수량, 단가). 서버가 준 자릿수만 보여주고,
 * toFixed로 가짜 정밀도를 만들지 않는다.
 */
export function formatDecimal(value: number): string {
  return value.toLocaleString("ko-KR", { maximumFractionDigits: 10 });
}

export function formatKrw(value: number): string {
  return `${formatDecimal(value)}원`;
}
