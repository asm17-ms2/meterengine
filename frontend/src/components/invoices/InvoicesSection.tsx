import {
  InvoicesTable,
  type InvoiceGroupView,
  type InvoiceLineView,
} from "@/components/invoices/InvoicesTable";
import { EmptyState } from "@/components/screen/EmptyState";
import { ErrorState } from "@/components/screen/ErrorState";
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
} from "@/lib/format";
import { sumOf, toMeterItems } from "@/lib/meter-items";
import { shiftMonth } from "@/lib/month";

export async function InvoicesSection({
  invoices,
  month,
}: {
  invoices: Promise<Result<ListInvoicesResponse>>;
  month: string;
}) {
  const result = await invoices;

  if (!result.ok) {
    return (
      <ErrorState
        title="인보이스를 불러오지 못했습니다"
        error={result.error}
        narrowerHref={`/invoices?month=${shiftMonth(month, -1)}`}
      />
    );
  }

  if (result.data.invoices.length === 0) {
    return (
      <EmptyState
        title="확정된 인보이스가 없습니다"
        body={`${month}에 확정된 인보이스가 없습니다. 확정 전 금액은 청구 예정액에서 봅니다.`}
        resetHref="/invoices"
      />
    );
  }

  return (
    <>
      <InvoicesTable
        groups={result.data.invoices.map(toInvoiceGroupView)}
        totalAmount={formatKrw(
          result.data.invoices.reduce((sum, invoice) => sum + invoice.total_amount, 0),
        )}
      />
      <div className="screen-footer">
        <p className="screen-note">
          금액과 미터 라인은 확정할 때 저장한 값이라 뒤에 단가나 고객 이름을 고쳐도 바뀌지
          않습니다. 합계는 공급가액에 세액을 더한 값입니다.
        </p>
      </div>
    </>
  );
}

function toInvoiceGroupView(invoice: InvoiceResponse): InvoiceGroupView {
  return {
    id: invoice.id,
    customerId: invoice.customer_id,
    customerName: invoice.customer_name,
    finalizedAt: formatKstDateTime(invoice.finalized_at),
    supplyAmount: formatKrw(invoice.supply_amount),
    taxAmount: formatKrw(invoice.tax_amount),
    totalAmount: formatKrw(invoice.total_amount),
    items: toMeterItems(
      invoice.id,
      invoice.lines,
      (line) => line.billable_metric_code,
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
    label: line.billable_metric_code,
    labelTooltip: formatTargetPropertyTooltip(line.target_property),
    dimensionValues: line.dimension_values,
    quantity: formatDecimal(line.quantity),
    unitPrice: formatKrw(line.unit_price),
    amount: formatKrw(line.amount),
  };
}
