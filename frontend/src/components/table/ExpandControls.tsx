"use client";

import { Button, Flex } from "antd";

import { useCollapse } from "@/components/table/CollapseProvider";

export function ExpandControls() {
  const { expandAll, collapseAll } = useCollapse();
  return (
    <Flex gap="small">
      <Button size="small" onClick={expandAll}>
        모두 펼치기
      </Button>
      <Button size="small" onClick={collapseAll}>
        모두 접기
      </Button>
    </Flex>
  );
}
