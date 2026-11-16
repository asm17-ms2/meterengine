import { mkdtemp } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { API_KEY_ENV } from "./credentials.ts";
import type { CliDeps } from "./context.ts";
import { BASE_URL_ENV } from "./context.ts";

export const TEST_KEY = "me_test_wiCp766QYNqhyYm4Z7Xq9wYosNvQCeVC";
export const LIVE_KEY = "me_live_QouOFuCiJyAbC0VGK1ltUCkG4IZM2LtA";

export type Call = { url: string; method: string; headers: Headers };
export type Reply = Response | Error | ((url: string) => Response | Error);

export interface SetupOptions {
  env?: NodeJS.ProcessEnv;
  secret?: string;
  files?: Record<string, string>;
  now?: Date;
}

// 준비한 응답을 차례로 돌려주는 가짜 fetch로 CLI를 돌린다. 함수 응답은 요청 주소를 보고 고른다.
export async function setup(replies: Reply[], options: SetupOptions = {}) {
  const out: string[] = [];
  const err: string[] = [];
  const calls: Call[] = [];
  const deps: CliDeps = {
    env: { [BASE_URL_ENV]: "http://localhost:8080", ...(options.env ?? { [API_KEY_ENV]: TEST_KEY }) },
    configDir: join(await mkdtemp(join(tmpdir(), "meterengine-cli-")), "meterengine"),
    stdout: (text) => out.push(text),
    stderr: (text) => err.push(text),
    readSecret: async () => options.secret ?? TEST_KEY,
    readFile: async (path) => {
      const text = options.files?.[path];
      if (text === undefined) throw Object.assign(new Error(`ENOENT: ${path}`), { code: "ENOENT" });
      return text;
    },
    now: () => options.now ?? new Date("2026-11-14T03:00:00Z"),
    clientOptions: {
      maxRetries: 0,
      fetch: async (input, init = {}) => {
        const url = String(input);
        calls.push({ url, method: init.method ?? "GET", headers: new Headers(init.headers) });
        const next = replies.shift();
        if (next === undefined) throw new Error(`준비한 응답보다 호출이 많다: ${url}`);
        const reply = typeof next === "function" ? next(url) : next;
        if (reply instanceof Error) throw reply;
        return reply;
      },
    },
  };
  return { deps, out, err, calls, stdoutJson: () => JSON.parse(out.join("")) as unknown, stderrJson: () => JSON.parse(err.join("")) as unknown };
}

export function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}
