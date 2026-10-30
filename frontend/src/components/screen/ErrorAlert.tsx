"use client";

import { Alert, Flex, Grid } from "antd";

import { alertIcon } from "@/components/screen/alert-icon";
import { LinkButton } from "@/components/screen/LinkButton";
import { RetryButton } from "@/components/screen/RetryButton";

export function ErrorAlert({
  title,
  message,
  narrowerHref,
  resetHref,
}: {
  title: string;
  message: string;
  narrowerHref?: string;
  resetHref?: string;
}) {
  const screens = Grid.useBreakpoint();
  const isNarrow = screens.md === false;

  const actions = (
    <Flex gap="small" wrap>
      <RetryButton />
      {narrowerHref ? <LinkButton href={narrowerHref}>기간 좁히기</LinkButton> : null}
      {resetHref ? <LinkButton href={resetHref}>조건 초기화</LinkButton> : null}
    </Flex>
  );

  return (
    <Alert
      type="error"
      showIcon
      icon={alertIcon("error")}
      title={title}
      description={
        isNarrow ? (
          <Flex vertical gap="small">
            <span>{message}</span>
            {actions}
          </Flex>
        ) : (
          message
        )
      }
      action={isNarrow ? undefined : actions}
    />
  );
}
