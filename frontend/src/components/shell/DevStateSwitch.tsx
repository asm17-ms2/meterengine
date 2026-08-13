"use client";

import { usePathname, useSearchParams } from "next/navigation";
import Link from "next/link";

import {
  DEV_STATES,
  DEV_STATE_LABELS,
  devStateEnabled,
  readDevState,
} from "@/lib/dev-state";

/**
 * 사이드바 하단의 '데모 - 표 상태' 스위치. 개발 모드에서만 렌더링된다.
 *
 * 상태를 URL(?state=)에만 쓴다. 실제 데이터 경로는 각 화면의 로더가
 * 네트워크 호출 전에 이 값을 보고 분기한다.
 *
 * 프로덕션 빌드에서는 devStateEnabled가 상수 false라 이 컴포넌트가 통째로 죽는다.
 * useSearchParams()를 쓰므로 호출부에서 <Suspense>로 감싸야 prerender가 막히지 않는다.
 */
export function DevStateSwitch() {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const current = readDevState(searchParams.get("state") ?? undefined);

  if (!devStateEnabled) return null;

  function hrefFor(state: string): string {
    const next = new URLSearchParams(searchParams.toString());
    if (state === "normal") next.delete("state");
    else next.set("state", state);
    const query = next.toString();
    return query ? `${pathname}?${query}` : pathname;
  }

  return (
    <div className="dev-switch">
      <span className="dev-switch__label">데모 - 표 상태</span>
      <div className="dev-switch__options">
        {DEV_STATES.map((state) => (
          <Link
            key={state}
            href={hrefFor(state)}
            className="dev-switch__btn"
            aria-current={current === state ? "true" : undefined}
            replace
          >
            {DEV_STATE_LABELS[state]}
          </Link>
        ))}
      </div>
    </div>
  );
}
