// 계약 E2E의 가짜 API와 FE는 커널이 준 빈 포트로 뜬다. 고정 포트는 CI 러너의 임시 포트 범위와 겹친다
// webServer.wait가 기동 로그에서 포트를 읽어 E2E_FIXTURE_PORT, E2E_FRONTEND_PORT 환경변수로 넘긴다

// tsconfig의 target이 ES2017이라 이름 붙은 그룹을 정규식 리터럴로 쓰지 못한다
const FIXTURE_READY = new RegExp("fixture listening on (?<e2e_fixture_port>\\d+)");
const FRONTEND_READY = new RegExp("frontend listening on (?<e2e_frontend_port>\\d+)");

export function contractWebServers(fixtureScript: string) {
  return [
    { command: `node e2e/${fixtureScript}`, wait: { stdout: FIXTURE_READY } },
    { command: "node e2e/start-frontend.mjs --fixture", wait: { stdout: FRONTEND_READY }, env: { NEXT_TELEMETRY_DISABLED: "1" }, timeout: 60_000 },
  ];
}

function announcedPort(name: "E2E_FIXTURE_PORT" | "E2E_FRONTEND_PORT"): string {
  // 목록 조회는 서버 없이 명세를 읽는다. 빈 값은 80번 포트로 읽히므로 어디에도 붙지 않는 0을 준다
  return process.env[name] ?? "0";
}

export const fixtureUrl = () => `http://127.0.0.1:${announcedPort("E2E_FIXTURE_PORT")}`;
export const frontendOrigin = () => `http://localhost:${announcedPort("E2E_FRONTEND_PORT")}`;
