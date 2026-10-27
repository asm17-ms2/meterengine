import { redirect } from "next/navigation";
import { requireTenantSession } from "@/lib/auth/session";
import { workspaceHref } from "@/lib/workspace-href";

export default async function BillableMetricPricesPage({ searchParams }: { searchParams: Promise<Record<string, string | string[] | undefined>> }) {
  const params = await searchParams;
  const session = await requireTenantSession();
  const query = new URLSearchParams();
  if (typeof params.month === "string") query.set("month", params.month);
  if (typeof params.customer_id === "string") query.set("customer_id", params.customer_id);
  const scope = new URLSearchParams({ organization_id: typeof params.organization_id === "string" ? params.organization_id : session.organization!.id });
  redirect(workspaceHref(`/pricing${query.size ? `?${query}` : ""}`, scope));
}
