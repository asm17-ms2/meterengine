"use client";

import {
  CloseCircleFilled,
  DownOutlined,
  LoadingOutlined,
  SearchOutlined,
} from "@ant-design/icons";
import { Select } from "antd";
import { useState } from "react";
import { useRef } from "react";
import { queryOptionsAction } from "@/app/query-options-actions";

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
  label = "고객",
  selectedCustomerId,
  onChange,
  id,
  disabled = false,
}: {
  query: ListQuery;
  capabilities: ListCapabilities;
  customers: CustomerOption[];
  isLoadingOptions?: boolean;
  label?: string;
  selectedCustomerId?: string;
  onChange?: (customerId: string | undefined) => void;
  id?: string;
  disabled?: boolean;
}) {
  const { query: liveQuery, push } = useListNavigation(query, capabilities);
  const [isOpen, setIsOpen] = useState(false);
  const [remoteOptions, setRemoteOptions] = useState<CustomerOption[] | null>(null);
  const [isSearching, setIsSearching] = useState(false);
  const [searchError, setSearchError] = useState("");
  const latestRequest = useRef(0);
  const searchText = useRef("");

  async function searchCustomers(text: string) {
    searchText.current = text;
    const sequence = ++latestRequest.current;
    setIsSearching(true);
    const result = await queryOptionsAction("customers", text);
    if (sequence !== latestRequest.current) return;
    setIsSearching(false);
    if (result.ok) { setRemoteOptions(result.data.map(option => ({ value: option.value, label: option.label + (option.archived ? " (보관됨)" : "") }))); setSearchError(""); }
    else setSearchError(result.error.message);
  }

  if (!capabilities.customer_id) return null;

  return (
    <Select<string, CustomerOption>
      disabled={disabled}
      id={id}
      aria-label={label}
      allowClear={{
        clearIcon: <CloseCircleFilled aria-hidden aria-label={undefined} />,
        label: "지우기",
      }}
      showSearch={{ filterOption: false, onSearch: searchCustomers }}
      placeholder="고객: 전체"
      loading={isLoadingOptions || isSearching}
      suffixIcon={<CustomerFilterIcon isLoading={isLoadingOptions || isSearching} isOpen={isOpen} />}
      options={remoteOptions ?? customers}
      notFoundContent={searchError || (isSearching ? "조회 중" : "일치하는 고객이 없습니다")}
      optionRender={(option) => <CustomerLabel name={option.data.label} id={option.data.value} />}
      value={isLoadingOptions ? undefined : onChange ? selectedCustomerId : liveQuery.customerId}
      onOpenChange={open => { setIsOpen(open); if (open) void searchCustomers(searchText.current); else searchText.current = ""; }}
      onChange={(customerId) => onChange ? onChange(customerId) : push({ customerId })}
      style={{ minWidth: 200 }}
    />
  );
}
