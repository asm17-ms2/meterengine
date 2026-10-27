import "server-only";


export async function mutationScopeError(data: FormData, organizationId?: string): Promise<string | null> {
  const formOrganization = data.get("organization_id");
  if (typeof formOrganization !== "string" || !formOrganization || formOrganization !== organizationId) return "열어 둔 화면의 도입사가 바뀌었습니다. 현재 도입사를 확인한 뒤 다시 시도하세요.";
  return null;
}
