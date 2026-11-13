export const LOCAL_HOSTS: ReadonlySet<string> = new Set(["localhost", "127.0.0.1", "[::1]"]);
export const DEFAULT_BASE_URL = "http://127.0.0.1:38147";
// 초기화는 어느 주소에서나 테스트 환경에만 넣는다. 실제 환경(live)을 고르는 입력은 없다.
export const SEED_ENVIRONMENT = "test";

export type TargetInput = {
  baseUrlOption?: string;
  baseUrlEnv?: string;
  confirmHost?: string;
  environmentEnv?: string;
};

// 주소가 로컬로 보여도 운영 서버 안이나 SSH 터널일 수 있다. 로컬 주소가 건너뛰는 검사는 https와 --confirm-host뿐이다.
export function checkTarget(input: TargetInput): { url: URL; isLocal: boolean } {
  if (input.environmentEnv !== undefined && input.environmentEnv !== "" && input.environmentEnv !== SEED_ENVIRONMENT) {
    throw new Error("초기화는 테스트 환경에만 넣습니다. METERENGINE_ENVIRONMENT를 지우거나 test로 두고 다시 실행하세요");
  }
  const raw = input.baseUrlOption ?? input.baseUrlEnv ?? DEFAULT_BASE_URL;
  let url: URL;
  try { url = new URL(raw); } catch { throw new Error("API 주소 형식이 올바르지 않습니다"); }
  // 내용 없는 "?"와 "#"는 url.search와 url.hash가 비어 있어 원문에서 찾는다.
  if (!["http:", "https:"].includes(url.protocol) || url.username || url.password || /[?#]/.test(raw) || url.pathname !== "/") {
    throw new Error("API 주소는 경로, 계정, 쿼리 없이 http(s)://호스트[:포트] 형식이어야 합니다");
  }
  if (input.confirmHost !== undefined && input.confirmHost !== url.hostname) throw new Error("--confirm-host가 대상 호스트 이름과 다릅니다");
  if (LOCAL_HOSTS.has(url.hostname)) return { url, isLocal: true };
  if (input.baseUrlOption === undefined) throw new Error("로컬이 아닌 서버는 환경 변수만으로 실행하지 않습니다. --base-url과 --confirm-host 인자를 주세요");
  if (input.confirmHost === undefined) throw new Error("로컬이 아닌 서버는 --confirm-host <호스트 이름>을 함께 줘야 합니다");
  if (url.protocol !== "https:") throw new Error("로컬이 아닌 서버는 https만 받습니다");
  return { url, isLocal: false };
}
