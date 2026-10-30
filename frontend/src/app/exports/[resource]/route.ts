import { tenantExport } from "@/lib/api/client";
import { CAPABILITIES } from "@/lib/api/capabilities";
import { exportFilename } from "@/lib/export-filename";
import { readListQuery, toServerParams } from "@/lib/list-query";
import { normalizeResourceQuery, searchParametersFromUrl } from "@/lib/resource-query";

const RESOURCES = {
  events: { path: "/v1/events/export", capabilities: CAPABILITIES.listEvents },
  customers: { path: "/v1/customers/export", capabilities: CAPABILITIES.listCustomers },
  "billable-metrics": { path: "/v1/billable-metrics/export", capabilities: CAPABILITIES.listBillableMetrics },
  "billable-metric-prices": { path: "/v1/billable-metric-prices/export", capabilities: CAPABILITIES.listBillableMetricPrices },
  usage: { path: "/v1/usage/export", capabilities: CAPABILITIES.aggregateBillableMetricUsages },
  billing: { path: "/v1/invoices/draft/export", capabilities: CAPABILITIES.previewDraftInvoice },
  invoices: { path: "/v1/invoices/export", capabilities: CAPABILITIES.listInvoices },
};

export async function GET(request: Request, context: { params: Promise<{ resource: string }> }) {
  const { resource } = await context.params;
  if (!Object.hasOwn(RESOURCES, resource)) return new Response("찾을 수 없는 내보내기입니다.", { status: 404 });
  const target = RESOURCES[resource as keyof typeof RESOURCES];
  const url = new URL(request.url);
  const search = normalizeResourceQuery(resource, searchParametersFromUrl(url.searchParams));
  const query = ["events", "usage", "billing", "invoices", "billable-metric-prices"].includes(resource) ? readListQuery(search, target.capabilities, { hasMonth: true }) : readListQuery(search, target.capabilities);
  const params = toServerParams(query, target.capabilities);
  if (resource === "events") {
    const includeProperties = search.include_properties;
    if (includeProperties !== undefined) params.include_properties = Array.isArray(includeProperties) ? includeProperties[0] : includeProperties;
  }
  delete params.page;
  delete params.size;
  const result = await tenantExport(target.path, params);
  if (!result.ok) return Response.json({ code: result.error.code, message: result.error.message }, { status: result.error.status || 503, headers: { "Cache-Control": "no-store" } });
  const csv = result.data.body.startsWith("\uFEFF") ? result.data.body : "\uFEFF" + result.data.body;
  return new Response(csv, { headers: { "Content-Type": result.data.contentType, "Content-Disposition": `attachment; filename="${exportFilename(resource, { month: query.month, from: query.from, to: query.to, environment: url.searchParams.get("environment") })}"`, "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff" } });
}
