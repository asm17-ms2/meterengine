export function isZeroPriceRate(model: string, unitPrice: string, tierPrices: string[]): boolean {
  if (model === "per_unit") return Number(unitPrice) === 0;
  return tierPrices.length > 0 && tierPrices.every(price => Number(price) === 0);
}

const MAX_UNIT_PRICE_FRACTION_DIGITS = 10;

// 화면은 단가를 소수 10자리까지 그대로 보여 주므로, 그보다 정밀하거나 정확히 담을 수 없는 큰 단가는 API로 등록하게 한다.
export function isRepresentableUnitPrice(text: string): boolean {
  const value = Number(text);
  if (!Number.isFinite(value) || Math.abs(value) > Number.MAX_SAFE_INTEGER) return false;
  const plain = /^-?\d+(?:\.(\d+))?$/.exec(text.trim());
  if (!plain) return false;
  return (plain[1]?.length ?? 0) <= MAX_UNIT_PRICE_FRACTION_DIGITS;
}
