"use client";

import { Flex, Skeleton, Typography } from "antd";

export function TableSkeleton() {
  return (
    <Flex vertical gap="small" role="status" aria-busy="true" aria-label="데이터를 불러오고 있습니다">
      <Skeleton aria-hidden active title={false} paragraph={{ rows: 8 }} />
      <Typography.Text type="secondary">불러오는 중...</Typography.Text>
    </Flex>
  );
}
