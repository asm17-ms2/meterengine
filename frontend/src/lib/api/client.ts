import "server-only";

import { redirect } from "next/navigation";
import { headers } from "next/headers";
import { requestApi, type Result } from "@/lib/api/transport";
import { readSessionToken, requireTenantSession } from "@/lib/auth/session";
import { hasScope, tenantRequestScope } from "@/lib/auth/permissions";

export { toDisplayMessage } from "@/lib/api/transport";
export type { ApiError, FieldError, Result } from "@/lib/api/transport";

async function tenantRequest<T>(path: string, options: { method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE"; body?: unknown; timeoutMs?: number; idempotencyKey?: string; isEmptyResponse?: boolean; ifMatch?: string }): Promise<Result<T>> {
  const session = await requireTenantSession();
  const scope = tenantRequestScope(path, options.method);
  if (!hasScope(session.scopes, scope)) return { ok: false, error: { status: 403, code: "permission_denied", message: scope === "read" ? "현재 도입사의 조회 권한이 없습니다. 대표관리자에게 권한을 요청하세요." : "이 작업을 수행할 권한이 없습니다. 대표관리자에게 권한을 요청하세요." } };
  const result = await requestApi<T>(path, { ...options, organizationId: (await headers()).get("x-meterengine-organization-id") || session.organization!.id, token: await readSessionToken() });
  if (!result.ok && result.error.status === 401) redirect("/login?reason=expired");
  if (!result.ok && result.error.code === "organization_not_active") redirect("/organizations");
  return result;
}

export async function serverFetch<T>(
  _baseUrl: string, path: string, searchParams?: Record<string, string | undefined>, _cacheTag?: string,
): Promise<Result<T>> {
  void _cacheTag;
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(searchParams ?? {})) {
    if (value !== undefined) query.set(key, value);
  }
  return tenantRequest<T>(`${path}${query.size ? `?${query}` : ""}`, {});
}

export async function serverSend<T>(
  _baseUrl: string, path: string, options: { method: "POST" | "PUT" | "PATCH" | "DELETE"; body?: unknown; timeoutMs?: number; idempotencyKey?: string; isEmptyResponse?: boolean; ifMatch?: string },
): Promise<Result<T>> {
  return tenantRequest<T>(path, options);
}
