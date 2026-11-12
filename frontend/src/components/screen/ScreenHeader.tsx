"use client";

import { Flex, Typography } from "antd";

export function ScreenHeader({
  title,
  description,
  tags,
  extra,
  children,
}: {
  title: React.ReactNode;
  description?: React.ReactNode;
  tags?: React.ReactNode;
  extra?: React.ReactNode;
  children?: React.ReactNode;
}) {
  return (
    <Flex justify="space-between" align="start" gap="medium" wrap>
      <Flex vertical gap={4} style={{ minWidth: 0 }}>
        <Flex align="center" gap="small" wrap>
          <Typography.Title level={1} style={{ margin: 0, fontSize: "var(--screen-title-size)", letterSpacing: "-.035em" }}>
            {title}
          </Typography.Title>
          {tags}
        </Flex>
        {description ? (
          <Typography.Paragraph type="secondary" style={{ margin: 0, maxWidth: 650 }}>
            {description}
          </Typography.Paragraph>
        ) : null}
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
