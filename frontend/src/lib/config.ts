import "server-only";

/**
 * 서버 사이드 설정. 값 목록과 설명은 .env.example에 있다.
 *
 * 브라우저는 백엔드를 직접 호출하지 않는다. 모든 백엔드 호출은 Server Component에서
 * 서버 사이드 fetch로 나가므로 CORS 자체가 없고, 조직 식별자가 클라이언트에 가지 않는다.
 * 'server-only' 임포트가 이 파일을 클라이언트 컴포넌트에서 쓰면 빌드를 깨뜨린다.
 */
export const config = {
  apiBaseUrl: process.env.METERENGINE_API_BASE_URL ?? "http://localhost:8080",
  organizationId:
    process.env.METERENGINE_ORGANIZATION_ID ??
    "d7cee55d-8c82-4afc-b996-6749d8b26a4e",
  organizationName: process.env.METERENGINE_ORGANIZATION_NAME ?? "데모 도입사",
} as const;
