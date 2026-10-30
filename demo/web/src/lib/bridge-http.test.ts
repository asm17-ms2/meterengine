import assert from "node:assert/strict";
import { createServer, type IncomingMessage, type Server } from "node:http";
import type { AddressInfo } from "node:net";
import { test } from "node:test";

import { requestBridge } from "./bridge-http.ts";

type Received = { method: string; url: string; headerNames: string[]; body: string };

type Reply = { status: number; body: string };

async function withServer(reply: Reply, run: (baseUrl: string, received: Received[]) => Promise<void>): Promise<void> {
  const received: Received[] = [];
  const server: Server = createServer((request: IncomingMessage, response) => {
    const chunks: Buffer[] = [];
    request.on("data", (chunk: Buffer) => chunks.push(chunk));
    request.on("end", () => {
      received.push({
        method: request.method ?? "",
        url: request.url ?? "",
        headerNames: Object.keys(request.headers).sort(),
        body: Buffer.concat(chunks).toString("utf8"),
      });
      response.writeHead(reply.status, { "Content-Type": "application/json" });
      response.end(reply.body);
    });
  });
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  const { port } = server.address() as AddressInfo;
  try {
    await run(`http://127.0.0.1:${port}`, received);
  } finally {
    server.closeAllConnections();
    await new Promise<void>((resolve) => server.close(() => resolve()));
  }
}

const BROWSER_HEADERS = ["origin", "sec-fetch-site", "sec-fetch-mode", "sec-fetch-dest"];

test("requestBridge는 Origin과 Sec-Fetch 헤더를 보내지 않는다", async () => {
  await withServer({ status: 200, body: "{}" }, async (baseUrl, received) => {
    await requestBridge(baseUrl, "GET", "/meterengine/health");
    await requestBridge(baseUrl, "PUT", "/meterengine/config", { owner: "a" });
    await requestBridge(baseUrl, "POST", "/meterengine/restart");
    assert.equal(received.length, 3);
    for (const { headerNames } of received) {
      for (const name of BROWSER_HEADERS) {
        assert.ok(!headerNames.includes(name), `${name} 헤더가 있다`);
      }
    }
    assert.deepEqual(received[0].headerNames, ["connection", "host"]);
    assert.deepEqual(received[1].headerNames, ["connection", "content-length", "content-type", "host"]);
    assert.deepEqual(received[2].headerNames, ["connection", "content-length", "host"]);
  });
});

test("PUT 본문을 JSON으로 보내고 응답 JSON을 돌려준다", async () => {
  const reply = { status: 400, body: JSON.stringify({ error: "timeout_seconds는 0보다 큰 수여야 합니다", field: "timeout_seconds" }) };
  await withServer(reply, async (baseUrl, received) => {
    const changes = { owner: "성종", allow: ["meterengine", "career"], timeout_seconds: 7.5 };
    const response = await requestBridge(baseUrl, "PUT", "/meterengine/config", changes);
    assert.deepEqual(response, { status: 400, body: JSON.parse(reply.body) });
    assert.equal(received[0].method, "PUT");
    assert.equal(received[0].url, "/meterengine/config");
    assert.deepEqual(JSON.parse(received[0].body), changes);
  });
});

test("JSON이 아닌 응답과 빈 응답도 status와 함께 돌려준다", async () => {
  await withServer({ status: 404, body: "not found" }, async (baseUrl) => {
    assert.deepEqual(await requestBridge(baseUrl, "GET", "/nothing"), { status: 404, body: "not found" });
  });
  await withServer({ status: 200, body: "" }, async (baseUrl) => {
    assert.deepEqual(await requestBridge(baseUrl, "POST", "/meterengine/session"), { status: 200, body: null });
  });
});

test("연결할 수 없으면 Error", async () => {
  const closed = createServer();
  await new Promise<void>((resolve) => closed.listen(0, "127.0.0.1", resolve));
  const { port } = closed.address() as AddressInfo;
  await new Promise<void>((resolve) => closed.close(() => resolve()));
  await assert.rejects(requestBridge(`http://127.0.0.1:${port}`, "GET", "/meterengine/health"), (error: unknown) => {
    return error instanceof Error && error.message.includes("ECONNREFUSED") && !error.message.includes(String(port));
  });
});

test("응답이 시간 안에 오지 않으면 Error", async () => {
  const silent = createServer(() => undefined);
  await new Promise<void>((resolve) => silent.listen(0, "127.0.0.1", resolve));
  const { port } = silent.address() as AddressInfo;
  try {
    await assert.rejects(requestBridge(`http://127.0.0.1:${port}`, "GET", "/meterengine/health", undefined, 100), Error);
  } finally {
    silent.closeAllConnections();
    await new Promise<void>((resolve) => silent.close(() => resolve()));
  }
});
