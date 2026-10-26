"use client";

import { Typography } from "antd";

type TextType = React.ComponentProps<typeof Typography.Text>["type"];

export function NumberText({
  children,
  strong = false,
  type,
}: {
  children: React.ReactNode;
  strong?: boolean;
  type?: TextType;
}) {
  return (
    <Typography.Text
      strong={strong}
      type={type}
      style={{ fontVariantNumeric: "tabular-nums", whiteSpace: "nowrap" }}
    >
      {children}
    </Typography.Text>
  );
}
