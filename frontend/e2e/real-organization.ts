import { execFileSync } from "node:child_process";

const DB_CONTAINER = process.env.E2E_DB_CONTAINER ?? "meterengine-0696-e2e";
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

// 가입한 조직은 실제 환경이 잠겨 있어 환경 헤더 없이 부르는 준비 요청이 403으로 막힌다.
// 잠금이 아닌 기능을 보는 실제 스택 명세는 테스트 DB에서 실제 환경을 열고 시작한다.
export function openLiveEnvironment(organizationId: string) {
  if (!UUID_PATTERN.test(organizationId)) throw new Error("조직 ID를 확인하세요.");
  const output = execFileSync("docker", ["exec", "-i", DB_CONTAINER, "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", "meterengine", "-d", "meterengine", "-At"], {
    input: `UPDATE organization SET live_environment_opened_at = coalesce(live_environment_opened_at, now()) WHERE id = '${organizationId}' RETURNING id;`,
    encoding: "utf8", stdio: ["pipe", "pipe", "pipe"],
  });
  // 다른 DB를 가리키면 한 줄도 바뀌지 않는다. 뒤에서 403으로 엉뚱하게 실패하기 전에 여기서 알린다.
  if (!output.includes(organizationId.toLowerCase())) throw new Error(`${DB_CONTAINER}에 조직 ${organizationId}가 없어요. E2E_DB_CONTAINER를 확인하세요.`);
}
