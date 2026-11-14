import type { ApiErrorBody, GenerationView } from "@/lib/contracts";

export class ApiFailure extends Error {
  status: number;
  code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export async function readApi<T>(url: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(url, { ...options, credentials: "same-origin", cache: "no-store", headers: { Accept: "application/json", ...options.headers } });
  const body: unknown = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) {
    const error = body && typeof body === "object" && "error" in body ? (body as ApiErrorBody).error : null;
    throw new ApiFailure(response.status, typeof error?.code === "string" ? error.code : "request_failed", typeof error?.message === "string" ? error.message : "요청을 마치지 못했어요. 잠시 뒤 다시 시도해 주세요.");
  }
  return body as T;
}

export function jsonRequest(body: unknown): RequestInit {
  return { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) };
}

export function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiFailure ? error.message : fallback;
}

function wait(milliseconds: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    const onAbort = () => { clearTimeout(timer); reject(new Error("aborted")); };
    const timer = setTimeout(() => { signal?.removeEventListener("abort", onAbort); resolve(); }, milliseconds);
    signal?.addEventListener("abort", onAbort, { once: true });
  });
}

// 같은 요청이 다른 탭에서 처리 중이면 202가 오므로 끝날 때까지 다시 읽는다.
export async function settleGeneration(first: GenerationView, signal?: AbortSignal): Promise<GenerationView> {
  let result = first;
  for (let attempt = 0; result.state === "processing" && attempt < 30; attempt++) {
    await wait(1000, signal);
    result = await readApi<GenerationView>(`/api/requests/${encodeURIComponent(first.id)}`, { signal });
  }
  if (result.state === "processing") throw new Error("processing");
  return result;
}
