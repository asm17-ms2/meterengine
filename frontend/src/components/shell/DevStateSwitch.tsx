"use client";

import { Flex, Typography } from "antd";
import { usePathname, useSearchParams } from "next/navigation";

import { LinkButton } from "@/components/screen/LinkButton";
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
    <Flex vertical gap="small" style={{ padding: 16 }}>
      <Typography.Text type="secondary">데모 - 표 상태</Typography.Text>
      <Flex gap="small" wrap>
        {DEV_STATES.map((state) => {
          const isCurrent = currentState === state;
          return (
            <LinkButton
              key={state}
              replace
              size="small"
              type={isCurrent ? "primary" : "default"}
              aria-current={isCurrent ? "true" : undefined}
              href={buildHref(state)}
            >
              {DEV_STATE_LABELS[state]}
            </LinkButton>
          );
        })}
      </Flex>
    </Flex>
  );
}
