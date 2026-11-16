import type { Command } from "commander";
import type { CliDeps } from "../context.ts";
import { BASE_URL_ENV, clientFor, printJson } from "../context.ts";
import { UsageError } from "../exit-code.ts";

// fetch가 . 과 .. 를 정리하면 /v1/ 밖으로 나갈 수 있다. %2e도 같은 뜻으로 읽는다.
const DOT_SEGMENT = /^(\.|%2e){1,2}$/i;

function decodedSegment(segment: string): string {
  try {
    return decodeURIComponent(segment);
  } catch {
    return segment;
  }
}

function collect(value: string, previous: string[] | undefined): string[] {
  return [...(previous ?? []), value];
}

// 앞의 /는 없어도 받고 /v1/은 꼭 있어야 한다. 쿼리는 경로의 ? 뒤 그대로에 --query를 인코딩해 덧붙인다.
export function apiPathOf(input: string, queries: string[]): string {
  if (/^[a-z][a-z0-9+.-]*:|^\/\//i.test(input)) throw new UsageError("invalid_path", "주소 전체가 아니라 /v1/로 시작하는 경로만 씁니다. 서버 주소는 METERENGINE_API_BASE_URL로 정합니다");
  // URL 파서는 탭과 줄바꿈을 지우고 역슬래시를 /로 읽어 아래 구간 검사를 비켜 간다.
  if (/[\x00-\x1f\x7f\\#]/.test(input)) throw new UsageError("invalid_path", "경로에 제어 문자(탭, 줄바꿈 등), 역슬래시, #는 쓸 수 없습니다");
  const path = input.startsWith("/") ? input : `/${input}`;
  const queryStart = path.indexOf("?");
  const pathname = queryStart === -1 ? path : path.slice(0, queryStart);
  if (!pathname.startsWith("/v1/")) throw new UsageError("invalid_path", `경로는 /v1/로 시작합니다. 예: /v1${pathname}`);
  const segments = pathname.split("/");
  if (segments.some((segment) => DOT_SEGMENT.test(segment))) throw new UsageError("invalid_path", "경로에 . 이나 .. 구간은 쓸 수 없습니다");
  if (!new URL(pathname, "http://localhost").pathname.startsWith("/v1/")) throw new UsageError("invalid_path", "경로가 /v1/ 밖을 가리킵니다");
  // 내보내기는 CSV만 내서 Accept: application/json이면 서버가 {id} 같은 다른 경로로 읽는다.
  if (decodedSegment(segments.at(-1) ?? "") === "export") {
    throw new UsageError("unsupported_path", "CSV 내보내기(/export)는 JSON이 아니라 api get으로 받을 수 없습니다. 같은 데이터는 /export를 뺀 목록 경로로 조회합니다");
  }

  const extra = new URLSearchParams();
  for (const query of queries) {
    const separator = query.indexOf("=");
    if (separator <= 0) throw new UsageError("invalid_query", `--query는 key=value 형식입니다: ${query}`);
    extra.append(query.slice(0, separator), query.slice(separator + 1));
  }
  if (extra.size === 0) return path;
  const joiner = queryStart === -1 ? "?" : path.endsWith("?") || path.endsWith("&") ? "" : "&";
  return `${path}${joiner}${extra}`;
}

export function registerApi(program: Command, deps: CliDeps): void {
  const api = program.command("api").description("전용 명령이 없는 API를 직접 부른다. 조회(GET)만 된다");
  api
    .command("get")
    .description("GET 요청을 보내고 응답 JSON을 그대로 낸다 (GET /v1/...)")
    .argument("<path>", "/v1/로 시작하는 경로. 쿼리는 ?를 붙여 써도 되고 셸에서는 따옴표로 감싼다. 예: '/v1/usage?month=2026-11'")
    .option("--query <key=value>", "쿼리 하나를 인코딩해 덧붙인다. 여러 번 쓸 수 있다", collect)
    .action(async (input: string, options: { query?: string[] }, command: Command) => {
      const path = apiPathOf(input, options.query ?? []);
      const client = await clientFor(deps, command);
      let body: unknown;
      try {
        body = await client.get(path);
      } catch (error) {
        // 성공 응답이 JSON이 아니면 SDK가 JSON.parse에서 SyntaxError를 낸다. /export는 위에서 막았으니 주소가 틀린 경우가 대부분이다.
        if (error instanceof SyntaxError) throw new Error(`서버 응답이 JSON이 아닙니다. ${BASE_URL_ENV}가 MeterEngine API 주소를 가리키는지 확인하세요`);
        throw error;
      }
      printJson(deps.stdout, body);
    });
}
