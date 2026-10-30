import { ResourceSearchControls } from "@/components/screen/ResourceSearchControls";
import { ScreenCard } from "@/components/screen/ScreenCard";
import { ScreenHeader } from "@/components/screen/ScreenHeader";
import type { ListCapabilities } from "@/lib/api/capabilities";
import type { ListQuery } from "@/lib/list-query";

export function BillableMetricsFrame({
  query,
  capabilities,
  children,
}: {
  query: ListQuery;
  capabilities: ListCapabilities;
  children: React.ReactNode;
}) {
  return (
    <>
      <ScreenHeader title="미터" />
      <ScreenCard>
        <ResourceSearchControls query={query} capabilities={capabilities} resource="billable-metrics" />
        {children}
      </ScreenCard>
    </>
  );
}
