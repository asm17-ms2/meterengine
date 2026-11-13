"use client";

import { useEffect, useState } from "react";

const FRAME_MS = 30;
const MAX_FRAMES = 90;

function prefersReducedMotion(): boolean {
  return typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

// 서버가 돌려준 완성된 응답을 화면에서만 조금씩 드러낸다. 읽기 도구에는 처음부터 전체를 준다.
export function StreamingText({ text, isAnimated, className }: { text: string; isAnimated: boolean; className?: string }) {
  const characters = Array.from(text);
  const [shown, setShown] = useState(() => (isAnimated && !prefersReducedMotion() ? 0 : characters.length));
  const length = characters.length;
  useEffect(() => {
    if (shown >= length) return;
    const step = Math.max(2, Math.ceil(length / MAX_FRAMES));
    const timer = window.setTimeout(() => setShown(current => Math.min(length, current + step)), FRAME_MS);
    return () => window.clearTimeout(timer);
  }, [shown, length]);
  const isDone = shown >= length;
  return <div className={className}>
    <span aria-hidden={isDone ? undefined : true}>{characters.slice(0, shown).join("")}{!isDone && <span className="stream-caret" />}</span>
    {!isDone && <span className="screen-reader-only">{text}</span>}
  </div>;
}
