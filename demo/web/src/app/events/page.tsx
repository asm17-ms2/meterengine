import { connection } from "next/server";

import { EventSendForm, type TargetCustomers } from "@/components/EventSendForm";
import { formatKstTimestamp } from "@/lib/event-form";
import { clientFor, targetNames, toErrorView, type ClientOverrides } from "@/lib/meter-engine";

const CUSTOMER_LIST_OVERRIDES: ClientOverrides = { maxRetries: 0, timeoutMs: 3000 };

async function loadCustomers(name: string): Promise<TargetCustomers> {
  try {
    const response = await clientFor(name, CUSTOMER_LIST_OVERRIDES).listCustomers();
    return { name, customers: response.customers.map((customer) => ({ id: customer.id, name: customer.name })) };
  } catch (error) {
    return { name, customers: [], error: toErrorView(error).message };
  }
}

export default async function EventSendPage() {
  await connection();
  const defaultTimestamp = formatKstTimestamp(new Date());
  let names: string[];
  try {
    names = targetNames();
  } catch (error) {
    return <EventSendForm targets={[]} defaultTimestamp={defaultTimestamp} configError={toErrorView(error).message} />;
  }
  const targets = await Promise.all(names.map(loadCustomers));
  return <EventSendForm targets={targets} defaultTimestamp={defaultTimestamp} />;
}
