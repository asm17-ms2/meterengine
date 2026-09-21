"use client";

import { usePathname, useSearchParams } from "next/navigation";
import Link from "next/link";

import {
  DEV_STATES,
  DEV_STATE_LABELS,
  isDevStateEnabled,
  readDevState,
} from "@/lib/dev-state";

export function DevStateSwitch() {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const currentState = readDevState(searchParams.get("state") ?? undefined);

  if (!isDevStateEnabled) return null;

  function buildHref(state: string): string {
    const nextParams = new URLSearchParams(searchParams.toString());
    if (state === "normal") nextParams.delete("state");
    else nextParams.set("state", state);
    const query = nextParams.toString();
    return query ? `${pathname}?${query}` : pathname;
  }

  return (
    <div className="dev-switch">
      <span className="dev-switch__label">데모 - 표 상태</span>
      <div className="dev-switch__options">
        {DEV_STATES.map((state) => (
          <Link
            key={state}
            href={buildHref(state)}
            className="dev-switch__btn"
            aria-current={currentState === state ? "true" : undefined}
            replace
          >
            {DEV_STATE_LABELS[state]}
          </Link>
        ))}
      </div>
    </div>
  );
}
