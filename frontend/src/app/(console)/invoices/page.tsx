import { Suspense } from "react";

import { InvoicesLoading } from "@/components/invoices/InvoicesLoading";
import { InvoicesSection } from "@/components/invoices/InvoicesSection";
import { listCustomers, readCustomerId } from "@/lib/api/customers";
import { listInvoices } from "@/lib/api/invoices";
import { readDevState } from "@/lib/dev-state";
import { readOptionalMonth } from "@/lib/month";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

export default async function InvoicesPage({
  searchParams,
}: {
  searchParams: SearchParams;
}) {
  const params = await searchParams;
  const month = readOptionalMonth(params.month);
  const customerId = readCustomerId(params.customer_id);
  const devState = readDevState(params.state);

  if (devState === "loading") return <InvoicesLoading month={month} />;

  return (
    <Suspense fallback={<InvoicesLoading month={month} />}>
      <InvoicesSection
        invoices={listInvoices({ month, customerId }, devState)}
        customers={listCustomers(devState)}
        month={month}
        customerId={customerId}
      />
    </Suspense>
  );
}
