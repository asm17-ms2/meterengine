// 브라우저와 서버가 같이 쓰는 토큰 어림 규칙과 캐시 기준.
export const PROMPT_CACHE_MIN_TOKENS = 1024;
export const PROMPT_CACHE_TTL_MS = 5 * 60 * 1000;

function isHangul(code: number): boolean {
  return (code >= 0xac00 && code <= 0xd7a3) || (code >= 0x1100 && code <= 0x11ff) || (code >= 0x3130 && code <= 0x318f);
}

// 한글은 한 글자에 1토큰, 나머지는 네 글자에 1토큰으로 어림한다.
export function estimateTokens(text: string): number {
  let hangul = 0;
  let other = 0;
  for (const char of text) {
    if (isHangul(char.codePointAt(0) ?? 0)) hangul++;
    else if (char.trim()) other++;
  }
  return hangul + Math.ceil(other / 4);
}
