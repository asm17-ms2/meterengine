"use client";

import { useSyncExternalStore } from "react";
import { Button } from "antd";

const PREFERENCE_KEY = "meterengine-guide-mode";
let isFallback = false;

function subscribe(listener: () => void) {
  window.addEventListener("storage", listener);
  window.addEventListener(PREFERENCE_KEY, listener);
  return () => {
    window.removeEventListener("storage", listener);
    window.removeEventListener(PREFERENCE_KEY, listener);
  };
}

function readPreference() {
  try { return localStorage.getItem(PREFERENCE_KEY) === "on"; } catch { return isFallback; }
}

export function useGuideMode() {
  const isGuideMode = useSyncExternalStore(subscribe, readPreference, () => false);
  function setGuideMode(next: boolean) {
    isFallback = next;
    try { localStorage.setItem(PREFERENCE_KEY, next ? "on" : "off"); } catch {}
    window.dispatchEvent(new Event(PREFERENCE_KEY));
  }
  return { isGuideMode, setGuideMode };
}

export function GuideModeToggle() {
  const { isGuideMode, setGuideMode } = useGuideMode();
  return <Button aria-pressed={isGuideMode} onClick={() => setGuideMode(!isGuideMode)}>{isGuideMode ? "업무 화면으로" : "시연 안내 보기"}</Button>;
}

export function GuideOnly({ children }: { children: React.ReactNode }) {
  const { isGuideMode } = useGuideMode();
  return isGuideMode ? children : null;
}
