"use client";

import { useEffect, useState } from "react";
import { Flex, Skeleton, Spin, Typography } from "antd";

export function AggregateLoading({ title }: { title: string }) {
  const [seconds, setSeconds] = useState(0);
  useEffect(() => {
    const startedAt = Date.now();
    const timer = setInterval(() => setSeconds(Math.floor((Date.now() - startedAt) / 1000)), 1000);
    return () => clearInterval(timer);
  }, []);
  return (
    <Flex vertical gap="middle" role="status" aria-busy="true">
      <Flex align="center" gap="middle">
        <Spin size="large" />
        <Flex vertical>
          <Typography.Text strong>
            {title}
            {seconds > 0 && <span aria-hidden="true"> ({seconds}초)</span>}
          </Typography.Text>
          <Typography.Text>선택한 달의 이벤트를 모두 세고 있어서 10초 가까이 걸릴 수 있어요. 새로고침하지 않아도 결과가 이 자리에 나와요.</Typography.Text>
        </Flex>
      </Flex>
      <div aria-hidden="true"><Skeleton active title={false} paragraph={{ rows: 6 }} /></div>
    </Flex>
  );
}
