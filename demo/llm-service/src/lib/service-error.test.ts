import assert from "node:assert/strict";
import { test } from "node:test";
import { runInNewContext } from "node:vm";
import { isServiceError, ServiceError } from "./service-error.ts";

const BRAND = Symbol.for("meterengine.lumen.service-error.v1");

test("내부 오류는 라우트 번들에서 생성자와 realm이 달라도 식별한다", () => {
  const error = new ServiceError(401, "session_required", "로그인하세요.");
  assert.equal(isServiceError(error), true);
  const foreign = runInNewContext(`
    class ServiceError extends Error {
      constructor() {
        super("새 요청으로 시작하세요.");
        this[Symbol.for("meterengine.lumen.service-error.v1")] = true;
        this.status = 409;
        this.code = "request_conflict";
        this.isRetryable = false;
      }
    }
    new ServiceError();
  `);
  assert.equal(foreign instanceof Error, false);
  assert.equal(foreign instanceof ServiceError, false);
  assert.equal(isServiceError(foreign), true);
  if (isServiceError(foreign)) {
    assert.equal(foreign.status, 409);
    assert.equal(foreign.code, "request_conflict");
    assert.equal(foreign.message, "새 요청으로 시작하세요.");
    assert.equal(foreign.isRetryable, false);
  }
});

test("외부 오류와 원시 JSON은 모양이 같아도 내부 오류로 인정하지 않는다", () => {
  const external = { status: 403, code: "upstream_error", message: "private upstream credential", isRetryable: false };
  assert.equal(isServiceError(external), false);
  assert.equal(isServiceError(Object.assign(new Error(external.message), external)), false);
  assert.equal(isServiceError(JSON.parse(JSON.stringify(new ServiceError(403, "invalid_origin", external.message)))), false);
  assert.equal(isServiceError({ ...external, [Symbol("meterengine.lumen.service-error.v1")]: true }), false);
});

test("내부 표식이 있어도 허용되지 않는 오류 값은 거절한다", () => {
  const valid = { [BRAND]: true, status: 403, code: "invalid_origin", message: "서비스 화면에서 다시 시도하세요.", isRetryable: false };
  for (const invalid of [
    { status: 399 }, { status: 600 }, { status: 401.5 }, { status: "401" },
    { code: "UPSTREAM_ERROR" }, { code: "bad-code" }, { code: "" }, { code: "x".repeat(65) },
    { message: "" }, { message: " " }, { message: "x".repeat(1001) }, { isRetryable: "false" }, { [BRAND]: false },
  ]) assert.equal(isServiceError({ ...valid, ...invalid }), false);
  const accessor = { ...valid };
  Object.defineProperty(accessor, "message", { get() { throw new Error("unexpected accessor"); } });
  assert.equal(isServiceError(accessor), false);
  assert.equal(isServiceError(Object.create(valid)), false);
  assert.equal(isServiceError(null), false);
  assert.equal(isServiceError("error"), false);
});
