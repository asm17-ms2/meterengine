import assert from "node:assert/strict";
import test from "node:test";
import { CAPTCHA_REQUEST_TIMEOUT_MS, captchaMessage, turnstileField } from "../src/lib/auth/captcha.ts";
import { isEmailVerificationToken } from "../src/lib/auth/email-verification.ts";

test("only backend-shaped email verification tokens reach the API", () => {
  assert.equal(isEmailVerificationToken(`me_verify_${"aZ09".repeat(8)}`), true);
  for (const raw of ["", "me_verify_short", `me_invite_${"a".repeat(32)}`, `me_verify_${"a".repeat(31)}!`, `me_verify_${"a".repeat(33)}`]) assert.equal(isEmailVerificationToken(raw), false);
});

test("the Turnstile token is sent only when the widget produced one", () => {
  const withToken = new FormData();
  withToken.set("turnstile_token", "XXXX.DUMMY.TOKEN.XXXX");
  assert.deepEqual(turnstileField(withToken), { turnstile_token: "XXXX.DUMMY.TOKEN.XXXX" });
  const empty = new FormData();
  empty.set("turnstile_token", "");
  assert.deepEqual(turnstileField(empty), {});
  assert.deepEqual(turnstileField(new FormData()), {});
});

test("captcha errors get screen messages and other errors fall through", () => {
  assert.match(captchaMessage({ status: 400, code: "captcha_failed", message: "" }), /다시 마친 뒤/);
  assert.match(captchaMessage({ status: 503, code: "captcha_unavailable", message: "" }), /잠시 뒤/);
  assert.equal(captchaMessage({ status: 400, code: "validation_error", message: "" }), undefined);
});

test("signup and resend wait longer than the backend's captcha check and the default 5 second limit", () => {
  // backend CaptchaConfig: 연결 1초 + 읽기 3초
  const backendCaptchaMs = 1_000 + 3_000;
  assert.ok(CAPTCHA_REQUEST_TIMEOUT_MS > 5_000);
  assert.ok(CAPTCHA_REQUEST_TIMEOUT_MS >= backendCaptchaMs * 3);
});
