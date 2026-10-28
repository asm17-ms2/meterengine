import assert from "node:assert/strict";
import test from "node:test";
import { InputFieldError, readBusinessRegistrationNumber, readWholeNumber, successMessage } from "../src/app/(console)/workspace-action-feedback.ts";

test("each workspace action that shows a toast says what it did", () => {
  const messages = ["createMember", "updateMemberRole", "updateMemberScopes", "revokeOrganizationInvitation", "deleteApiKey", "updateApiKeyScopes"].map(successMessage);
  for (const message of messages) assert.notEqual(message, "처리했습니다.");
  assert.equal(new Set(messages).size, messages.length);
  assert.equal(successMessage("unknownOperation"), "처리했습니다.");
});

test("a whole number outside its range names the field instead of blaming JSON", () => {
  const limit = { min: 1, max: 2147483647, message: "한도 범위" };
  for (const value of ["2147483648", "0", "1.5", "99999999999999999999", ""]) {
    assert.throws(() => readWholeNumber(value, "rate_limit_per_minute", limit), error => error instanceof InputFieldError && error.field === "rate_limit_per_minute" && error.message === "한도 범위");
  }
  assert.equal(readWholeNumber("1", "rate_limit_per_minute", limit), 1);
  assert.equal(readWholeNumber("2147483647", "rate_limit_per_minute", limit), 2147483647);
  assert.equal(readWholeNumber(String(Number.MAX_SAFE_INTEGER), "amount", { min: 0, max: Number.MAX_SAFE_INTEGER, message: "금액" }), Number.MAX_SAFE_INTEGER);
});

test("a business registration number keeps only its ten digits and names the field when it is not ten digits", () => {
  for (const value of ["123-45-67890", " 123 45 67890 ", "1234567890"]) assert.equal(readBusinessRegistrationNumber(value, "business_registration_number"), "1234567890");
  for (const value of ["12345", "123-45-6789a", "12345678901", ""]) {
    assert.throws(() => readBusinessRegistrationNumber(value, "business_registration_number"), error => error instanceof InputFieldError && error.field === "business_registration_number" && error.message === "사업자등록번호는 숫자 10자리로 입력하세요.");
  }
});
