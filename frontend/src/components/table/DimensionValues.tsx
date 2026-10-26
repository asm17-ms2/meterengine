"use client";

import { theme, Tooltip, Typography } from "antd";

import { toDimensionValueSlots } from "@/lib/format";

export function countDimensionSlots(dimensionValuesList: Record<string, unknown>[]): number {
  return Math.max(
    1,
    ...dimensionValuesList.map((dimensionValues) => Object.keys(dimensionValues).length),
  );
}

export function DimensionValues({
  dimensionValues,
  slotCount,
  children,
}: {
  dimensionValues: Record<string, unknown>;
  slotCount: number;
  children?: React.ReactNode;
}) {
  const { token } = theme.useToken();

  return (
    <>
      <div
        style={{
          display: "grid",
          gridTemplateColumns: `repeat(${slotCount}, minmax(0, 1fr))`,
          columnGap: 16,
          fontFamily: token.fontFamilyCode,
        }}
      >
        {toDimensionValueSlots(dimensionValues).map((slot) => (
          <Tooltip key={slot.hint} title={slot.hint}>
            <Typography.Text
              ellipsis
              type={slot.isMissing ? "secondary" : undefined}
              style={{ fontFamily: token.fontFamilyCode }}
            >
              {slot.value}
            </Typography.Text>
          </Tooltip>
        ))}
      </div>
      {children}
    </>
  );
}
