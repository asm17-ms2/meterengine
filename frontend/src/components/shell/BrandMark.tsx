import type { CSSProperties } from "react";

// 막대 비율은 globals.css의 콘솔 헤더 마크를 기준으로 하고 scale은 크기만 키운다.
export function BrandMark({ scale = 1 }: { scale?: number }) {
  return <span className="meter-mark" aria-hidden="true" style={{ "--mark-scale": scale } as CSSProperties}><i /><i /><i /></span>;
}
