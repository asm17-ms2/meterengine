"use client";

import { Empty, Flex, Typography } from "antd";

import { LinkButton } from "@/components/screen/LinkButton";

export function EmptyState({
  title,
  body,
  resetHref,
  resetLabel = "조건 초기화",
  action,
}: {
  title: string;
  body: string;
  resetHref?: string;
  resetLabel?: string;
  action?: React.ReactNode;
}) {
  const footer = action ?? (resetHref ? <LinkButton href={resetHref}>{resetLabel}</LinkButton> : null);

  return (
    <Empty
      image={Empty.PRESENTED_IMAGE_SIMPLE}
      description={
        <Flex vertical gap={4}>
          <Typography.Text strong>{title}</Typography.Text>
          <Typography.Text type="secondary">{body}</Typography.Text>
        </Flex>
      }
    >
      {footer}
    </Empty>
  );
}
