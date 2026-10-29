import assert from "node:assert/strict";
import test from "node:test";
import { InputFieldError, errorMessageForCode, existingCustomerMessage, readBusinessRegistrationNumber, readWholeNumber, staleCancellationMessage, successMessage, unconfirmedMessage } from "../src/app/(console)/workspace-action-feedback.ts";
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

test("a business registration number keeps only its ten digits and names the field when it is not ten digits", () => {
  for (const value of ["123-45-67890", " 123 45 67890 ", "1234567890"]) assert.equal(readBusinessRegistrationNumber(value, "business_registration_number"), "1234567890");
  for (const value of ["12345", "123-45-6789a", "12345678901", ""]) {
    assert.throws(() => readBusinessRegistrationNumber(value, "business_registration_number"), error => error instanceof InputFieldError && error.field === "business_registration_number" && error.message === "사업자등록번호는 숫자 10자리로 입력하세요.");
  }
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

test("a cancellation rejected because another cancellation came first asks to refresh instead of retrying", () => {
  for (const code of ["payment_attempt_fully_canceled", "payment_cancellation_amount_exceeded"]) {
    assert.match(staleCancellationMessage("createPaymentCancellation", code), /화면의 잔액이 최신이 아닙니다/);
  }
  assert.equal(staleCancellationMessage("createPaymentCancellation", "payment_cancellation_idempotency_conflict"), unconfirmedMessage("createPaymentCancellation"));
  assert.equal(staleCancellationMessage("createPaymentAttempt", "payment_attempt_fully_canceled"), undefined);
  assert.equal(staleCancellationMessage("createPaymentCancellation", "validation_failed"), undefined);
  assert.equal(staleCancellationMessage("createPaymentCancellation", undefined), undefined);
});

test("a missing Toss key says where to save it", () => {
  const message = errorMessageForCode("toss_payments_credential_missing");
  assert.match(message, /도입사 설정/);
  assert.match(message, /토스페이먼츠 연동/);
  assert.equal(errorMessageForCode("unknown_code"), undefined);
});

test("re-registering an existing external customer id is reported as not created", () => {
  const input = { externalId: "customer-1", name: "새 고객", billingEmail: "new@example.com" };
  assert.equal(existingCustomerMessage(input, { name: "기존 고객", billing_email: "old@example.com" }), '외부 고객 ID가 같은 기존 고객 "기존 고객"이 있어 새로 등록하지 않았습니다.');
  assert.equal(existingCustomerMessage(input, { name: "새 고객", billing_email: "new@example.com" }), undefined);
  assert.equal(existingCustomerMessage({ ...input, externalId: "" }, { name: "기존 고객", billing_email: null }), undefined);
});

