import type { LumenConfig } from "./config.ts";
import { ServiceError } from "./service-error.ts";

const MAX_BODY_BYTES = 32768;

export function assertRequest(request: Request, config: Pick<LumenConfig, "appOrigin">, isMutation = false): void {
  if (request.headers.get("host")?.toLowerCase() !== new URL(config.appOrigin).host.toLowerCase()) throw new ServiceError(403, "invalid_host", "설정된 서비스 주소에서 다시 열어 주세요.");
  if (isMutation && request.headers.get("origin") !== config.appOrigin) throw new ServiceError(403, "invalid_origin", "서비스 화면에서 다시 시도하세요.");
}

export async function readJson(request: Request): Promise<unknown> {
  if (request.headers.get("content-type")?.split(";")[0].trim().toLowerCase() !== "application/json") throw new ServiceError(400, "invalid_content_type", "JSON 형식으로 요청하세요.");
  const reader = request.body?.getReader();
  if (!reader) throw new ServiceError(400, "invalid_json", "요청 내용을 확인하세요.");
  const chunks: Uint8Array[] = [];
  let size = 0;
  for (;;) {
    const item = await reader.read();
    if (item.done) break;
    size += item.value.length;
    if (size > MAX_BODY_BYTES) {
      await reader.cancel();
      throw new ServiceError(413, "request_too_large", "요청 내용이 너무 큽니다.");
    }
    chunks.push(item.value);
  }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
  try { return JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes)); }
  catch { throw new ServiceError(400, "invalid_json", "요청 내용의 JSON 형식을 확인하세요."); }
}

