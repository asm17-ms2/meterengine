"use client";

import { Flex, Skeleton, Typography } from "antd";

export function TableSkeleton() {
  return (
    <Flex vertical gap="small" role="status">
      <div aria-hidden="true"><Skeleton active title={false} paragraph={{ rows: 8 }} /></div>
      <Typography.Text type="secondary">불러오는 중...</Typography.Text>
    </Flex>
  );
}
