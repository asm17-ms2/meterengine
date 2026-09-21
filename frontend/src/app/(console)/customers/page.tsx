import { Suspense } from "react";

import { CustomersLoading } from "@/components/customers/CustomersLoading";
import { CustomersSection } from "@/components/customers/CustomersSection";
import { listCustomers } from "@/lib/api/customers";
import { readDevState } from "@/lib/dev-state";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

export default async function CustomersPage({
  searchParams,
}: {
  searchParams: SearchParams;
}) {
  const params = await searchParams;
  const devState = readDevState(params.state);

  const customers = devState === "loading" ? null : listCustomers(devState);

  if (!customers) return <CustomersLoading />;

  return (
    <Suspense fallback={<CustomersLoading />}>
      <CustomersSection customers={customers} />
    </Suspense>
  );
}
