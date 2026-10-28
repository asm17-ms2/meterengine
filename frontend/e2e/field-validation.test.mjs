import assert from "node:assert/strict";
import test from "node:test";
import { fieldValidationMessage } from "../src/lib/field-validation.ts";

const valid = { valueMissing: false, typeMismatch: false, rangeUnderflow: false, rangeOverflow: false, stepMismatch: false, badInput: false, tooLong: false, valid: true };
const broken = key => ({ ...valid, [key]: true, valid: false });

test("native validity becomes a short inline message without the label", () => {
  assert.equal(fieldValidationMessage(broken("valueMissing"), {}), "값을 입력하세요.");
  assert.equal(fieldValidationMessage(broken("valueMissing"), { isSelect: true }), "항목을 선택하세요.");
  assert.equal(fieldValidationMessage(broken("typeMismatch"), { type: "email" }), "이메일 주소 형식으로 입력하세요.");
  assert.equal(fieldValidationMessage(broken("typeMismatch"), { type: "url" }), "URL 형식으로 입력하세요.");
  assert.equal(fieldValidationMessage(broken("rangeUnderflow"), { min: 1 }), "1 이상으로 입력하세요.");
  assert.equal(fieldValidationMessage(broken("rangeOverflow"), { max: 2147483647 }), "2147483647 이하로 입력하세요.");
  assert.equal(fieldValidationMessage(broken("stepMismatch"), {}), "정수로 입력하세요.");
  assert.equal(fieldValidationMessage(broken("badInput"), { step: "any" }), "숫자로 입력하세요.");
  assert.equal(fieldValidationMessage(broken("tooLong"), { maxLength: 255 }), "255자 이하로 입력하세요.");
  assert.equal(fieldValidationMessage(valid, {}), undefined);
});
