const MONEY = new Intl.NumberFormat("ko-KR");
// 청구할 때 기본료와 최소 사용료는 10원 미만을 버린다. 청구 계산과 같은 규칙이다.
const BILLING_UNIT_WON = 10;

export function planAmount(value: number): string {
  const billed = value - (value % BILLING_UNIT_WON);
  return billed === value ? `${MONEY.format(value)}원` : `${MONEY.format(value)}원 (청구 ${MONEY.format(billed)}원)`;
}
