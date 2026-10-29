import { Alert } from "antd";
import { BillingFinalizedNotice } from "@/components/billing/BillingFinalizedNotice";
import {
  BillingTable,
  type BillingGroupView,
  type BillingLineView,
  type BillingUnpricedUsageView,
} from "@/components/billing/BillingTable";
import { EmptyState } from "@/components/screen/EmptyState";
import { ErrorState } from "@/components/screen/ErrorState";
import { ListPagination } from "@/components/screen/ListPagination";
import { ScreenNote } from "@/components/screen/ScreenNote";
import { CAPABILITIES } from "@/lib/api/capabilities";
import type { Result } from "@/lib/api/client";
import {
  countDraftInvoiceLines,
  hasUnpricedUsages,
  type DraftInvoiceResponse,
  type DraftInvoiceCustomer,
  type DraftInvoiceLine,
  type DraftInvoiceUnpricedUsage,
} from "@/lib/api/billing";
import type { InvoiceResponse, ListInvoicesResponse } from "@/lib/api/invoices";
import {
  formatDecimal,
  formatKoreanMonth,
  formatKrw,
  formatKstDateTime,
  formatTargetPropertyTooltip,
  formatQuantityBasis,
} from "@/lib/format";
import { buildListHref, type MonthlyListQuery } from "@/lib/list-query";
import { sumOf, toMeterItems } from "@/lib/meter-items";
import { shiftMonth } from "@/lib/month";

export async function BillingSection({
  draftInvoice,
  invoices,
  isMonthFinalized,
  query,
}: {
  draftInvoice: Promise<Result<DraftInvoiceResponse>>;
  invoices: Promise<Result<ListInvoicesResponse>>;
  isMonthFinalized: Promise<boolean>;
  query: MonthlyListQuery;
}) {
  const { month } = query;
  const [result, isFinalized] = await Promise.all([draftInvoice, isMonthFinalized]);

  if (!result.ok) {
    return (
      <ErrorState
        title="청구 예정액을 계산하지 못했습니다"
        error={result.error}
        narrowerHref={`/billing?month=${shiftMonth(month, -1)}`}
      />
    );
  }

  if (result.data.customers.length === 0) {
    if (result.data.summary && (result.data.total ?? 0) > 0) return <><EmptyState title="이 페이지에 청구 예정 내역이 없습니다" body={`현재 조건에 맞는 고객은 ${result.data.total}곳입니다. 첫 페이지에서 다시 확인하세요.`} resetHref={buildListHref("/billing", query, { page: 0 })} resetLabel="첫 페이지 보기" /><ListPagination query={query} capabilities={CAPABILITIES.previewDraftInvoice} total={result.data.total} /></>;
    if (isFinalized) return <EmptyState title="이 달의 청구는 확정됐습니다" body={query.customerId ? "선택한 고객과 조건에 표시할 청구 예정 내역이 없습니다. 확정 인보이스는 같은 고객 조건에서 확인할 수 있습니다." : "현재 조건에 표시할 청구 예정 내역이 없습니다. 확정 인보이스에서 청구 결과를 확인하세요."} resetHref={buildListHref("/invoices", query, { q: "", metricCode: undefined, minAmount: undefined, maxAmount: undefined, sort: undefined, order: undefined, page: 0 })} resetLabel="인보이스 보기" />;
    if (result.data.summary && result.data.summary.month_customer_count > 0) return <EmptyState title="조건에 맞는 청구 예정 내역이 없습니다" body={`검색 조건을 바꿔 확인하세요. 월 전체 확정 대상은 ${result.data.summary.month_billable_customer_count}곳입니다.`} resetHref={buildListHref("/billing", query, { page: 0, q: "", customerId: undefined, metricCode: undefined, minAmount: undefined, maxAmount: undefined })} />;
    const finalized = await invoices;
    if (!finalized.ok) {
      return <ErrorState title="확정 여부를 확인하지 못했습니다" error={finalized.error} />;
    }
    if (finalized.data.invoices.length > 0) {
      return (
        <EmptyState
          title={query.customerId ? "선택한 고객의 인보이스가 확정됐습니다" : "이 달은 모두 확정됐습니다"}
          body={`${result.data.month}에 ${query.customerId ? "선택한 고객의 " : ""}확정 인보이스가 ${finalized.data.invoices.length}건 있고, 현재 조건에 확정 전인 고객은 없습니다.`}
          resetHref={`/invoices?month=${month}`}
          resetLabel="인보이스 보기"
        />
      );
    }
    return (
      <EmptyState
        title={query.customerId ? "선택한 고객의 청구 예정 내역이 없습니다" : "등록된 고객이 없습니다"}
        body={query.customerId ? "다른 고객을 선택하거나 확정 인보이스에서 청구 내역을 확인하세요." : `이 도입사에 고객이 없어 ${result.data.month} 청구 예정 라인이 만들어지지 않았습니다. 고객을 먼저 등록하세요.`}
        resetHref={`/billing?month=${month}`}
      />
    );
  }

  const negativeCustomers = result.data.customers.filter(customer => (customer.negative_usages?.length ?? 0) > 0);
  const finalized = await invoices;
  const finalizedInvoices = finalized.ok ? finalized.data.invoices : [];

  if (
    !isFinalized &&
    (result.data.summary ? result.data.summary.line_count === 0 && result.data.summary.unpriced_count === 0 && result.data.summary.negative_count === 0 : countDraftInvoiceLines(result.data.customers) === 0 && !hasUnpricedUsages(result.data.customers) && negativeCustomers.length === 0)
  ) {
    return (
      <EmptyState
        title={`${formatKoreanMonth(month)} 청구 예정액이 없습니다`}
        body={`현재 조건에 맞는 고객 ${result.data.summary?.customer_count ?? result.data.customers.length}곳 모두 ${result.data.month}에 청구할 사용량이 없습니다.`}
        resetHref={`/usage?month=${month}`}
        resetLabel="사용량 화면으로"
      />
    );
  }

  const billingTable = (
    <>
      {result.data.summary && <p role="status">조건 전체 {result.data.summary.customer_count}곳, 예정액 {formatKrw(result.data.summary.amount)}, 단가 없는 조합 {result.data.summary.unpriced_count}개. 확정 작업은 필터와 페이지에 관계없이 이 달 전체 {result.data.summary.month_billable_customer_count}곳을 대상으로 합니다.</p>}
      {negativeCustomers.length > 0 && <Alert showIcon type="warning" title="음수 집계 조합은 0원으로 처리했습니다" description={<><p>개별 음수 이벤트도 선택한 집계 함수에 따라 반영합니다. 단위 변환 전 집계값이 음수인 조합은 청구에서 제외했습니다. 확정하기 전에 사용량을 확인하세요.</p><details><summary>제외한 조합 보기</summary><ul>{negativeCustomers.flatMap(customer => (customer.negative_usages ?? []).map((usage, index) => <li key={`${customer.customer_id}-${index}`}>{customer.customer_name} / {usage.billable_metric_code} / {Object.entries(usage.dimension_values).map(([key,value]) => `${key}: ${String(value)}`).join(", ") || "모든 사용량"} / 집계값 {formatDecimal(usage.quantity)}</li>))}</ul></details></>} />}

      <BillingTable
        groups={toBillingGroupViews(result.data.customers)}
        totalAmount={formatKrw(result.data.total_amount)}
        query={query}
        capabilities={CAPABILITIES.previewDraftInvoice}
      />
      <ListPagination
        query={query}
        capabilities={CAPABILITIES.previewDraftInvoice}
        total={result.data.total}
      />
      <ScreenNote>
        미터 라인은 고객 금액이 어느 미터에서 나왔는지 분해한 줄입니다.
        금액은 세금 전 공급가액이고 줄마다 10원 미만은 버립니다. 단가가 등록되지 않은 조합의
        사용량은 고객마다 기타 줄에 모으고 금액에 넣지 않습니다. 확정하면 청구할 금액이 있는
        고객의 사용량이 있는 줄만 인보이스에 실리고 세액 10%가 더해집니다. 이미 확정된 고객은
        이 표에 나오지 않습니다.
        반복소수는 줄여 표시하며, 금액은 정확한 분자와 분모로 계산합니다.
      </ScreenNote>
    </>
  );

  if (isFinalized) {
    return (
      <BillingFinalizedNotice
        title={finalizedInvoices.length ? toFinalizedTitle(month, finalizedInvoices) : `${formatKoreanMonth(month)} 청구는 확정됐습니다`}
        invoicesHref={buildListHref("/invoices", query, { q: "", metricCode: undefined, minAmount: undefined, maxAmount: undefined, sort: undefined, order: undefined, page: 0 })}
        zeroAmountCustomerCount={result.data.summary?.customer_count ?? result.data.customers.length}
      >
        {billingTable}
      </BillingFinalizedNotice>
    );
  }

  return billingTable;
}

function toFinalizedTitle(month: string, invoices: InvoiceResponse[]): string {
  const finalizedAts = invoices.map((invoice) => formatKstDateTime(invoice.finalized_at)).sort();
  const lastFinalizedAt = finalizedAts[finalizedAts.length - 1];
  const prefix = `${formatKoreanMonth(month)} 인보이스 ${invoices.length}건이`;
  return finalizedAts[0] === lastFinalizedAt
    ? `${prefix} ${lastFinalizedAt}에 확정됐습니다`
    : `${prefix} 확정됐습니다. 마지막 확정은 ${lastFinalizedAt}입니다`;
}

function toBillingGroupViews(customers: DraftInvoiceCustomer[]): BillingGroupView[] {
  return customers.map((customer) => ({
    customerId: customer.customer_id,
    customerName: customer.customer_name,
    amount: formatKrw(customer.amount),
    amountValue: customer.amount,
    items: toMeterItems(
      customer.customer_id,
      customer.lines,
      (line) => line.line_type === "graduated_tier" ? `${line.billable_metric_code}/tier/${line.tier_index ?? 0}` : line.billable_metric_code ?? line.line_type ?? "usage",
      toBillingLineView,
      (lines) => ({
        quantity: formatDecimal(sumOf(lines.map((line) => line.quantity))),
        amount: formatKrw(sumOf(lines.map((line) => line.amount))),
      }),
    ),
    unpricedUsage: toBillingUnpricedUsageView(customer.unpriced_usages),
  }));
}

function toBillingUnpricedUsageView(
  usages: DraftInvoiceUnpricedUsage[],
): BillingUnpricedUsageView | null {
  if (usages.length === 0) return null;
  return {
    quantity: formatDecimal(sumOf(usages.map((usage) => usage.quantity))),
    lines: usages.map((usage) => ({
      label: usage.billable_metric_code,
      labelTooltip: formatTargetPropertyTooltip(usage.target_property),
      dimensionValues: usage.dimension_values,
      quantity: formatDecimal(usage.quantity),
    })),
  };
}

function toBillingLineView(line: DraftInvoiceLine): BillingLineView {
  return {
    label: line.line_type === "base_fee" ? "기본료" : line.line_type === "minimum_adjustment" ? "최소 사용료 조정" : line.line_type === "graduated_tier" ? `${line.billable_metric_code} / 구간 ${(line.tier_index ?? 0) + 1}` : line.line_type === "volume" ? `${line.billable_metric_code} / 총량 단가` : line.billable_metric_code ?? "사용료",
    labelTooltip: formatTargetPropertyTooltip(line.target_property),
    dimensionValues: line.dimension_values,
    quantity: formatDecimal(line.quantity),
    quantityBasis: formatQuantityBasis(line.quantity_numerator, line.quantity_denominator),
    unitPrice: formatKrw(line.unit_price),
    amount: formatKrw(line.amount),
  };
}
