export function maskApiPath(path: string): string {
  return path.split("?")[0].split("/").map(segment => segment === "" || /^v\d+$/.test(segment) || /^[a-z-]+$/.test(segment) ? segment : ":id").join("/");
}

export function describeTransportFailure(cause: unknown, method: string, path: string, elapsedMs: number): { status: number; code: string; message: string } {
  const isTimedOut = (cause as { name?: unknown } | null)?.name === "TimeoutError";
  console.error(`[api] ${method} ${maskApiPath(path)} ${isTimedOut ? "timeout" : "network_error"} ${elapsedMs}ms`);
  if (!isTimedOut) return { status: 0, code: "network_error", message: "서버에 연결하지 못했습니다. 잠시 후 다시 시도하세요." };
  return {
    status: 0,
    code: "timeout",
    message: method === "GET" ? "응답이 늦어 정보를 불러오지 못했습니다. 잠시 후 다시 조회하세요." : "응답이 늦어 결과를 확인하지 못했습니다. 요청이 처리됐을 수 있으니 화면을 새로 조회해 결과를 확인한 뒤 진행하세요.",
  };
}
