"use client";

import Script from "next/script";
import { useEffect, useRef, useState } from "react";

type TurnstileRenderOptions = {
  sitekey: string;
  action: string;
  language: string;
  callback: (token: string) => void;
  // eslint-disable-next-line @typescript-eslint/naming-convention -- Turnstile render 옵션 이름
  "error-callback": () => void;
  // eslint-disable-next-line @typescript-eslint/naming-convention -- Turnstile render 옵션 이름
  "expired-callback": () => void;
  "response-field": boolean;
};

type TurnstileApi = {
  render: (container: HTMLElement, options: TurnstileRenderOptions) => string | undefined;
  reset: (widgetId: string) => void;
  remove: (widgetId: string) => void;
};

declare global {
  interface Window { turnstile?: TurnstileApi }
}

// Cloudflare가 이 주소의 스크립트만 지원한다. 복사해 두거나 프록시하지 않는다.
const SCRIPT_URL = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";

// 서버가 토큰을 한 번만 받으므로, 제출 결과가 돌아올 때마다(resetKey가 바뀔 때) 위젯을 다시 풀게 한다.
export function TurnstileWidget({ siteKey, action, resetKey }: { siteKey: string; action: string; resetKey: unknown }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const widgetIdRef = useRef<string | undefined>(undefined);
  const [isScriptLoaded, setIsScriptLoaded] = useState(() => typeof window !== "undefined" && Boolean(window.turnstile));
  const [token, setToken] = useState("");
  const [hasError, setHasError] = useState(false);

  useEffect(() => {
    const turnstile = window.turnstile;
    const container = containerRef.current;
    if (!isScriptLoaded || !turnstile || !container) return;
    widgetIdRef.current = turnstile.render(container, {
      sitekey: siteKey,
      action,
      language: "ko",
      callback: value => { setToken(value); setHasError(false); },
      // eslint-disable-next-line @typescript-eslint/naming-convention -- Turnstile render 옵션 이름
      "error-callback": () => { setToken(""); setHasError(true); },
      // eslint-disable-next-line @typescript-eslint/naming-convention -- Turnstile render 옵션 이름
      "expired-callback": () => setToken(""),
      "response-field": false,
    });
    return () => {
      if (widgetIdRef.current) turnstile.remove(widgetIdRef.current);
      widgetIdRef.current = undefined;
    };
  }, [isScriptLoaded, siteKey, action]);

  const isFirstResetRef = useRef(true);
  useEffect(() => {
    if (isFirstResetRef.current) { isFirstResetRef.current = false; return; }
    if (widgetIdRef.current) window.turnstile?.reset(widgetIdRef.current);
    setToken("");
  }, [resetKey]);

  return <div className="form-field" role="group" aria-label="사람인지 확인">
    <Script src={SCRIPT_URL} strategy="afterInteractive" onReady={() => setIsScriptLoaded(true)} onError={() => setHasError(true)} />
    <div ref={containerRef} />
    <input type="hidden" name="turnstile_token" value={token} />
    {hasError && <span className="field-hint" role="alert">확인 화면을 불러오지 못했어요. 새로고침한 뒤 다시 시도해 주세요.</span>}
  </div>;
}
