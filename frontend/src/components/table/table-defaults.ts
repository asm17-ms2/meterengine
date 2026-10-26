import { HEADER_HEIGHT } from "@/components/shell/layout-sizes";

export const TABLE_STICKY = { offsetHeader: HEADER_HEIGHT };
export const MODAL_WIDTH = "min(520px, calc(100vw - 32px))";
export const DRAWER_SIZE = "min(440px, 100vw)";

const FRACTION_DIGITS = 9;
const FRACTION_PATTERN = /T\d{2}:\d{2}:\d{2}\.(\d+)/;

function fractionOf(timestamp: string): string {
  return (FRACTION_PATTERN.exec(timestamp)?.[1] ?? "").padEnd(FRACTION_DIGITS, "0");
}

export function koreanCompare(left: string, right: string): number {
  return left.localeCompare(right, "ko");
}

export function timestampCompare(left: string, right: string): number {
  const leftTime = Date.parse(left);
  const rightTime = Date.parse(right);
  if (Number.isNaN(leftTime) || Number.isNaN(rightTime)) {
    if (left === right) return 0;
    return left < right ? -1 : 1;
  }
  if (leftTime !== rightTime) return leftTime - rightTime;
  const leftFraction = fractionOf(left);
  const rightFraction = fractionOf(right);
  if (leftFraction === rightFraction) return 0;
  return leftFraction < rightFraction ? -1 : 1;
}
