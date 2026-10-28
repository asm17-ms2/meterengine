export function isZeroPriceRate(model: string, unitPrice: string, tierPrices: string[]): boolean {
  if (model === "per_unit") return Number(unitPrice) === 0;
  return tierPrices.length > 0 && tierPrices.every(price => Number(price) === 0);
}
