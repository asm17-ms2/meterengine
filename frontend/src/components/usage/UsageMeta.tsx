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
      고객 <b>{groups.length}</b>곳, 미터 라인{" "}
      <b>{countBillableMetricLines(groups)}</b>줄
    </>
  );
}
