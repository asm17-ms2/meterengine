import type { Metadata } from "next";
import { Suspense } from "react";

import { InvoicesSection } from "@/components/invoices/InvoicesSection";
import { FilterBar } from "@/components/screen/FilterBar";
import { ListControls } from "@/components/screen/ListControls";
import { ListControlsLoader } from "@/components/screen/ListControlsLoader";
import { ScreenCard } from "@/components/screen/ScreenCard";
import { ScreenHeader } from "@/components/screen/ScreenHeader";
import { StatusTag } from "@/components/screen/StatusTag";
import { TableSkeleton } from "@/components/screen/TableSkeleton";
import { CollapseProvider } from "@/components/table/CollapseProvider";
import { ExpandControls } from "@/components/table/ExpandControls";
import { BILLABLE_METRICS_CACHE_TAG, listBillableMetrics } from "@/lib/api/billable-metrics";
import { CAPABILITIES } from "@/lib/api/capabilities";
import { CUSTOMERS_CACHE_TAG, listCustomers } from "@/lib/api/customers";
import { listInvoices } from "@/lib/api/invoices";
import { formatKoreanMonth } from "@/lib/format";
import { readListQuery } from "@/lib/list-query";
import { ALL_MONTHS } from "@/lib/month";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

export const metadata: Metadata = { title: "확정 인보이스" };

export default async function InvoicesPage({
  searchParams,
}: {
  searchParams: SearchParams;
}) {
  const params = await searchParams;
  const capabilities = CAPABILITIES.listInvoices;
  const query = readListQuery(params, capabilities, { hasMonth: true });
  const { month, devState } = query;
  const isAllMonths = month === ALL_MONTHS;

  const invoices = devState === "loading" ? null : listInvoices(query, devState);
  const monthControl = { value: month, allowAll: capabilities.month_all };

  return (
    <CollapseProvider>
      <ScreenHeader
        title={isAllMonths ? "인보이스 - 전체 기간" : `인보이스 - ${formatKoreanMonth(month)}`}
        tags={<StatusTag color="success">확정 (finalized)</StatusTag>}
      />

      <ScreenCard>
        <FilterBar end={<ExpandControls />}>
          <Suspense
            fallback={
              <ListControls
                query={query}
                capabilities={capabilities}
                month={monthControl}
                customers={[]}
                eventTypes={[]}
                isLoadingOptions
              />
            }
          >
            <ListControlsLoader
              query={query}
              capabilities={capabilities}
              month={monthControl}
              customers={
                capabilities.customer_id
                  ? listCustomers(readListQuery({ status: "all" }, CAPABILITIES.listCustomers), "normal", CUSTOMERS_CACHE_TAG)
                  : undefined
              }
              metrics={
                capabilities.type
                  ? listBillableMetrics(readListQuery({ status: "all" }, CAPABILITIES.listBillableMetrics), "normal", BILLABLE_METRICS_CACHE_TAG)
                  : undefined
              }
            />
          </Suspense>
        </FilterBar>

        {invoices ? (
          <Suspense fallback={<TableSkeleton />}>
            <InvoicesSection invoices={invoices} query={query} capabilities={capabilities} />
          </Suspense>
        ) : (
          <TableSkeleton />
        )}
      </ScreenCard>
    </CollapseProvider>
  );
}
