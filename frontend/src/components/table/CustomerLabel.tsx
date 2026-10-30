"use client";

import { theme, Typography } from "antd";

export function CustomerLabel({ name, id }: { name: string; id: string }) {
  const { token } = theme.useToken();
  return (
    <Typography.Text ellipsis={{ tooltip: `${name} (${id})` }}>
      <Typography.Text strong>{name}</Typography.Text>{" "}
      <Typography.Text
        type="secondary"
        style={{ fontSize: token.fontSizeSM, fontFamily: token.fontFamilyCode }}
      >
        {id}
      </Typography.Text>
    </Typography.Text>
  );
}
