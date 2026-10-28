import type { Metadata } from "next";
import { Suspense } from "react";

import { CustomersLoading } from "@/components/customers/CustomersLoading";
import { CustomersSection } from "@/components/customers/CustomersSection";
import { CAPABILITIES } from "@/lib/api/capabilities";
import { listCustomers } from "@/lib/api/customers";
import { readListQuery } from "@/lib/list-query";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

export const metadata: Metadata = { title: "고객" };

export default async function CustomersPage({
  searchParams,
}: {
  searchParams: SearchParams;
}) {
  const params = await searchParams;
  const capabilities = CAPABILITIES.listCustomers;
  const query = readListQuery(params, capabilities);
  const { devState } = query;

  const customers = devState === "loading" ? null : listCustomers(query, devState);

  if (!customers) return <CustomersLoading query={query} capabilities={capabilities} />;

  return (
    <Suspense fallback={<CustomersLoading query={query} capabilities={capabilities} />}>
      <CustomersSection customers={customers} query={query} />
    </Suspense>
  );
}
