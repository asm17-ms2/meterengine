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

test("a number field without min reports the default minimum the input enforces", () => {
  assert.equal(fieldValidationMessage(broken("rangeUnderflow"), { type: "number" }), "0 이상으로 입력하세요.");
  assert.equal(fieldValidationMessage(broken("rangeUnderflow"), { type: "number", min: 1 }), "1 이상으로 입력하세요.");
});

test("a partly filled month field asks for both year and month", () => {
  assert.equal(fieldValidationMessage(broken("badInput"), { type: "month" }), "연도와 월을 모두 입력하세요.");
});

test("a partly filled required month field gets the month format message, not the empty one", () => {
  assert.equal(fieldValidationMessage({ ...broken("badInput"), valueMissing: true }, { type: "month" }), "연도와 월을 모두 입력하세요.");
});

test("a partly filled required number field gets the number format message, not the empty one", () => {
  assert.equal(fieldValidationMessage({ ...broken("badInput"), valueMissing: true }, { type: "number" }), "정수로 입력하세요.");
});
