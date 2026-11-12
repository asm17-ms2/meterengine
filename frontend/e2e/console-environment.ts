import { DEFAULT_ENVIRONMENT } from "../src/lib/environment";

// 콘솔은 주소에 환경이 없으면 기본 환경을 열고, 백엔드는 헤더가 없으면 legacy로 본다. 세션으로 만든 데이터를 화면과 같은 환경에 둔다.
export function consoleHeaders(token: string): Record<string, string> {
  return { Authorization: `Bearer ${token}`, "X-MeterEngine-Environment": DEFAULT_ENVIRONMENT };
}
