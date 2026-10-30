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

export type ResourceRangeError = { field: "max_amount" | "to"; message: string };

// 서버도 거꾸로 된 범위를 400으로 거절하지만, 화면에서 먼저 막아야 입력칸 옆에 고칠 곳을 보여 줄 수 있다.
export function resourceRangeErrors(parameters: URLSearchParams): ResourceRangeError[] {
  const errors: ResourceRangeError[] = [];
  const minAmount = parameters.get("min_amount");
  const maxAmount = parameters.get("max_amount");
  if (minAmount && maxAmount && Number(minAmount) > Number(maxAmount)) errors.push({ field: "max_amount", message: "최대 금액은 최소 금액보다 작을 수 없습니다." });
  const from = parameters.get("from");
  const to = parameters.get("to");
  if (from && to && from >= to) errors.push({ field: "to", message: "확정 시각 끝은 시작보다 뒤여야 합니다." });
  return errors;
}
