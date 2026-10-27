export type BridgeResult = { ok: true; status: number; body: unknown } | { ok: false; message: string };

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

export function bridgeErrorText(result: BridgeResult): string {
  if (!result.ok) {
    return result.message;
  }
  const detail = isRecord(result.body) && typeof result.body.error === "string" ? `: ${result.body.error}` : "";
  return `브리지 응답 ${result.status}${detail}`;
}

function countsText(counts: unknown): string {
  if (!isRecord(counts)) {
    return "";
  }
  const parts = Object.entries(counts).map(([outcome, count]) => `${outcome} ${String(count)}`);
  return parts.length === 0 ? "" : `, 보낸 결과 ${parts.join(" ")}`;
}

function readinessText(body: Record<string, unknown>): string {
  if (body.organization_error !== undefined && body.organization_error !== null && body.organization_error !== "") {
    return `전송 중단 (도입사 확인 실패: ${String(body.organization_error)}). API 키와 도입사 상태, 전송 대상 서버 연결을 확인하세요`;
  }
  if (body.status !== "ok" || body.ready === false || body.forwarding === false || body.error) {
    return `전송 준비 안 됨 (상태: ${typeof body.status === "string" ? body.status : "알 수 없음"}). 브리지 상태와 설정을 확인하세요`;
  }
  if ("org_id" in body) {
    return "API 키 전송 준비 미확인 (기존 org_id 방식). API 키 방식 브리지 연동이 필요합니다";
  }
  if (
    !isRecord(body.organization) ||
    typeof body.organization.id !== "string" || body.organization.id.trim() === "" ||
    typeof body.organization.name !== "string" || body.organization.name.trim() === "" ||
    body.organization_error !== null
  ) {
    return "전송 준비 미확인 (도입사 확인 전). 브리지에 이벤트가 들어온 뒤 상태를 다시 확인하세요";
  }
  return `전송 준비됨, 도입사 ${body.organization.name}`;
}

export function describeBridgeHealth(result: BridgeResult): string {
  if (!result.ok) {
    return `브리지: 연결 확인 실패 (${bridgeErrorText(result)}). 브리지 실행 여부와 주소를 확인하세요`;
  }
  if (result.status !== 200 || !isRecord(result.body)) {
    return `브리지: 연결됨, 상태 확인 실패 (${bridgeErrorText(result)}). health 응답과 브리지 설정을 확인하세요`;
  }
  const { owner, base_url: baseUrl, counts } = result.body;
  const ownerText = typeof owner === "string" && owner !== "" ? owner : "없음";
  const baseUrlText = typeof baseUrl === "string" ? baseUrl : "알 수 없음";
  return `브리지: 연결됨, ${readinessText(result.body)}, 주인 ${ownerText}, 전송 대상 ${baseUrlText}${countsText(counts)}`;
}
