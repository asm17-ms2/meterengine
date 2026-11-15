import type { Result } from "@/lib/api/client";
import {
  countBillableMetricLines,
  toCustomerGroups,
  type ListBillableMetricUsagesResponse,
} from "@/lib/api/usage";

export async function UsageMeta({
  usage,
}: {
  usage: Promise<Result<ListBillableMetricUsagesResponse>>;
}) {
  const result = await usage;
  if (!result.ok) return null;

  const groups = toCustomerGroups(result.data);
  return (
    <>
      고객 <b>{result.data.summary?.customer_count ?? groups.length}</b>명, 집계 항목{" "}
      <b>{result.data.summary?.line_count ?? countBillableMetricLines(groups)}</b>개
    </>
  );
}
