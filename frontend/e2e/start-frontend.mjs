import { cpSync, existsSync } from "node:fs";
import { createServer } from "node:net";

async function freePort() {
  const probe = createServer();
  await new Promise(resolve => probe.listen(0, "127.0.0.1", resolve));
  const { port } = probe.address();
  await new Promise(resolve => probe.close(resolve));
  return port;
}

cpSync(".next/static", ".next/standalone/.next/static", { recursive: true });
if (existsSync("public")) cpSync("public", ".next/standalone/public", { recursive: true });
process.env.HOSTNAME = "127.0.0.1";
if (process.argv.includes("--fixture")) {
  if (!process.env.E2E_FIXTURE_PORT) throw new Error("가짜 API가 포트를 알리지 않았습니다. 설정의 webServer에서 가짜 API를 FE보다 먼저 띄우세요.");
  process.env.METERENGINE_API_BASE_URL = `http://127.0.0.1:${process.env.E2E_FIXTURE_PORT}`;
  // standalone server.js는 PORT=0을 3000으로 바꾼다. 빈 포트를 받아 넘긴다
  process.env.PORT = String(await freePort());
}
await import("../.next/standalone/server.js");

const health = `http://127.0.0.1:${process.env.PORT}/health`;
while (!(await fetch(health).then(response => response.ok, () => false))) await new Promise(resolve => setTimeout(resolve, 100));
console.log(`frontend listening on ${process.env.PORT}`);
