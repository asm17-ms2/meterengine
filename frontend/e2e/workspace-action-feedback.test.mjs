import assert from "node:assert/strict";
import test from "node:test";
import { InputFieldError, readWholeNumber, successMessage, unconfirmedMessage } from "../src/app/(console)/workspace-action-feedback.ts";
import { operationResultCopy } from "../src/lib/workspace-operation-results.ts";

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

test("an unconfirmed Toss key save points at the saved time, not at payment history", () => {
  const message = unconfirmedMessage("updateTossPaymentsCredential");
  assert.match(message, /토스/);
  assert.match(message, /저장 시각/);
  assert.doesNotMatch(message, /결제와 취소 내역/);
  assert.equal(operationResultCopy("updateTossPaymentsCredential").refreshLabel, "토스 키 상태 새로 조회");
  for (const operation of ["createPaymentAttempt", "createPaymentCancellation"]) {
    assert.equal(unconfirmedMessage(operation), "처리 결과를 확인하지 못했습니다. 중복 요청을 피하려면 결제와 취소 내역을 먼저 새로 조회하세요.");
    assert.equal(operationResultCopy(operation).refreshLabel, "결제와 취소 내역 새로 조회");
  }
});
