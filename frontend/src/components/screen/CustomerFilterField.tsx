"use client";

import { useId, useState } from "react";
import { CustomerFilter, type CustomerOption } from "@/components/screen/CustomerFilter";
import { CAPABILITIES } from "@/lib/api/capabilities";
import type { ListQuery } from "@/lib/list-query";

export function CustomerFilterField({ query, customers }: { query: ListQuery; customers: CustomerOption[] }) {
  const id = useId();
  const [customerId, setCustomerId] = useState(query.customerId);
  return <div className="workspace-field"><label htmlFor={id}>고객</label><CustomerFilter id={id} query={query} capabilities={CAPABILITIES.listEvents} customers={customers} selectedCustomerId={customerId} onChange={setCustomerId} /><input type="hidden" name="customer_id" value={customerId ?? ""} /></div>;
}
