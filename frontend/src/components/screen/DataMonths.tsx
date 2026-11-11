import { Button } from "antd";
import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import { dataMonths } from "@/lib/api/search";
import { buildListHref, type ListQuery } from "@/lib/list-query";

export async function DataMonths({ resource, pathname, query, filterForm }: { resource: "events" | "usage" | "invoices" | "billable-metric-prices"; pathname: string; query: ListQuery; filterForm?: string }) {
  const result = await dataMonths(resource);
  if (!result.ok) return <p className="field-hint" role="status">데이터가 있는 월을 불러오지 못했어요. 조회 월을 직접 고르세요.</p>;
  if (!result.data.months.length) return <p className="field-hint">아직 데이터가 있는 월이 없어요.</p>;
  return <details><summary className="disclosure-summary">데이터가 있는 월 ({result.data.months.length}개)</summary><nav className="workspace-actions" aria-label="데이터가 있는 월">{result.data.months.map(month => {
    const isSelected = query.month === month && !query.from && !query.to;
    return filterForm
      ? <Button key={month} size="small" type={isSelected ? "primary" : "default"} htmlType="submit" form={filterForm} name="month" value={month} formNoValidate={resource === "events"} aria-current={isSelected ? "date" : undefined}>{month}</Button>
      : <Link key={month} href={buildListHref(pathname, query, { month, from: undefined, to: undefined })} aria-current={query.month === month ? "page" : undefined}>{month}</Link>;
  })}</nav></details>;
}
