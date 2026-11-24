import type { Metadata } from "next";
import { CsvDownload } from "@/components/screen/CsvDownload";
import { DataMonths } from "@/components/screen/DataMonths";
import { Suspense } from "react";

import { BillingAutoFinalizeStamp } from "@/components/billing/BillingAutoFinalizeStamp";
import { BillingCalculatedStamp } from "@/components/billing/BillingCalculatedStamp";
import { BillingExpandControls } from "@/components/billing/BillingExpandControls";
import { BillingFinalizedLink } from "@/components/billing/BillingFinalizedLink";
import { BillingMeta } from "@/components/billing/BillingMeta";
import { BillingSection } from "@/components/billing/BillingSection";
import { BillingStatusTag } from "@/components/billing/BillingStatusTag";
import { InvoiceFinalizeButton } from "@/components/billing/InvoiceFinalizeButton";
import { InvoiceFinalizeDialog } from "@/components/billing/InvoiceFinalizeDialog";
import { InvoiceFinalizeSection } from "@/components/billing/InvoiceFinalizeSection";
import { CustomerRequiredState } from "@/components/screen/CustomerRequiredState";
import { CutoffStamp } from "@/components/screen/CutoffStamp";
import { FilterBar } from "@/components/screen/FilterBar";
import { ListControlsLoader } from "@/components/screen/ListControlsLoader";
import { ScreenCard } from "@/components/screen/ScreenCard";
import { ScreenHeader } from "@/components/screen/ScreenHeader";
import { AggregateLoading } from "@/components/screen/AggregateLoading";
import { searchKey } from "@/lib/search-key";
import { CollapseProvider } from "@/components/table/CollapseProvider";
import { ExpandControls } from "@/components/table/ExpandControls";
import { previewDraftInvoice, resolveIsFinalizedMonth } from "@/lib/api/billing";
import { CAPABILITIES } from "@/lib/api/capabilities";
import { listAllInvoices } from "@/lib/api/invoices";
import { formatKoreanMonth } from "@/lib/format";
import { formatKstMonth } from "@/lib/month";
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
  const loadingKey = searchKey(params);

  const isCustomerFiltered = Boolean(query.customerId || query.metricCode || query.q || query.minAmount || query.maxAmount || query.page);
  const monthInvoicesQuery = readListQuery({ month }, CAPABILITIES.listInvoices, { hasMonth: true });
  const invoicesQuery: MonthlyListQuery = { ...monthInvoicesQuery, customerId: query.customerId };

  const hasCustomer = Boolean(query.customerId);
  const draftInvoice =
    devState === "loading" || !hasCustomer ? null : previewDraftInvoice(query, devState);
  const invoices = listAllInvoices(invoicesQuery, devState);
  const monthInvoices = isCustomerFiltered ? listAllInvoices(monthInvoicesQuery, devState) : invoices;
  // 조건을 건 응답도 summary에 월 전체 값(month_*)을 실어 오므로 월 전체 발행 영역도 같은 응답을 쓴다.
  const isMonthFinalized = draftInvoice ? resolveIsFinalizedMonth(draftInvoice, monthInvoices) : Promise.resolve(false);

  return (
    <CollapseProvider>
      <ScreenHeader
        title={`청구 검토 - ${formatKoreanMonth(month)}`}
        tags={
          draftInvoice ? (
            <Suspense key={loadingKey} fallback={null}>
              <BillingStatusTag draftInvoice={draftInvoice} invoices={monthInvoices} />
            </Suspense>
          ) : null
        }
        extra={
          !hasCustomer ? (
            <InvoiceFinalizeDialog month={month} monthLabel={formatKoreanMonth(month)} isUnfinishedMonth={month >= formatKstMonth()} />
          ) : draftInvoice ? (
            <Suspense key={month} fallback={<InvoiceFinalizeButton disabled />}>
              <InvoiceFinalizeSection draftInvoice={draftInvoice} month={month} />
            </Suspense>
          ) : (
            <InvoiceFinalizeButton disabled />
          )
        }
      >
        {draftInvoice ? (
          <Suspense key={loadingKey} fallback={null}>
            <BillingMeta draftInvoice={draftInvoice} />
          </Suspense>
        ) : null}
      </ScreenHeader>

      <ScreenCard>
        <FilterBar
          end={
            draftInvoice ? (
              <>
                <Suspense key={`${loadingKey}:finalized-link`} fallback={null}>
                  <BillingFinalizedLink invoices={monthInvoices} month={month} />
                </Suspense>
                <CutoffStamp month={month} now={new Date()} />
                <Suspense key={`${loadingKey}:auto-finalize`} fallback={null}>
                  <BillingAutoFinalizeStamp draftInvoice={draftInvoice} />
                </Suspense>
                <Suspense key={`${loadingKey}:calculated`} fallback={null}>
                  <BillingCalculatedStamp draftInvoice={draftInvoice} />
                </Suspense>
                <Suspense key={`${loadingKey}:expand`} fallback={null}>
                  <BillingExpandControls draftInvoice={draftInvoice} isMonthFinalized={isMonthFinalized} />
                </Suspense>
              </>
            ) : hasCustomer ? (
              <ExpandControls />
            ) : (
              <Suspense key={`${loadingKey}:finalized-link`} fallback={null}>
                <BillingFinalizedLink invoices={monthInvoices} month={month} />
              </Suspense>
            )
          }
        >
          <Suspense
            fallback={<span role="status">조회 조건을 불러오는 중이에요.</span>}
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

        {!hasCustomer ? (
          <CustomerRequiredState subject="청구 예정액은" />
        ) : draftInvoice ? (
          <Suspense key={loadingKey} fallback={<AggregateLoading title="청구 예정액을 계산하는 중이에요" />}>
            <BillingSection draftInvoice={draftInvoice} invoices={invoices} isMonthFinalized={isMonthFinalized} query={query} />
          </Suspense>
        ) : (
          <AggregateLoading title="청구 예정액을 계산하는 중이에요" />
        )}
      </ScreenCard>
    </CollapseProvider>
  );
}
