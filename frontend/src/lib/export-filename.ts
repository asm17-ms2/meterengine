type ExportScope = { month?: string; from?: string; to?: string; environment?: string | null };

const UNSAFE_FILENAME_CHARACTERS = /[^A-Za-z0-9_-]+/g;

function safe(value: string): string {
  return value.replace(UNSAFE_FILENAME_CHARACTERS, "-").replace(/-+$/, "");
}

/** 조회 기간과 업무 환경을 담은 CSV 파일 이름. legacy 환경은 이름에 넣지 않는다. */
export function exportFilename(resource: string, scope: ExportScope): string {
  const period = scope.from && scope.to ? `${safe(scope.from)}_${safe(scope.to)}` : scope.month ? safe(scope.month) : "";
  const environment = scope.environment && scope.environment !== "legacy" ? safe(scope.environment) : "";
  return [resource, period, environment].filter(Boolean).join("-") + ".csv";
}

export function filenameFromDisposition(disposition: string | null, resource: string): string {
  const name = disposition?.match(/filename="([^"]+)"/)?.[1];
  return name ?? `${resource}.csv`;
}
