import { BillingFinalizedNotice } from "@/components/billing/BillingFinalizedNotice";
import {
  BillingTable,
  type BillingGroupView,
} from "@/components/billing/BillingTable";
import { EmptyState } from "@/components/screen/EmptyState";
import { ErrorState } from "@/components/screen/ErrorState";
import type { Result } from "@/lib/api/client";
import {
  countDraftInvoiceLines,
  isFinalizedMonth,
  type DraftInvoiceResponse,
  type DraftInvoiceCustomer,
} from "@/lib/api/billing";
import type { InvoiceResponse, ListInvoicesResponse } from "@/lib/api/invoices";
import {
  formatDecimal,
  formatKoreanMonth,
  formatKrw,
  formatKstDateTime,
} from "@/lib/format";
import { shiftMonth } from "@/lib/month";

export async function BillingSection({
  draftInvoice,
  invoices,
  month,
}: {
  draftInvoice: Promise<Result<DraftInvoiceResponse>>;
  invoices: Promise<Result<ListInvoicesResponse>>;
  month: string;
}) {
  const result = await draftInvoice;

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
    const finalized = await invoices;
    if (!finalized.ok) {
      return <ErrorState title="확정 여부를 확인하지 못했습니다" error={finalized.error} />;
    }
    if (finalized.data.invoices.length > 0) {
      return (
        <EmptyState
          title="이 달은 모두 확정됐습니다"
          body={`${result.data.month}에 확정된 인보이스가 ${finalized.data.invoices.length}건 있고, 확정 전인 고객은 없습니다.`}
          resetHref={`/invoices?month=${month}`}
          resetLabel="인보이스 보기"
        />
      );
    }
    return (
      <EmptyState
        title="등록된 고객이 없습니다"
        body={`이 도입사에 고객이 없어 ${result.data.month} 청구 예정 라인이 만들어지지 않았습니다. 고객을 먼저 등록하세요.`}
        resetHref="/billing"
      />
    );
  }

  const finalized = await invoices;
  const finalizedInvoices = finalized.ok ? finalized.data.invoices : [];
  const isFinalized = isFinalizedMonth(result.data.customers, finalizedInvoices);

  if (!isFinalized && countDraftInvoiceLines(result.data.customers) === 0) {
    return (
      <EmptyState
        title={`${formatKoreanMonth(month)} 청구 예정액이 없습니다`}
        body={`고객 ${result.data.customers.length}곳 모두 단가가 매겨진 사용량이 없습니다. 사용량이 없거나, 사용량이 있는 조합에 단가가 등록되지 않은 경우입니다.`}
        resetHref="/billable-metric-prices"
        resetLabel="가격 화면으로"
      />
    );
  }

  const billingTable = (
    <>
      <BillingTable
        groups={toBillingGroupViews(result.data.customers)}
        totalAmount={formatKrw(result.data.total_amount)}
      />
      <div className="screen-footer">
        <p className="screen-note">
          미터 라인은 고객 금액이 어느 미터에서 나왔는지 분해한 줄입니다.
          금액은 세금 전 공급가액이고 줄마다 10원 미만은 버립니다. 확정하면 청구할 금액이 있는
          고객의 사용량이 있는 줄만 인보이스에 실리고 세액 10%가 더해집니다. 이미 확정된 고객은
          이 표에 나오지 않습니다.
        </p>
      </div>
    </>
  );

  if (isFinalized) {
    return (
      <BillingFinalizedNotice
        title={toFinalizedTitle(month, finalizedInvoices)}
        invoicesHref={`/invoices?month=${month}`}
        zeroAmountCustomerCount={result.data.customers.length}
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
    lines: customer.lines.map((line) => ({
      label: line.target_property
        ? `${line.billable_metric_code} (${line.target_property})`
        : line.billable_metric_code,
      dimensionValues: line.dimension_values,
      quantity: formatDecimal(line.quantity),
      unitPrice: formatKrw(line.unit_price),
      amount: formatKrw(line.amount),
    })),
  }));
}
