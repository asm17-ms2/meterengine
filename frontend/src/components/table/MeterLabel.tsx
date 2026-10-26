"use client";

import { theme, Tooltip, Typography } from "antd";

export function MeterLabel({
  label,
  labelTooltip,
  isCodeLabel = true,
}: {
  label: string;
  labelTooltip?: string;
  isCodeLabel?: boolean;
}) {
  const { token } = theme.useToken();
  const text = (
    <Typography.Text style={isCodeLabel ? { fontFamily: token.fontFamilyCode } : undefined}>
      {label}
    </Typography.Text>
  );

  return labelTooltip ? (
    <Tooltip placement="right" title={labelTooltip}>
      {text}
    </Tooltip>
  ) : (
    text
  );
}
