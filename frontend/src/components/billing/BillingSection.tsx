import {
  BillingTable,
  type BillingGroupView,
} from "@/components/billing/BillingTable";
import { EmptyState } from "@/components/screen/EmptyState";
import { ErrorState } from "@/components/screen/ErrorState";
import type { Result } from "@/lib/api/client";
import {
  countDraftInvoiceLines,
  type DraftInvoiceResponse,
  type DraftInvoiceCustomer,
} from "@/lib/api/billing";
import { formatDecimal, formatKrw } from "@/lib/format";
import { shiftMonth } from "@/lib/month";

export async function BillingSection({
  draftInvoice,
  month,
}: {
  draftInvoice: Promise<Result<DraftInvoiceResponse>>;
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
    return (
      <EmptyState
        title="등록된 고객이 없습니다"
        body={`이 도입사에 고객이 없어 ${result.data.month} 청구 예정 라인이 만들어지지 않았습니다. 고객을 먼저 등록하세요.`}
        resetHref="/billing"
      />
    );
  }

  if (countDraftInvoiceLines(result.data.customers) === 0) {
    return (
      <EmptyState
        title="등록된 미터가 없습니다"
        body={`고객 ${result.data.customers.length}곳이 등록돼 있지만 미터가 없어 ${result.data.month} 청구 예정 라인이 만들어지지 않았습니다. 미터를 먼저 등록하세요.`}
        resetHref="/billing"
      />
    );
  }

  return (
    <>
      <BillingTable
        groups={toBillingGroupViews(result.data.customers)}
        totalAmount={formatKrw(result.data.total_amount)}
      />
      <div className="screen-footer">
        <p className="screen-note">
          미터 라인은 고객 금액이 어느 미터에서 나왔는지 분해한 줄입니다.
          인보이스의 line item에 해당하며, 확정 시 그대로 청구서에 실립니다.
        </p>
      </div>
    </>
  );
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
      quantity: formatDecimal(line.quantity),
      unitPrice: formatKrw(line.unit_price),
      amount: formatKrw(line.amount),
    })),
  }));
}
