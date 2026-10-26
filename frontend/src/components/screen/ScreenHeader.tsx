"use client";

import { Flex, Typography } from "antd";

export function ScreenHeader({
  title,
  tags,
  extra,
  children,
}: {
  title: React.ReactNode;
  tags?: React.ReactNode;
  extra?: React.ReactNode;
  children?: React.ReactNode;
}) {
  return (
    <Flex justify="space-between" align="start" gap="medium" wrap>
      <Flex vertical gap={4} style={{ minWidth: 0 }}>
        <Flex align="center" gap="small" wrap>
          <Typography.Title level={3} style={{ margin: 0 }}>
            {title}
          </Typography.Title>
          {tags}
        </Flex>
        {children ? (
          <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
            {children}
          </Typography.Paragraph>
        ) : null}
      </Flex>
      {extra ? (
        <Flex align="center" gap="small" wrap>
          {extra}
        </Flex>
      ) : null}
    </Flex>
  );
}
