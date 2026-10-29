import type { Metadata } from "next";
import { CsvDownload } from "@/components/screen/CsvDownload";
import { DataMonths } from "@/components/screen/DataMonths";
import { Suspense } from "react";

import { InvoicesSection } from "@/components/invoices/InvoicesSection";
import { FilterBar } from "@/components/screen/FilterBar";
import { ListControlsLoader } from "@/components/screen/ListControlsLoader";
import { ScreenCard } from "@/components/screen/ScreenCard";
import { ScreenHeader } from "@/components/screen/ScreenHeader";
import { StatusTag } from "@/components/screen/StatusTag";
import { TableSkeleton } from "@/components/screen/TableSkeleton";
import { CollapseProvider } from "@/components/table/CollapseProvider";
import { ExpandControls } from "@/components/table/ExpandControls";
import { CAPABILITIES } from "@/lib/api/capabilities";
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
        title={query.from || query.to ? "인보이스 - 직접 지정 기간" : isAllMonths ? "인보이스 - 전체 기간" : `인보이스 - ${formatKoreanMonth(month)}`}
        tags={<StatusTag color="success">확정 (finalized)</StatusTag>}
      />

      <ScreenCard>
        <FilterBar end={<ExpandControls />}>
          <Suspense
            fallback={<span role="status">조회 조건을 불러오는 중입니다.</span>}
          >
            <ListControlsLoader
              resource="invoices"
              query={query}
              capabilities={capabilities}
              month={monthControl}
            />
          </Suspense>
        </FilterBar>
        <CsvDownload resource="invoices" />
        <Suspense fallback={null}><DataMonths filterForm="resource-search-invoices" resource="invoices" pathname="/invoices" query={query} /></Suspense>

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
