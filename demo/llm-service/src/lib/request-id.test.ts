import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { createRequestId } from "./request-id.ts";

test("randomUUID가 없는 HTTP 원격 접속에서도 v4 형식의 요청 키를 만든다", () => {
  const id = createRequestId({ getRandomValues: globalThis.crypto.getRandomValues.bind(globalThis.crypto) });
  assert.match(id, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
});

test("메시지 요청 키는 crypto.randomUUID를 직접 부르지 않는다", () => {
  const source = readFileSync(new URL("../components/LumenApplication.tsx", import.meta.url), "utf8");
  assert.doesNotMatch(source, /crypto\.randomUUID\(/);
});
