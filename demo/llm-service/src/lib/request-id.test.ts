import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { test } from "node:test";
import { createRequestId } from "./request-id.ts";

test("randomUUID가 없는 HTTP 원격 접속에서도 v4 형식의 요청 키를 만든다", () => {
  const id = createRequestId({ getRandomValues: globalThis.crypto.getRandomValues.bind(globalThis.crypto) });
  assert.match(id, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
});

test("화면 코드는 요청 키를 만들 때 crypto.randomUUID를 직접 부르지 않는다", () => {
  const directory = new URL("../components/", import.meta.url);
  const files = readdirSync(directory).filter(name => name.endsWith(".tsx"));
  assert.ok(files.length > 0);
  for (const name of files) assert.doesNotMatch(readFileSync(new URL(name, directory), "utf8"), /crypto\.randomUUID\(/, name);
});
