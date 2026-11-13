// 가상 회사 한 곳의 이름, 모델, 용도, 미터를 모은다. 앱과 초기화 스크립트가 같이 읽는다.
// ID, 코드, 속성 이름을 바꾸면 이미 수집한 이벤트와 등록한 단가가 맞지 않게 된다.

export type SurfaceId = "chat" | "api" | "batch";
export type ModelKind = "text" | "embedding";
export type MetricKey = "input" | "output" | "cacheRead" | "cacheWrite";

export const COMPANY = {
  id: "openab",
  name: "OpenAB",
  emailDomain: "openab.example",
} as const;

export const EVENT_TYPE = "llm_request";
export const TRANSACTION_ID_PREFIX = "req_";
export const DIMENSION_PROPERTIES = ["model", "surface"] as const;
export const TOKEN_UNIT_SIZE = 1_000_000;

// defaultModelId는 그 화면을 처음 열 때 골라 두는 모델이다.
export const SURFACES: ReadonlyArray<{ id: SurfaceId; name: string; defaultModelId: string }> = [
  { id: "chat", name: "대화", defaultModelId: "ab-5" },
  { id: "api", name: "API", defaultModelId: "ab-5" },
  { id: "batch", name: "일괄 처리", defaultModelId: "ab-5-nano" },
];

// 로컬 모형이 답을 만드는 방식. 임베딩 모델은 글 대신 벡터를 돌려주므로 "none"이다.
export type ReplyStyle = "detailed" | "brief" | "label" | "none";
// 응답 시간 모형(ms). 기본 시간에 출력 토큰마다 perOutputToken을 더한다.
export type LatencyProfile = { base: number; perOutputToken: number };

export const MODELS: ReadonlyArray<{ id: string; name: string; kind: ModelKind; surfaces: readonly SurfaceId[]; description: string; reply: ReplyStyle; latency: LatencyProfile }> = [
  { id: "ab-5", name: "AB-5", kind: "text", surfaces: ["chat", "api", "batch"], description: "복잡한 추론과 긴 문서 작업", reply: "detailed", latency: { base: 620, perOutputToken: 18 } },
  { id: "ab-5-mini", name: "AB-5 mini", kind: "text", surfaces: ["chat", "api", "batch"], description: "빠른 응답이 필요한 일상 업무", reply: "brief", latency: { base: 310, perOutputToken: 7 } },
  { id: "ab-5-nano", name: "AB-5 nano", kind: "text", surfaces: ["api", "batch"], description: "분류와 추출 같은 대량 처리", reply: "label", latency: { base: 140, perOutputToken: 3 } },
  { id: "ab-embed-3", name: "AB Embed 3", kind: "embedding", surfaces: ["api", "batch"], description: "검색과 추천에 쓰는 임베딩", reply: "none", latency: { base: 45, perOutputToken: 0 } },
];

export function defaultModelId(surface: SurfaceId): string {
  return SURFACES.find(item => item.id === surface)!.defaultModelId;
}

export const METRICS: ReadonlyArray<{ key: MetricKey; code: string; name: string; property: string }> = [
  { key: "input", code: "llm-input-tokens", name: "입력 토큰", property: "input_tokens" },
  { key: "output", code: "llm-output-tokens", name: "출력 토큰", property: "output_tokens" },
  { key: "cacheRead", code: "llm-cache-read-tokens", name: "캐시 읽기 토큰", property: "cache_read_tokens" },
  { key: "cacheWrite", code: "llm-cache-write-tokens", name: "캐시 쓰기 토큰", property: "cache_write_tokens" },
];

export function transactionId(requestId: string): string {
  return TRANSACTION_ID_PREFIX + requestId.replaceAll("-", "").toLowerCase();
}
