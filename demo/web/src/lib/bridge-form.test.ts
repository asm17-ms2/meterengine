import assert from "node:assert/strict";
import { test } from "node:test";

import { toBridgeChanges, toBridgeFields } from "./bridge-form.ts";

const CONFIG = {
  owner: "성종",
  base_url: "http://localhost:8080",
  allow: ["meterengine", "career"],
  deny: [],
  fallback_project: "기타 프로젝트",
  timeout_seconds: 10,
};

function readerOf(values: Record<string, string>): (name: string) => string {
  return (name) => values[name] ?? "";
}

test("응답의 값 타입으로 입력 종류를 고르고 순서를 지킨다", () => {
  assert.deepEqual(toBridgeFields(CONFIG), [
    { name: "owner", kind: "text", value: "성종" },
    { name: "base_url", kind: "text", value: "http://localhost:8080" },
    { name: "allow", kind: "list", value: "meterengine, career" },
    { name: "deny", kind: "list", value: "" },
    { name: "fallback_project", kind: "text", value: "기타 프로젝트" },
    { name: "timeout_seconds", kind: "number", value: "10" },
  ]);
});

test("api_key는 비밀번호 입력이고 가린 값을 값으로 쓰지 않는다", () => {
  const fields = toBridgeFields({ owner: "", api_key: "me_test_****2345" });
  assert.deepEqual(fields, [
    { name: "owner", kind: "text", value: "" },
    { name: "api_key", kind: "secret", value: "", placeholder: "me_test_****2345" },
  ]);
});

test("모르는 타입의 값은 그리지 않는다", () => {
  assert.deepEqual(toBridgeFields({ owner: "a", nested: { a: 1 }, enabled: true, empty: null, mixed: ["a", 1] }), [
    { name: "owner", kind: "text", value: "a" },
  ]);
});

test("폼 값을 브리지가 받는 타입으로 바꾼다", () => {
  const fields = toBridgeFields({ ...CONFIG, api_key: "me_test_****2345" });
  const result = toBridgeChanges(
    fields,
    readerOf({
      owner: " 성종 ",
      base_url: "http://localhost:8081",
      allow: " meterengine, ,career,, demo ",
      deny: "",
      fallback_project: "기타",
      timeout_seconds: " 7.5 ",
      api_key: "",
    }),
  );
  assert.deepEqual(result, {
    changes: {
      owner: "성종",
      base_url: "http://localhost:8081",
      allow: ["meterengine", "career", "demo"],
      deny: [],
      fallback_project: "기타",
      timeout_seconds: 7.5,
      api_key: "",
    },
  });
});

test("숫자가 아닌 timeout_seconds는 브리지에 보내지 않고 필드를 알려준다", () => {
  const fields = toBridgeFields(CONFIG);
  for (const text of ["", "abc", "10s", "Infinity"]) {
    const result = toBridgeChanges(fields, readerOf({ ...CONFIG, allow: "", deny: "", timeout_seconds: text }));
    assert.ok("field" in result, `${text}가 통과했다`);
    assert.equal(result.field, "timeout_seconds");
  }
});
