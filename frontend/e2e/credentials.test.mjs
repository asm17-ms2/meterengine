import assert from "node:assert/strict";
import test from "node:test";
import { credentialFieldErrors, isValidEmail, isValidPassword, PASSWORD_RULE_MESSAGE } from "../src/lib/auth/credentials.ts";

test("passwords are 8 characters or more and at most 72 UTF-8 bytes", () => {
  assert.equal(isValidPassword("a".repeat(7)), false);
  assert.equal(isValidPassword("a".repeat(8)), true);
  assert.equal(isValidPassword("a".repeat(72)), true);
  assert.equal(isValidPassword("a".repeat(73)), false);
  assert.equal(isValidPassword("가".repeat(24)), true);
  assert.equal(isValidPassword("가".repeat(25)), false);
});

test("emails need one at sign, a dot after it, no spaces, and at most 254 characters", () => {
  assert.equal(isValidEmail("member@example.com"), true);
  assert.equal(isValidEmail("member @example.com"), false);
  assert.equal(isValidEmail(`${"a".repeat(243)}@example.com`), false);
});

test("credential field errors name each failing field with the screen message", () => {
  assert.deepEqual(credentialFieldErrors({ name: "", email: "x", password: "short" }), { name: "이름을 입력하세요.", email: "올바른 이메일 주소를 입력하세요.", password: PASSWORD_RULE_MESSAGE });
  assert.deepEqual(credentialFieldErrors({ name: "가".repeat(256), email: "member@example.com", password: "long-enough" }), { name: "이름은 255자 이내로 입력하세요." });
  assert.deepEqual(credentialFieldErrors({ password: "long-enough" }), {});
});
