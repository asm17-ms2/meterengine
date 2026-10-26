import { useEffect } from "react";

const MAX_FOCUS_FRAMES = 10;

type Focusable = { focus: () => void };

type FocusTarget = { current: Focusable | null };

function toElement(target: Focusable): Element | null {
  if (target instanceof Element) return target;
  const input = (target as { input?: unknown }).input;
  return input instanceof Element ? input : null;
}

export function focusInFrames(getTarget: () => Focusable | null | undefined): () => void {
  let frame = 0;
  let frameId = 0;
  const focusTarget = () => {
    const focusable = getTarget();
    if (focusable) {
      focusable.focus();
      const element = toElement(focusable);
      if (element === null || document.activeElement === element) return;
    }
    frame += 1;
    if (frame < MAX_FOCUS_FRAMES) frameId = requestAnimationFrame(focusTarget);
  };
  frameId = requestAnimationFrame(focusTarget);
  return () => cancelAnimationFrame(frameId);
}

export function useInitialFocus(target: FocusTarget, isActive = true) {
  useEffect(() => {
    if (!isActive) return;
    return focusInFrames(() => target.current);
  }, [target, isActive]);
}
