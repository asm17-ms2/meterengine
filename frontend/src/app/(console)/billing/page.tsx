import type { Metadata } from "next";
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
import { ListControls } from "@/components/screen/ListControls";
import { ListControlsLoader } from "@/components/screen/ListControlsLoader";
import { ScreenCard } from "@/components/screen/ScreenCard";
import { ScreenHeader } from "@/components/screen/ScreenHeader";
import { TableSkeleton } from "@/components/screen/TableSkeleton";
import { CollapseProvider } from "@/components/table/CollapseProvider";
import { ExpandControls } from "@/components/table/ExpandControls";
import { listBillableMetrics } from "@/lib/api/billable-metrics";
import { previewDraftInvoice } from "@/lib/api/billing";
import { CAPABILITIES } from "@/lib/api/capabilities";
import { listCustomers } from "@/lib/api/customers";
import { listAllInvoices } from "@/lib/api/invoices";
import { formatKoreanMonth } from "@/lib/format";
import { readListQuery, type MonthlyListQuery } from "@/lib/list-query";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

const BILLING_CAPABILITIES = CAPABILITIES.previewDraftInvoice;

export const metadata: Metadata = { title: "청구 검토" };

export default async function BillingPage({
  searchParams,
}: {
  searchParams: SearchParams;
}) {
  const params = await searchParams;
  const query = readListQuery(params, BILLING_CAPABILITIES, { hasMonth: true });
  const { month, devState } = query;

  const isCustomerFiltered = query.customerId !== undefined;
  const monthQuery: MonthlyListQuery = { ...query, customerId: undefined };
  const monthInvoicesQuery = readListQuery({ month }, CAPABILITIES.listInvoices, { hasMonth: true });
  const invoicesQuery: MonthlyListQuery = { ...monthInvoicesQuery, customerId: query.customerId };

  const draftInvoice =
    devState === "loading" ? null : previewDraftInvoice(query, devState);
  const invoices = listAllInvoices(invoicesQuery, devState);
  const monthDraftInvoice =
    draftInvoice && isCustomerFiltered ? previewDraftInvoice(monthQuery, devState) : draftInvoice;
  const monthInvoices = isCustomerFiltered ? listAllInvoices(monthInvoicesQuery, devState) : invoices;

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
                  <BillingExpandControls draftInvoice={draftInvoice} invoices={invoices} />
                </Suspense>
              </>
            ) : (
              <ExpandControls />
            )
          }
        >
          <Suspense
            fallback={
              <ListControls
                query={query}
                capabilities={BILLING_CAPABILITIES}
                month={{ value: month }}
                customers={[]}
                eventTypes={[]}
                isLoadingOptions
              />
            }
          >
            <ListControlsLoader
              query={query}
              capabilities={BILLING_CAPABILITIES}
              month={{ value: month }}
              customers={
                BILLING_CAPABILITIES.customer_id
                  ? listCustomers(readListQuery({}, CAPABILITIES.listCustomers), "normal")
                  : undefined
              }
              metrics={
                BILLING_CAPABILITIES.type
                  ? listBillableMetrics(readListQuery({}, CAPABILITIES.listBillableMetrics), "normal")
                  : undefined
              }
            />
          </Suspense>
        </FilterBar>

        {draftInvoice ? (
          <Suspense fallback={<TableSkeleton />}>
            <BillingSection draftInvoice={draftInvoice} invoices={invoices} query={query} />
          </Suspense>
        ) : (
          <TableSkeleton />
        )}
      </ScreenCard>
    </CollapseProvider>
  );
}
