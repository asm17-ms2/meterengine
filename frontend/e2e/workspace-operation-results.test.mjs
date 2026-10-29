import assert from "node:assert/strict";
import test from "node:test";
import { operationResultCopy } from "../src/lib/workspace-operation-results.ts";

test("result labels come from the operation, not from response fields", () => {
  assert.deepEqual([operationResultCopy("createPaymentCancellation").refreshLabel, operationResultCopy("createPaymentCancellation").retryLabel], ["결제와 취소 내역 새로 조회", "새 취소 요청"]);
  assert.equal(operationResultCopy("createPaymentAttempt").retryLabel, "새 요청");
  assert.equal(operationResultCopy("createPaymentMethodRegistration").linkLabel, "카드 등록 링크");
  assert.equal(operationResultCopy("createOrganizationInvitation").linkLabel, "초대 링크");
  assert.match(operationResultCopy("createOrganizationInvitation").linkNote, /초대 링크/);
  const other = operationResultCopy("updateApiKeyScopes");
  assert.equal(other.refreshLabel, "최신 상태 새로 조회");
  assert.doesNotMatch(other.refreshLabel + other.linkLabel + other.linkNote, /결제|카드/);
});

test("Toss key save uses its own refresh label that matches its unconfirmed message", () => {
  assert.equal(operationResultCopy("updateTossPaymentsCredential").refreshLabel, "토스 키 상태 새로 조회");
});
