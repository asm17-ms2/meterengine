import "server-only";

import { headers } from "next/headers";
import { isWorkspaceEnvironment } from "@/lib/environment";

export async function mutationScopeError(data: FormData, organizationId?: string, options: { requiresEnvironment?: boolean } = {}): Promise<string | null> {
  const formOrganization = data.get("organization_id");
  if (data.getAll("organization_id").length !== 1 || typeof formOrganization !== "string" || !formOrganization || formOrganization !== organizationId) return "열어 둔 화면의 도입사가 바뀌었습니다. 현재 도입사를 확인한 뒤 다시 시도하세요.";
  const requestHeaders = await headers();
  const requestOrganization = requestHeaders.get("x-meterengine-organization-id");
  if (requestOrganization && requestOrganization !== organizationId) return "열어 둔 화면의 도입사가 바뀌었습니다. 현재 도입사를 확인한 뒤 다시 시도하세요.";
  if (options.requiresEnvironment === false) return null;
  const environment = data.get("environment");
  const requestEnvironment = requestHeaders.get("x-meterengine-environment");
  if (data.getAll("environment").length !== 1 || !isWorkspaceEnvironment(environment) || !isWorkspaceEnvironment(requestEnvironment) || environment !== requestEnvironment) return "열어 둔 화면의 업무 환경이 바뀌었습니다. 환경을 확인한 뒤 다시 시도하세요.";
  return null;
}
