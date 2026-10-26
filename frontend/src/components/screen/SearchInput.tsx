"use client";

import { CloseCircleFilled } from "@ant-design/icons";
import { Input } from "antd";

export function SearchInput({
  label,
  value,
  onChange,
  onBlur,
  disabled = false,
}: {
  label: string;
  value?: string;
  onChange?: (value: string) => void;
  onBlur?: () => void;
  disabled?: boolean;
}) {
  return (
    <Input
      role="searchbox"
      allowClear={{ clearIcon: <CloseCircleFilled aria-label="검색어 지우기" /> }}
      style={{ width: 340, maxWidth: "100%" }}
      aria-label={label}
      placeholder={label}
      value={value}
      onChange={(event) => onChange?.(event.target.value)}
      onBlur={onBlur}
      onKeyDown={(event) => {
        if (event.key !== "Escape" || !value) return;
        event.preventDefault();
        onChange?.("");
      }}
      disabled={disabled}
    />
  );
}
