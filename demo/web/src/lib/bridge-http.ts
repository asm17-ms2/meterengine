import { request, type OutgoingHttpHeaders } from "node:http";

export type BridgeMethod = "GET" | "PUT" | "POST";

export type BridgeResponse = { status: number; body: unknown };

export const DEFAULT_BRIDGE_TIMEOUT_MS = 3000;

function parseBody(text: string): unknown {
  if (text === "") {
    return null;
  }
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

function connectionMessage(error: Error): string {
  const { code } = error as NodeJS.ErrnoException;
  return code === undefined ? "브리지에 연결하지 못했습니다" : `브리지에 연결하지 못했습니다 (${code})`;
}

function bridgeEndpoint(baseUrl: string, path: string): URL {
  let url: URL;
  try {
    url = new URL(path, baseUrl);
  } catch {
    throw new Error("브리지 주소를 해석하지 못했습니다");
  }
  if (url.protocol !== "http:") {
    throw new Error("브리지 주소는 http://로 시작해야 합니다");
  }
  return url;
}

export function requestBridge(
  baseUrl: string,
  method: BridgeMethod,
  path: string,
  body?: unknown,
  timeoutMs: number = DEFAULT_BRIDGE_TIMEOUT_MS,
): Promise<BridgeResponse> {
  return new Promise((resolve, reject) => {
    const url = bridgeEndpoint(baseUrl, path);
    const payload = body === undefined ? undefined : JSON.stringify(body);
    const headers: OutgoingHttpHeaders = {};
    if (payload !== undefined) {
      headers["Content-Type"] = "application/json";
      headers["Content-Length"] = Buffer.byteLength(payload);
    }
    let isTimedOut = false;
    const outgoing = request(url, { method, headers, timeout: timeoutMs }, (incoming) => {
      const chunks: Buffer[] = [];
      incoming.on("data", (chunk: Buffer) => chunks.push(chunk));
      incoming.on("error", (error) => reject(new Error("브리지 응답을 끝까지 읽지 못했습니다", { cause: error })));
      incoming.on("end", () => {
        resolve({ status: incoming.statusCode ?? 0, body: parseBody(Buffer.concat(chunks).toString("utf8")) });
      });
    });
    outgoing.on("timeout", () => {
      isTimedOut = true;
      outgoing.destroy();
    });
    outgoing.on("error", (error) => {
      const message = isTimedOut ? `브리지가 ${timeoutMs}ms 안에 응답하지 않았습니다` : connectionMessage(error);
      reject(new Error(message, { cause: error }));
    });
    outgoing.end(payload);
  });
}
