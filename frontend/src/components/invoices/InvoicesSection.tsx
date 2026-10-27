import {
  InvoicesTable,
  type InvoiceGroupView,
  type InvoiceLineView,
} from "@/components/invoices/InvoicesTable";
import { EmptyState } from "@/components/screen/EmptyState";
import { ErrorState } from "@/components/screen/ErrorState";
import { ListPagination } from "@/components/screen/ListPagination";
import { ScreenNote } from "@/components/screen/ScreenNote";
import type { InvoicePaymentResponse } from "@/lib/api/workspace";
import type { ListCapabilities } from "@/lib/api/capabilities";
import type { Result } from "@/lib/api/client";
import type {
  InvoiceLineResponse,
  InvoiceResponse,
  ListInvoicesResponse,
} from "@/lib/api/invoices";
import {
  formatDecimal,
  formatKrw,
  formatKstDateTime,
  formatTargetPropertyTooltip,
  formatQuantityBasis,
} from "@/lib/format";
import { buildListHref, type MonthlyListQuery } from "@/lib/list-query";
import { sumOf, toMeterItems } from "@/lib/meter-items";
import { ALL_MONTHS, shiftMonth } from "@/lib/month";

export async function InvoicesSection({
  invoices,
  query,
  capabilities,
}: {
  invoices: Promise<Result<ListInvoicesResponse>>;
  query: MonthlyListQuery;
  capabilities: ListCapabilities;
}) {
  const result = await invoices;
  const { month } = query;
  const isAllMonths = month === ALL_MONTHS;

  if (!result.ok) {
    return (
      <ErrorState
        title="인보이스를 불러오지 못했습니다"
        error={result.error}
        narrowerHref={isAllMonths ? undefined : `/invoices?month=${shiftMonth(month, -1)}`}
        resetHref={
          query.customerId || query.type
            ? buildListHref("/invoices", query, {
                customerId: undefined,
                type: undefined,
                q: undefined,
                size: undefined,
                page: undefined,
              })
            : undefined
        }
      />
    );
  }

  if (result.data.invoices.length === 0 && (result.data.total ?? 0) > 0) return <><EmptyState title="이 페이지에 인보이스가 없습니다" body={`현재 조건에 맞는 인보이스는 ${result.data.total}건입니다. 첫 페이지에서 다시 확인하세요.`} resetHref={buildListHref("/invoices", query, { page: 0 })} resetLabel="첫 페이지 보기" /><ListPagination query={query} capabilities={capabilities} total={result.data.total} /></>;

  if (result.data.invoices.length === 0 && (query.q || query.customerId || query.metricCode || query.paymentStatus || query.minAmount || query.maxAmount || query.from || query.to || query.mode)) return <EmptyState title="조건에 맞는 인보이스가 없습니다" body="검색, 수납 상태, 금액과 기간 조건을 바꿔 다시 조회하세요." resetHref={buildListHref("/invoices", query, { page: 0, q: "", customerId: undefined, metricCode: undefined, paymentStatus: undefined, minAmount: undefined, maxAmount: undefined, from: undefined, to: undefined, mode: undefined })} />;

  if (result.data.invoices.length === 0) {
    return (
      <EmptyState
        title="확정된 인보이스가 없습니다"
        body={
          isAllMonths
            ? "확정된 인보이스가 없습니다. 확정 전 금액은 청구 예정액에서 봅니다."
            : `${month}에 확정된 인보이스가 없습니다. 확정 전 금액은 청구 예정액에서 봅니다.`
        }
        resetHref={
          query.customerId
            ? buildListHref("/invoices", query, { customerId: undefined, size: undefined, page: undefined })
            : "/invoices"
        }
      />
    );
  }

  return (
    <>
      {result.data.summary && <p role="status">조건 전체 {result.data.summary.invoice_count}건, 세금 포함 {formatKrw(result.data.summary.total_amount)}, 취소액 {formatKrw(result.data.summary.canceled_amount)}, {result.data.summary.has_pending ? "결과 확인 중인 결제가 있어 미수금은 확정 전입니다." : `미수금 ${formatKrw(result.data.summary.outstanding_amount ?? 0)}`}</p>}
      <InvoicesTable
        groups={result.data.invoices.map(invoice => toInvoiceGroupView(invoice, result.data.payments?.[invoice.id]))}
        totalAmount={formatKrw(
          result.data.summary?.total_amount ?? result.data.invoices.reduce((sum, invoice) => sum + invoice.total_amount, 0),
        )}
        isAllMonths={isAllMonths}
        query={query}
        capabilities={capabilities}
      />
      <ListPagination query={query} capabilities={capabilities} total={result.data.total} />
      <ScreenNote>
        금액과 미터 라인은 확정할 때 저장한 값이라 뒤에 단가나 고객 이름을 고쳐도 바뀌지
        않습니다. 합계는 공급가액에 세액을 더한 값입니다.
        반복소수는 줄여 표시하며, 금액은 정확한 분자와 분모로 계산합니다.
      </ScreenNote>
    </>
  );
}

function toInvoiceGroupView(invoice: InvoiceResponse, payment?: InvoicePaymentResponse): InvoiceGroupView {
  return {
    id: invoice.id,
    invoiceNumber: invoice.invoice_number,
    paymentStatus: payment ? ({ unpaid: "미납", pending: "결과 확인 중", paid: "납부 완료", failed: "결제 실패", partially_canceled: "부분 취소", canceled: "전액 취소" }[payment.status] ?? payment.status) : undefined,
    canceledAmount: payment ? formatKrw(payment.canceled_amount) : undefined,
    customerId: invoice.customer_id,
    customerName: invoice.customer_name,
    month: invoice.month,
    finalizedAt: formatKstDateTime(invoice.finalized_at),
    finalizedAtIso: invoice.finalized_at,
    supplyAmount: formatKrw(invoice.supply_amount),
    taxAmount: formatKrw(invoice.tax_amount),
    totalAmount: formatKrw(invoice.total_amount),
    totalAmountValue: invoice.total_amount,
    items: toMeterItems(
      invoice.id,
      invoice.lines,
      (line) => line.line_type === "graduated_tier" ? `${line.billable_metric_code}/tier/${line.tier_index ?? 0}` : line.billable_metric_code ?? line.line_type ?? "usage",
      toInvoiceLineView,
      (lines) => ({
        quantity: formatDecimal(sumOf(lines.map((line) => line.quantity))),
        amount: formatKrw(sumOf(lines.map((line) => line.amount))),
      }),
    ),
  };
}

function toInvoiceLineView(line: InvoiceLineResponse): InvoiceLineView {
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
