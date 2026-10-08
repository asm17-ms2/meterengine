import Link from "next/link";

import { InvoicesFrame } from "@/components/invoices/InvoicesFrame";
import { InvoicesTable, type InvoiceRowView } from "@/components/invoices/InvoicesTable";
import type { CustomerOption } from "@/components/screen/CustomerSelect";
import { EmptyState } from "@/components/screen/EmptyState";
import type { InvoiceResponse } from "@/lib/api/invoices";
import { formatKoreanMonth, formatKrw, formatKstDateTime, formatKstStamp } from "@/lib/format";
import { formatKstMonth, isAwaitingFinalization, shiftMonth } from "@/lib/month";

export function InvoicesScreen({
  invoices,
  month,
  customerId,
  customerOptions,
  lockedCustomerOption,
}: {
  invoices: InvoiceResponse[];
  month: string | undefined;
  customerId: string | undefined;
  /** 없으면 고객 목록을 못 받은 것이라 고객 필터를 잠근다. */
  customerOptions: CustomerOption[] | undefined;
  lockedCustomerOption: CustomerOption | undefined;
}) {
  const customerCount = new Set(invoices.map((invoice) => invoice.customer_id)).size;
  const isCustomerFilterLocked = customerId !== undefined && customerOptions === undefined;

  return (
    <InvoicesFrame
      month={month}
      customerId={customerId}
      customerOptions={customerOptions}
      lockedCustomerOption={lockedCustomerOption}
      meta={
        <>
          인보이스 <b>{invoices.length}</b>건, 고객 <b>{customerCount}</b>곳
          <br />
          {isCustomerFilterLocked ? (
            <span className="screen-note">
              고객 목록을 불러오지 못해 고객 필터를 바꿀 수 없습니다
            </span>
          ) : invoices.length > 0 ? (
            <>마지막 확정 {formatKstStamp(latestFinalizedAt(invoices))}</>
          ) : null}
        </>
      }
    >
      {invoices.length === 0 ? (
        <InvoicesEmptyState month={month} customerId={customerId} />
      ) : (
        <>
          <InvoicesTable
            rows={invoices.map(toInvoiceRowView)}
            totalRow={
              month ? { customerCount, totalAmount: formatKrw(sumTotalAmount(invoices)) } : null
            }
          />
          <div className="screen-footer">
            <p className="screen-note">
              정렬: 대상 기간 최신순, 같은 달은 고객명 오름차순. 합계 금액(공급가액 + 세액)과
              고객명은 확정 시점에 저장한 값이라 뒤에 고객 이름을 고쳐도 바뀌지 않습니다. 확정 전
              금액은 <Link href="/billing">청구 예정액</Link>에서 봅니다.
            </p>
          </div>
        </>
      )}
    </InvoicesFrame>
  );
}

function InvoicesEmptyState({
  month,
  customerId,
}: {
  month: string | undefined;
  customerId: string | undefined;
}) {
  if (!month && !customerId) {
    return (
      <div className="empty-state">
        <div className="empty-state__title">확정된 인보이스가 없습니다</div>
        <p className="empty-state__body">
          인보이스는 매월 1일 14:00(KST)에 지난달 분이 자동으로 확정되며, 그 달에 이벤트가 있는
          고객만 대상입니다. 확정 전 금액은 청구 예정액에서 봅니다.
        </p>
        <Link className="btn btn-secondary" href="/billing" style={{ marginTop: 4 }}>
          청구 예정액 보기
        </Link>
      </div>
    );
  }

  if (month === formatKstMonth()) {
    return (
      <div className="empty-state">
        <div className="empty-state__title">
          {formatKoreanMonth(month)} 분은 아직 확정되지 않았습니다
        </div>
        <p className="empty-state__body">
          이번 달 분은 {formatKoreanMonth(shiftMonth(month, 1))} 1일 14:00(KST)에 자동
          확정됩니다. 그 전 금액은 청구 예정액에서 봅니다.
        </p>
        <NotYetFinalizedActions month={month} />
      </div>
    );
  }

  if (month && isAwaitingFinalization(month)) {
    return (
      <div className="empty-state">
        <div className="empty-state__title">
          {formatKoreanMonth(month)} 분은 오늘 14:00(KST)에 확정 예정입니다
        </div>
        <p className="empty-state__body">
          지난달 분은 매월 1일 14:00(KST)에 자동 확정됩니다. 확정되면 여기에 나오고, 그 전 금액은
          청구 예정액에서 봅니다.
        </p>
        <NotYetFinalizedActions month={month} />
      </div>
    );
  }

  return (
    <EmptyState
      title="조건에 맞는 인보이스가 없습니다"
      body={`${describeMissing(month, customerId)} 그 달에 이벤트가 없던 고객은 확정 대상이 아닙니다.`}
      resetHref="/invoices"
    />
  );
}

function NotYetFinalizedActions({ month }: { month: string }) {
  return (
    <div style={{ display: "flex", gap: 8, marginTop: 4 }}>
      <Link className="btn btn-secondary" href={`/billing?month=${month}`}>
        청구 예정액 보기
      </Link>
      <Link className="btn btn-secondary" href="/invoices">
        필터 초기화
      </Link>
    </div>
  );
}

function describeMissing(month: string | undefined, customerId: string | undefined): string {
  if (month && customerId) {
    return `${formatKoreanMonth(month)} 분으로 확정된 선택한 고객의 인보이스가 없습니다.`;
  }
  if (month) return `${formatKoreanMonth(month)} 분으로 확정된 인보이스가 없습니다.`;
  return "선택한 고객의 확정된 인보이스가 없습니다.";
}

function toInvoiceRowView(invoice: InvoiceResponse): InvoiceRowView {
  return {
    id: invoice.id,
    customerId: invoice.customer_id,
    customerName: invoice.customer_name,
    month: formatKoreanMonth(invoice.month),
    finalizedAt: formatKstDateTime(invoice.finalized_at),
    totalAmount: formatKrw(invoice.total_amount),
  };
}

function sumTotalAmount(invoices: InvoiceResponse[]): number {
  return invoices.reduce((sum, invoice) => sum + invoice.total_amount, 0);
}

function latestFinalizedAt(invoices: InvoiceResponse[]): Date {
  return new Date(
    Math.max(...invoices.map((invoice) => new Date(invoice.finalized_at).getTime())),
  );
}
