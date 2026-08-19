import {
  BillingTable,
  type BillingGroupView,
} from "@/components/billing/BillingTable";
import { EmptyState } from "@/components/screen/EmptyState";
import { ErrorState } from "@/components/screen/ErrorState";
import type { Result } from "@/lib/api/client";
import {
  countInvoiceLines,
  type DraftInvoice,
  type InvoiceCustomer,
} from "@/lib/api/invoice";
import { formatDecimal, formatKrw, formatKstStamp } from "@/lib/format";
import { shiftMonth } from "@/lib/month";

/**
 * 프라미스를 await하는 서버 컴포넌트. 페이지가 이걸 <Suspense>로 감싸서
 * 표 영역만 스켈레톤으로 바뀌고 화면 제목과 필터 행은 남는다.
 */
export async function BillingSection({
  invoice,
  month,
}: {
  invoice: Promise<Result<DraftInvoice>>;
  month: string;
}) {
  const result = await invoice;

  if (!result.ok) {
    return (
      <ErrorState
        title="청구 예정액을 계산하지 못했습니다"
        error={result.error}
        narrowerHref={`/billing?month=${shiftMonth(month, -1)}`}
      />
    );
  }

  // 표가 비는 경로가 둘이고 운영자가 할 일이 다르다 (UsageSection과 같은 구분).
  // 백엔드는 모든 고객에게 모든 미터의 라인을 만들어 주므로, 고객이 있는데 라인
  // 합이 0이라는 것은 등록된 미터가 없다는 뜻이다. 수집된 이벤트 유무는 둘 다와
  // 무관하다. 이벤트가 0건인 고객도 수량 0 라인으로 남기 때문이다.
  if (result.data.customers.length === 0) {
    return (
      <EmptyState
        title="등록된 고객이 없습니다"
        body={`이 도입사에 고객이 없어 ${result.data.month} 청구 예정 라인이 만들어지지 않았습니다. 고객을 먼저 등록하세요.`}
        resetHref="/billing"
      />
    );
  }

  if (countInvoiceLines(result.data.customers) === 0) {
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

/** 화면 제목 오른쪽 메타. 같은 프라미스를 보되 Suspense 경계가 따로다. */
export async function BillingMeta({
  invoice,
}: {
  invoice: Promise<Result<DraftInvoice>>;
}) {
  const result = await invoice;
  if (!result.ok) return null;

  return (
    <>
      고객 <b>{result.data.customers.length}</b>곳, 청구 라인{" "}
      <b>{countInvoiceLines(result.data.customers)}</b>줄
      <br />
      계산 시각 {formatKstStamp(new Date(result.data.calculated_at))}
    </>
  );
}

/**
 * 와이어 응답을 표가 그릴 문자열로 바꾼다. 수량과 단가는 소수가 올 수 있어
 * formatDecimal을 쓰고, 금액은 서버가 절사까지 끝낸 정수라 원화 표기만 한다.
 */
function toBillingGroupViews(customers: InvoiceCustomer[]): BillingGroupView[] {
  return customers.map((customer) => ({
    customerId: customer.customer_id,
    customerName: customer.customer_name,
    amount: formatKrw(customer.amount),
    lines: customer.lines.map((line) => ({
      label: line.target_property
        ? `${line.metric_code} (${line.target_property})`
        : line.metric_code,
      quantity: formatDecimal(line.quantity),
      unitPrice: formatKrw(line.unit_price),
      amount: formatKrw(line.amount),
    })),
  }));
}
