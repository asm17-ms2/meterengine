import { CsvDownload } from "@/components/screen/CsvDownload";
import { DataMonths } from "@/components/screen/DataMonths";
import { Suspense } from "react";

import { BillingCalculatedStamp } from "@/components/billing/BillingCalculatedStamp";
import { BillingExpandControls } from "@/components/billing/BillingExpandControls";
import { BillingFinalizedLink } from "@/components/billing/BillingFinalizedLink";
import { BillingMeta } from "@/components/billing/BillingMeta";
import { BillingSection } from "@/components/billing/BillingSection";
import { BillingStatusTag } from "@/components/billing/BillingStatusTag";
import { InvoiceFinalizeButton } from "@/components/billing/InvoiceFinalizeButton";
import { InvoiceFinalizeSection } from "@/components/billing/InvoiceFinalizeSection";
import { FilterBar } from "@/components/screen/FilterBar";
import { ListControlsLoader } from "@/components/screen/ListControlsLoader";
import { ScreenCard } from "@/components/screen/ScreenCard";
import { ScreenHeader } from "@/components/screen/ScreenHeader";
import { TableSkeleton } from "@/components/screen/TableSkeleton";
import { CollapseProvider } from "@/components/table/CollapseProvider";
import { ExpandControls } from "@/components/table/ExpandControls";
import { previewDraftInvoice, resolveIsFinalizedMonth } from "@/lib/api/billing";
import { CAPABILITIES } from "@/lib/api/capabilities";
import { listAllInvoices } from "@/lib/api/invoices";
import { formatKoreanMonth } from "@/lib/format";
import { readListQuery, type MonthlyListQuery } from "@/lib/list-query";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

const BILLING_CAPABILITIES = CAPABILITIES.previewDraftInvoice;

export default async function BillingPage({
  searchParams,
}: {
  searchParams: SearchParams;
}) {
  const params = await searchParams;
  const query = readListQuery(params, BILLING_CAPABILITIES, { hasMonth: true });
  const { month, devState } = query;

  const isCustomerFiltered = Boolean(query.customerId || query.metricCode || query.q || query.minAmount || query.maxAmount || query.page);
  const monthQuery: MonthlyListQuery = { ...query, customerId: undefined, metricCode: undefined, q: "", minAmount: undefined, maxAmount: undefined, page: 0 };
  const monthInvoicesQuery = readListQuery({ month }, CAPABILITIES.listInvoices, { hasMonth: true });
  const invoicesQuery: MonthlyListQuery = { ...monthInvoicesQuery, customerId: query.customerId };

  const draftInvoice =
    devState === "loading" ? null : previewDraftInvoice(query, devState);
  const invoices = listAllInvoices(invoicesQuery, devState);
  const monthDraftInvoice =
    draftInvoice && isCustomerFiltered ? previewDraftInvoice(monthQuery, devState) : draftInvoice;
  const monthInvoices = isCustomerFiltered ? listAllInvoices(monthInvoicesQuery, devState) : invoices;
  const isMonthFinalized = monthDraftInvoice ? resolveIsFinalizedMonth(monthDraftInvoice, monthInvoices) : Promise.resolve(false);

  return (
    <CollapseProvider>
      <ScreenHeader
        title={`청구 예정액 - ${formatKoreanMonth(month)}`}
        tags={
          monthDraftInvoice ? (
            <Suspense fallback={null}>
              <BillingStatusTag draftInvoice={monthDraftInvoice} invoices={monthInvoices} />
            </Suspense>
          ) : null
        }
        extra={
          monthDraftInvoice ? (
            <Suspense fallback={<InvoiceFinalizeButton disabled />}>
              <InvoiceFinalizeSection draftInvoice={monthDraftInvoice} month={month} />
            </Suspense>
          ) : (
            <InvoiceFinalizeButton disabled />
          )
        }
      >
        {draftInvoice ? (
          <Suspense fallback={null}>
            <BillingMeta draftInvoice={draftInvoice} />
          </Suspense>
        ) : null}
      </ScreenHeader>

      <ScreenCard>
        <FilterBar
          end={
            draftInvoice ? (
              <>
                <Suspense fallback={null}>
                  <BillingFinalizedLink invoices={monthInvoices} month={month} />
                </Suspense>
                <Suspense fallback={null}>
                  <BillingCalculatedStamp draftInvoice={draftInvoice} />
                </Suspense>
                <Suspense fallback={null}>
                  <BillingExpandControls draftInvoice={draftInvoice} isMonthFinalized={isMonthFinalized} />
                </Suspense>
              </>
            ) : (
              <ExpandControls />
            )
          }
        >
          <Suspense
            fallback={<span role="status">조회 조건을 불러오는 중입니다.</span>}
          >
            <ListControlsLoader
              resource="billing"
              query={query}
              capabilities={BILLING_CAPABILITIES}
              month={{ value: month }}
            />
          </Suspense>
        </FilterBar>
        <CsvDownload resource="billing" />
        <Suspense fallback={null}><DataMonths filterForm="resource-search-billing" resource="usage" pathname="/billing" query={query} /></Suspense>

        {draftInvoice ? (
          <Suspense fallback={<TableSkeleton />}>
            <BillingSection draftInvoice={draftInvoice} invoices={invoices} isMonthFinalized={isMonthFinalized} query={query} />
          </Suspense>
        ) : (
          <TableSkeleton />
        )}
      </ScreenCard>
    </CollapseProvider>
  );
}
