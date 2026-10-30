export function isZeroPriceRate(model: string, unitPrice: string, tierPrices: string[]): boolean {
  if (model === "per_unit") return Number(unitPrice) === 0;
  return tierPrices.length > 0 && tierPrices.every(price => Number(price) === 0);
}

const MAX_UNIT_PRICE_FRACTION_DIGITS = 10;
export const UNIT_PRICE_PRECISION_MESSAGE = "화면에서 정확히 보일 수 없는 단가입니다. 소수 10자리를 넘거나 자릿수가 많은 단가는 API로 등록하세요.";
const MAX_DECIMAL_EXPONENT = 400;

// 화면은 단가를 소수 10자리까지 보여 주므로, 그 표기로 되돌렸을 때 입력한 값과 같은 단가만 받고 나머지는 API로 등록하게 한다.
export function isRepresentableUnitPrice(text: string): boolean {
  const entered = canonicalDecimal(text);
  return entered !== undefined && canonicalDecimal(Number(text).toFixed(MAX_UNIT_PRICE_FRACTION_DIGITS)) === entered;
}

function canonicalDecimal(text: string): string | undefined {
  const parts = /^([+-]?)(\d*)(?:\.(\d*))?(?:[eE]([+-]?\d+))?$/.exec(text.trim());
  if (!parts) return undefined;
  const digits = `${parts[2]}${parts[3] ?? ""}`;
  const exponent = Number(parts[4] ?? 0);
  if (!digits || Math.abs(exponent) > MAX_DECIMAL_EXPONENT) return undefined;
  const point = parts[2].length + exponent;
  const padded = point <= 0 ? `${"0".repeat(1 - point)}${digits}` : digits.padEnd(point, "0");
  const splitAt = Math.max(point, 1);
  const whole = padded.slice(0, splitAt).replace(/^0+(?=\d)/, "");
  const fraction = padded.slice(splitAt).replace(/0+$/, "");
  const magnitude = fraction ? `${whole}.${fraction}` : whole;
  return parts[1] === "-" && /[1-9]/.test(magnitude) ? `-${magnitude}` : magnitude;
}
