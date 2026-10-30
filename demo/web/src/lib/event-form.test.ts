import assert from "node:assert/strict";
import { test } from "node:test";

import { formatKstTimestamp, parseEventProperties } from "./event-form.ts";

test("시각을 +09:00 RFC 3339 초 단위로 쓴다", () => {
  assert.equal(formatKstTimestamp(new Date("2026-10-26T05:03:04.987Z")), "2026-10-26T14:03:04+09:00");
  assert.equal(formatKstTimestamp(new Date("2026-10-26T15:00:00Z")), "2026-10-27T00:00:00+09:00");
});

test("properties는 JSON 객체만 받는다", () => {
  assert.deepEqual(parseEventProperties(' { "model": "opus", "tokens": 3 } '), {
    properties: { model: "opus", tokens: 3 },
  });
  assert.deepEqual(parseEventProperties("{}"), { properties: {} });
  for (const text of ["", "[]", "null", "3", '"a"', "{", "{'a': 1}"]) {
    const result = parseEventProperties(text);
    assert.ok("message" in result, `${text}가 통과했다`);
  }
});
