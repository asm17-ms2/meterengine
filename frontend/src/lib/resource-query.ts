type SearchParameters = Record<string, string | string[] | undefined>;

export function normalizeResourceQuery(resource: string, parameters: SearchParameters): SearchParameters {
  if (resource !== "billable-metric-prices") return { ...parameters };
  const metricStatus = parameters.metric_status;
  const status = typeof metricStatus === "string" && ["active", "archived", "all"].includes(metricStatus) ? metricStatus : "active";
  const archiveStatus = (value: string | string[] | undefined) => value === "archived" || value === "all" ? value : "active";
  return { ...parameters, status, metric_status: status, plan_status: archiveStatus(parameters.plan_status ?? parameters.status), rate_status: archiveStatus(parameters.rate_status ?? parameters.status) };
}

export function searchParametersFromUrl(search: URLSearchParams): SearchParameters {
  const result: SearchParameters = {};
  for (const key of new Set(search.keys())) {
    const values = search.getAll(key);
    result[key] = values.length === 1 ? values[0] : values;
  }
  return result;
}

export function csvDownloadParameters(resource: string, search: URLSearchParams, organizationId: string): URLSearchParams {
  const parameters = normalizeResourceQuery(resource, searchParametersFromUrl(search));
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(parameters)) {
    const first = Array.isArray(value) ? value[0] : value;
    if (key !== "page" && key !== "size" && typeof first === "string") query.set(key, first);
  }
  query.set("organization_id", organizationId);
  return query;
}

export function submittedResourceParameters(fields: Iterable<readonly [string, FormDataEntryValue]>, submitter?: { name: string; value: string }): URLSearchParams {
  const query = new URLSearchParams();
  for (const [name, value] of fields) {
    if (name === "page" || typeof value !== "string" || value.trim() === "") continue;
    query.set(name, name === "q" ? value.trim() : value);
  }
  if (submitter?.name) query.set(submitter.name, submitter.value);
  if (submitter?.name === "month") {
    query.delete("from");
    query.delete("to");
    query.delete("range_mode");
  }
  if (query.has("from") || query.has("to")) query.delete("month");
  return query;
}
