"use client";

import { Flex, Skeleton, Typography } from "antd";

export function TableSkeleton() {
  return (
    <Flex vertical gap="small" aria-hidden>
      <Skeleton active title={false} paragraph={{ rows: 8 }} />
      <Typography.Text type="secondary">불러오는 중...</Typography.Text>
    </Flex>
  );
}
