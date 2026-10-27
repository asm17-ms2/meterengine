import { cp, mkdir, stat } from "node:fs/promises";
import { readFileSync, existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { spawn } from "node:child_process";

const here = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2).filter(value => value !== "--");
const options = {};
for (let index = 0; index < args.length; index += 2) {
  if (!["--config", "--port", "--origin", "--host"].includes(args[index]) || !args[index + 1]) {
    throw new Error("사용법: node start.mjs --config /절대경로/config.json [--port 3200 --origin http://localhost:3200 --host 127.0.0.1]");
  }
  options[args[index].slice(2)] = args[index + 1];
}
let settings = {};
try {
  if (options.config) settings = JSON.parse(readFileSync(path.resolve(options.config), "utf8"));
} catch {
  throw new Error("비공개 JSON 설정 파일을 읽을 수 없습니다");
}
if (typeof settings !== "object" || settings === null || Array.isArray(settings) || Object.values(settings).some(value => typeof value !== "string")) {
  throw new Error("설정 파일은 문자열 환경변수의 JSON 객체여야 합니다");
}
const env = { ...process.env, ...settings };
if (options.origin) env.LUMEN_APP_ORIGIN = options.origin;
env.PORT = options.port ?? settings.PORT ?? process.env.PORT ?? "3200";
env.HOSTNAME = options.host ?? settings.HOSTNAME ?? "127.0.0.1";
env.NEXT_TELEMETRY_DISABLED = "1";
if (!/^\d+$/.test(env.PORT) || Number(env.PORT) < 1 || Number(env.PORT) > 65535) throw new Error("포트가 올바르지 않습니다");

const exported = path.join(here, "llm-service", "server.js");
const sourceApp = path.resolve(here, "..");
const built = path.join(sourceApp, ".next", "standalone", "llm-service", "server.js");
const server = existsSync(exported) ? exported : built;
if (!existsSync(server)) throw new Error("먼저 LLM 서비스의 production build를 실행하세요");
if (server === built) {
  const destination = path.dirname(server);
  await mkdir(path.join(destination, ".next"), { recursive: true });
  await cp(path.join(sourceApp, ".next", "static"), path.join(destination, ".next", "static"), { recursive: true });
  if (await stat(path.join(sourceApp, "public")).then(() => true, () => false)) {
    await cp(path.join(sourceApp, "public"), path.join(destination, "public"), { recursive: true });
  }
}
const child = spawn(process.execPath, [server], { env, cwd: path.dirname(server), stdio: "inherit" });
for (const signal of ["SIGINT", "SIGTERM"]) process.on(signal, () => child.kill(signal));
child.on("error", () => { console.error("LLM 서버를 시작하지 못했습니다"); process.exitCode = 1; });
child.on("exit", (code, signal) => { process.exitCode = signal ? 1 : code ?? 0; });
