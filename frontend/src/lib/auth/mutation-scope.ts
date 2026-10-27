import "server-only";

import { headers } from "next/headers";
import { readEnvironment } from "@/lib/environment";

export async function mutationScopeError(data: FormData, organizationId?: string): Promise<string | null> {
  const formOrganization = data.get("organization_id");
  if (typeof formOrganization !== "string" || !formOrganization || formOrganization !== organizationId) return "열어 둔 화면의 도입사가 바뀌었습니다. 현재 도입사를 확인한 뒤 다시 시도하세요.";
  const environment = data.get("environment");
  if (typeof environment !== "string" || readEnvironment(environment) !== readEnvironment((await headers()).get("x-meterengine-environment"))) return "열어 둔 화면의 업무 환경이 바뀌었습니다. 환경을 확인한 뒤 다시 시도하세요.";
  return null;
}
