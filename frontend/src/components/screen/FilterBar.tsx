"use client";

import { Flex } from "antd";

export function FilterBar({
  children,
  end,
}: {
  children?: React.ReactNode;
  end?: React.ReactNode;
}) {
  return (
    <Flex justify="space-between" align="center" gap="medium" wrap>
      <Flex align="center" gap="small" wrap style={{ minWidth: 0, maxWidth: "100%" }}>
        {children}
      </Flex>
      {end ? (
        <Flex align="center" gap="small" wrap>
          {end}
        </Flex>
      ) : null}
    </Flex>
  );
}
