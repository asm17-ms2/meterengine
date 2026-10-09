import { InvoicesFrame } from "@/components/invoices/InvoicesFrame";
import { InvoicesScreen } from "@/components/invoices/InvoicesScreen";
import type { CustomerOption } from "@/components/screen/CustomerSelect";
import { EmptyState } from "@/components/screen/EmptyState";
import { ErrorState } from "@/components/screen/ErrorState";
import type { Result } from "@/lib/api/client";
import type { ListCustomersResponse } from "@/lib/api/customers";
import type { ListInvoicesResponse } from "@/lib/api/invoices";
import { formatKstMonth, shiftMonth } from "@/lib/month";

export async function InvoicesSection({
  invoices,
  customers,
  month,
  customerId,
}: {
  invoices: Promise<Result<ListInvoicesResponse>>;
  customers: Promise<Result<ListCustomersResponse>>;
  month: string | undefined;
  customerId: string | undefined;
}) {
  const [invoicesResult, customersResult] = await Promise.all([invoices, customers]);
  const customerOptions = customersResult.ok
    ? withSelectedCustomer(
        customersResult.data.customers.map((customer) => ({
          value: customer.id,
          label: toCustomerLabel(customer.name, customer.id),
        })),
        customerId,
      )
    : undefined;
  const lockedCustomerOption =
    customerOptions === undefined && customerId
      ? toLockedCustomerOption(customerId, invoicesResult)
      : undefined;

  if (!invoicesResult.ok && invoicesResult.error.code === "customer_not_found") {
    return (
      <InvoicesFrame
        month={month}
        customerId={customerId}
        customerOptions={customerOptions}
        lockedCustomerOption={lockedCustomerOption}
      >
        <EmptyState
          title="고객을 찾을 수 없습니다"
          body={invoicesResult.error.message}
          resetHref="/invoices"
        />
      </InvoicesFrame>
    );
  }

  if (!invoicesResult.ok) {
    return (
      <InvoicesFrame
        month={month}
        customerId={customerId}
        customerOptions={customerOptions}
        lockedCustomerOption={lockedCustomerOption}
      >
        <ErrorState
          title="인보이스 목록을 불러오지 못했습니다"
          error={invoicesResult.error}
          narrowerHref={month ? undefined : buildLastMonthHref(customerId)}
        />
      </InvoicesFrame>
    );
  }

  return (
    <InvoicesScreen
      invoices={invoicesResult.data.invoices}
      month={month}
      customerId={customerId}
      customerOptions={customerOptions}
      lockedCustomerOption={lockedCustomerOption}
    />
  );
}

/** 고객 API는 같은 이름을 허용하므로 표처럼 id를 같이 보여 구별한다. */
function toCustomerLabel(name: string, id: string): string {
  return `${name} ${id}`;
}

/** URL의 고객이 목록에 없어도(지워진 고객 등) select가 현재 필터를 보이게 한다. */
function withSelectedCustomer(
  options: CustomerOption[],
  customerId: string | undefined,
): CustomerOption[] {
  if (!customerId || options.some((option) => option.value === customerId)) return options;
  return [...options, { value: customerId, label: toCustomerLabel("(알 수 없는 고객)", customerId) }];
}

/** 고객 목록을 못 받아도 URL의 고객을 select에 보여, 걸러진 표를 전체 고객으로 읽지 않게 한다. */
function toLockedCustomerOption(
  customerId: string,
  invoicesResult: Result<ListInvoicesResponse>,
): CustomerOption {
  const name = invoicesResult.ok
    ? invoicesResult.data.invoices.find((invoice) => invoice.customer_id === customerId)
        ?.customer_name
    : undefined;
  return { value: customerId, label: toCustomerLabel(name ?? "(이름 확인 불가)", customerId) };
}

function buildLastMonthHref(customerId: string | undefined): string {
  const params = new URLSearchParams({ month: shiftMonth(formatKstMonth(), -1) });
  if (customerId) params.set("customer_id", customerId);
  return `/invoices?${params.toString()}`;
}
