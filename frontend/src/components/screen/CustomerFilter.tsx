"use client";

import {
  CloseCircleFilled,
  DownOutlined,
  LoadingOutlined,
  SearchOutlined,
} from "@ant-design/icons";
import { Select } from "antd";
import { useState } from "react";

import { useListNavigation } from "@/components/screen/use-list-navigation";
import { CustomerLabel } from "@/components/table/CustomerLabel";
import type { ListCapabilities } from "@/lib/api/capabilities";
import type { ListQuery } from "@/lib/list-query";

export type CustomerOption = { value: string; label: string };

function CustomerFilterIcon({ isLoading, isOpen }: { isLoading: boolean; isOpen: boolean }) {
  if (isLoading) return <LoadingOutlined spin aria-hidden aria-label={undefined} />;
  if (isOpen) return <SearchOutlined aria-hidden aria-label={undefined} />;
  return <DownOutlined aria-hidden aria-label={undefined} />;
}

export function CustomerFilter({
  query,
  capabilities,
  customers,
  isLoadingOptions = false,
}: {
  query: ListQuery;
  capabilities: ListCapabilities;
  customers: CustomerOption[];
  isLoadingOptions?: boolean;
}) {
  const { query: liveQuery, push } = useListNavigation(query, capabilities);
  const [isOpen, setIsOpen] = useState(false);

  if (!capabilities.customer_id) return null;

  return (
    <Select<string, CustomerOption>
      aria-label="고객"
      allowClear={{
        clearIcon: <CloseCircleFilled aria-hidden aria-label={undefined} />,
        label: "지우기",
      }}
      showSearch={{ optionFilterProp: ["label", "value"] }}
      placeholder="고객: 전체"
      loading={isLoadingOptions}
      suffixIcon={<CustomerFilterIcon isLoading={isLoadingOptions} isOpen={isOpen} />}
      options={customers}
      optionRender={(option) => <CustomerLabel name={option.data.label} id={option.data.value} />}
      value={isLoadingOptions ? undefined : liveQuery.customerId}
      onOpenChange={setIsOpen}
      onChange={(customerId) => push({ customerId })}
      style={{ minWidth: 200 }}
    />
  );
}
