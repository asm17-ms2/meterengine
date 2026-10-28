import assert from "node:assert/strict";
import test from "node:test";
import { createRequestId } from "../src/lib/request-id.ts";

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

test("a request id is a version 4 UUID even without crypto.randomUUID", () => {
  const insecure = { getRandomValues: bytes => globalThis.crypto.getRandomValues(bytes) };
  const first = createRequestId(insecure);
  assert.match(first, UUID_V4);
  assert.notEqual(createRequestId(insecure), first);
  assert.match(createRequestId(), UUID_V4);
});
