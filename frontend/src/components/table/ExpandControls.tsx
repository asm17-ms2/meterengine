"use client";

import { Button, Flex, Grid } from "antd";

import { useCollapse } from "@/components/table/CollapseProvider";

// 모바일 카드는 각자 details로 펼치므로, 데스크톱 표에만 듣는 모두 펼치기와 접기는 md(768px) 미만에서 숨긴다.
export function ExpandControls() {
  const { expandAll, collapseAll } = useCollapse();
  const screens = Grid.useBreakpoint();
  if (screens.md === false) return null;
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
