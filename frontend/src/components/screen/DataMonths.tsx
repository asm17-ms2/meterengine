import { ScopedLink as Link } from "@/components/workspace/ScopedLink";
import { dataMonths } from "@/lib/api/search";
import { buildListHref, type ListQuery } from "@/lib/list-query";

export async function DataMonths({ resource, pathname, query, filterForm }: { resource: "events" | "usage" | "invoices" | "billable-metric-prices"; pathname: string; query: ListQuery; filterForm?: string }) {
  const result = await dataMonths(resource);
  if (!result.ok) return <p className="field-hint" role="status">데이터가 있는 월을 불러오지 못했습니다. 기간을 직접 지정해 조회할 수 있습니다.</p>;
  if (!result.data.months.length) return <p className="field-hint">아직 데이터가 있는 월이 없습니다.</p>;
  return <details><summary>데이터가 있는 월 ({result.data.months.length}개)</summary><nav className="workspace-actions" aria-label="데이터가 있는 월">{result.data.months.map(month => filterForm ? <button key={month} type="submit" form={filterForm} name="month" value={month} formNoValidate={resource === "events"} aria-current={query.month === month && !query.from && !query.to ? "date" : undefined}>{month}</button> : <Link key={month} href={buildListHref(pathname, query, { month, from: undefined, to: undefined })} aria-current={query.month === month ? "page" : undefined}>{month}</Link>)}</nav></details>;
}
