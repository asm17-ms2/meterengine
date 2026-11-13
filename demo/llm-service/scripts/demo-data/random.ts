import { createHash } from "node:crypto";

export type Random = {
  next(): number;
  between(min: number, max: number): number;
  integer(min: number, max: number): number;
  normal(): number;
  logNormal(median: number, sigma: number): number;
  pick<T>(weighted: ReadonlyArray<readonly [T, number]>): T;
};

export function digest(...parts: ReadonlyArray<string | number>): string {
  return createHash("sha256").update(parts.join("\u0000")).digest("hex");
}

// 씨앗과 열쇠 조각이 같으면 언제 돌려도 같은 수열이 나온다(sfc32).
export function createRandom(...parts: ReadonlyArray<string | number>): Random {
  const hex = digest(...parts);
  let a = parseInt(hex.slice(0, 8), 16) >>> 0;
  let b = parseInt(hex.slice(8, 16), 16) >>> 0;
  let c = parseInt(hex.slice(16, 24), 16) >>> 0;
  let d = parseInt(hex.slice(24, 32), 16) >>> 0;
  const next = () => {
    const t = (((a + b) >>> 0) + d) >>> 0;
    d = (d + 1) >>> 0;
    a = b ^ (b >>> 9);
    b = (c + (c << 3)) >>> 0;
    c = ((c << 21) | (c >>> 11)) >>> 0;
    c = (c + t) >>> 0;
    return t / 4294967296;
  };
  for (let index = 0; index < 12; index++) next();
  const random: Random = {
    next,
    between: (min, max) => min + (max - min) * next(),
    integer: (min, max) => min + Math.floor((max - min + 1) * next()),
    normal: () => {
      const u = 1 - next();
      const v = next();
      return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
    },
    logNormal: (median, sigma) => median * Math.exp(sigma * random.normal()),
    pick: weighted => {
      const total = weighted.reduce((sum, [, weight]) => sum + weight, 0);
      let point = next() * total;
      for (const [value, weight] of weighted) {
        point -= weight;
        if (point < 0) return value;
      }
      return weighted[weighted.length - 1][0];
    },
  };
  return random;
}
