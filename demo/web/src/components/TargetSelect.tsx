"use client";

import { Select } from "antd";

type TargetSelectProps = {
  targetNames: string[];
  value: string | undefined;
  onChange: (value: string) => void;
};

export function TargetSelect({ targetNames, value, onChange }: TargetSelectProps) {
  return (
    <Select
      aria-label="연동 대상"
      style={{ minWidth: 160 }}
      placeholder="대상"
      value={value}
      onChange={onChange}
      options={targetNames.map((name) => ({ value: name, label: name }))}
    />
  );
}
