"use client";

import { DownOutlined } from "@ant-design/icons";
import { Select } from "antd";

import { useListNavigation } from "@/components/screen/use-list-navigation";
import type { ListCapabilities } from "@/lib/api/capabilities";
import { PAGE_SIZE_OPTIONS, type ListQuery } from "@/lib/list-query";

export function PageSizeSelect({
  query,
  capabilities,
}: {
  query: ListQuery;
  capabilities: ListCapabilities;
}) {
  const { query: liveQuery, push } = useListNavigation(query, capabilities);

  if (!capabilities.size) return null;

  return (
    <Select<number>
      aria-label="페이지 크기"
      suffixIcon={<DownOutlined aria-hidden aria-label={undefined} />}
      options={PAGE_SIZE_OPTIONS.map((size) => ({ value: size, label: `${size}건씩` }))}
      value={liveQuery.size}
      onChange={(size) => push({ size })}
    />
  );
}
