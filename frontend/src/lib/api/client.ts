import "server-only";

import { redirect } from "next/navigation";
import { headers } from "next/headers";
import { LIVE_ENVIRONMENT_PATH, readEnvironment, type WorkspaceEnvironment } from "@/lib/environment";
import { workspaceHref } from "@/lib/workspace-href";
import { createHash } from "node:crypto";
import { shareInFlight } from "@/lib/api/in-flight";
import { requestApi, type Result } from "@/lib/api/transport";
import { readSessionToken, requireTenantSession } from "@/lib/auth/session";
import { hasScope, tenantRequestScope } from "@/lib/auth/permissions";
import { expiredLoginHref } from "@/lib/auth/navigation";

export { toDisplayMessage } from "@/lib/api/transport";
export type { ApiError, FieldError, Result } from "@/lib/api/transport";

async function tenantRequest<T>(path: string, options: { method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE"; body?: unknown; timeoutMs?: number; idempotencyKey?: string; isEmptyResponse?: boolean; ifMatch?: string; environment?: WorkspaceEnvironment; responseType?: "text"; shareInFlight?: boolean }): Promise<Result<T>> {
  const session = await requireTenantSession();
  const scope = tenantRequestScope(path, options.method);
  if (!hasScope(session.scopes, scope)) return { ok: false, error: { status: 403, code: "permission_denied", message: scope === "read" ? "조회 권한이 없어요. 대표관리자에게 권한을 요청하세요." : "이 작업을 할 권한이 없어요. 대표관리자에게 권한을 요청하세요." } };
  // 백엔드는 환경 헤더가 없으면 legacy 환경으로 보므로 legacy 환경일 때만 헤더를 뺀다.
  const environment = options.environment ?? readEnvironment((await headers()).get("x-meterengine-environment"));
  const organizationId = (await headers()).get("x-meterengine-organization-id") || session.organization!.id;
  const token = await readSessionToken();
  const send = () => requestApi<T>(path, { ...options, organizationId, environment: environment === "legacy" ? undefined : environment, token });
  // 새로고침으로 같은 조회가 다시 와도 백엔드에 똑같이 나갈 요청이면 진행 중인 것을 기다린다. 키에 토큰을 넣어 다른 세션과 섞지 않는다.
  const result = options.shareInFlight && (options.method ?? "GET") === "GET"
    ? await shareInFlight(createHash("sha256").update([token, organizationId, environment, path].join("\n")).digest("hex"), send)
    : await send();
  if (!result.ok && result.error.status === 401) redirect(expiredLoginHref((await headers()).get("x-meterengine-current-path")));
  if (!result.ok && result.error.code === "organization_not_active") redirect("/organizations");
  // 화면을 연 뒤에 잠긴 환경으로 요청이 가면 요청에 쓴 조직의 이용 신청 화면으로 보낸다.
  if (!result.ok && result.error.code === "live_environment_locked") redirect(workspaceHref(LIVE_ENVIRONMENT_PATH, new URLSearchParams({ organization_id: organizationId })));
  return result;
}

// 그 달 이벤트를 집계하는 조회는 데이터가 메모리에 없을 때 5초를 넘을 수 있어 10초를 기다린다.
export const AGGREGATE_TIMEOUT_MS = 10_000;
export const AGGREGATE_REQUEST = { timeoutMs: AGGREGATE_TIMEOUT_MS, shareInFlight: true } as const;

export async function serverFetch<T>(
  _baseUrl: string, path: string, searchParams?: Record<string, string | undefined>, options: { timeoutMs?: number; shareInFlight?: boolean } = {},
): Promise<Result<T>> {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(searchParams ?? {})) {
    if (value !== undefined) query.set(key, value);
  }
  return tenantRequest<T>(`${path}${query.size ? `?${query}` : ""}`, { timeoutMs: options.timeoutMs, shareInFlight: options.shareInFlight });
}

export async function serverSend<T>(
  _baseUrl: string, path: string, options: { method: "POST" | "PUT" | "PATCH" | "DELETE"; body?: unknown; timeoutMs?: number; idempotencyKey?: string; isEmptyResponse?: boolean; ifMatch?: string; environment?: WorkspaceEnvironment },
): Promise<Result<T>> {
  return tenantRequest<T>(path, options);
}

export async function tenantExport(path: string, searchParams?: Record<string, string | undefined>): Promise<Result<{ body: string; contentType: string; filename?: string }>> {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(searchParams ?? {})) if (value !== undefined && key !== "environment") query.set(key, value);
  return tenantRequest(`${path}${query.size ? `?${query}` : ""}`, { responseType: "text", timeoutMs: 30_000 });
}
