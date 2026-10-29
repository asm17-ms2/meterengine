"use client";

import { CustomerFilter, type CustomerOption } from "@/components/screen/CustomerFilter";
import { MonthPicker } from "@/components/screen/MonthPicker";
import { PageSizeSelect } from "@/components/screen/PageSizeSelect";
import { TypeFilter } from "@/components/screen/TypeFilter";
import { ArchiveFilter } from "@/components/workspace/ArchiveFilter";
import type { ListCapabilities } from "@/lib/api/capabilities";
import type { ListQuery } from "@/lib/list-query";

export type ListControlsMonth = { value: string; allowAll?: boolean };

export function ListControls({
  query,
  capabilities,
  month,
  customers = [],
  eventTypes = [],
  search,
  isLoadingOptions = false,
}: {
  query: ListQuery;
  capabilities: ListCapabilities;
  month?: ListControlsMonth;
  customers?: CustomerOption[];
  eventTypes?: string[];
  search?: React.ReactNode;
  isLoadingOptions?: boolean;
}) {
  return (
    <>
      {capabilities.archive && <ArchiveFilter />}
      {month ? <MonthPicker
          query={query}
          capabilities={capabilities}
          month={month.value}
          allowAll={month.allowAll}
        /> : null}
      <CustomerFilter
        query={query}
        capabilities={capabilities}
        customers={customers}
        isLoadingOptions={isLoadingOptions}
      />
      <TypeFilter query={query} capabilities={capabilities} eventTypes={eventTypes} />
      {search}
      <PageSizeSelect query={query} capabilities={capabilities} />
    </>
  );
}
