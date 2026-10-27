import assert from "node:assert/strict";
import test from "node:test";
import { isAllowedPublicInvoiceRequest } from "../src/lib/public-invoice-request.ts";
import { isValidInvoiceViewToken } from "../src/lib/invoice-view-links.ts";

function request(origin, host, extra = {}, url = "http://internal-next:3000/invoice-links/lookup") {
  return new Request(url, { method: "POST", headers: { ...(origin ? { Origin: origin } : {}), Host: host, ...extra } });
}

test("HTTPS 종료 프록시는 지정한 외부 origin과 보존한 Host로 검증한다", () => {
  assert.equal(isAllowedPublicInvoiceRequest(request("https://console.example.com", "console.example.com"), "https://console.example.com"), true);
  assert.equal(isAllowedPublicInvoiceRequest(request("https://console.example.com", "console.example.com", { "X-Forwarded-Proto": "http" }), "https://console.example.com/"), true);
});

test("위조 forwarded 헤더는 Origin 또는 Host 불일치를 허용하지 않는다", () => {
  assert.equal(isAllowedPublicInvoiceRequest(request("https://attacker.example", "console.example.com", { "X-Forwarded-Host": "console.example.com", "X-Forwarded-Proto": "https" }), "https://console.example.com"), false);
  assert.equal(isAllowedPublicInvoiceRequest(request("https://console.example.com", "attacker.example", { "X-Forwarded-Host": "console.example.com" }), "https://console.example.com"), false);
  assert.equal(isAllowedPublicInvoiceRequest(request("http://console.example.com", "console.example.com"), "https://console.example.com"), false);
});

test("origin 설정이 없으면 loopback의 같은 Host만 허용한다", () => {
  for (const host of ["localhost:33147", "127.0.0.1:33147", "[::1]:33147"]) {
    assert.equal(isAllowedPublicInvoiceRequest(request(`http://${host}`, host)), true);
  }
  assert.equal(isAllowedPublicInvoiceRequest(request("http://localhost:33147", "localhost:33148")), false);
  assert.equal(isAllowedPublicInvoiceRequest(request("https://console.example.com", "console.example.com")), false);
  assert.equal(isAllowedPublicInvoiceRequest(request("http://localhost.attacker.example:33147", "localhost.attacker.example:33147")), false);
});

test("누락과 null Origin, cross-site 요청은 거절한다", () => {
  assert.equal(isAllowedPublicInvoiceRequest(request(null, "localhost:33147")), false);
  assert.equal(isAllowedPublicInvoiceRequest(request("null", "localhost:33147")), false);
  assert.equal(isAllowedPublicInvoiceRequest(request("http://localhost:33147", "localhost:33147", { "Sec-Fetch-Site": "cross-site" })), false);
});

test("자격 증명이나 경로가 섞인 origin 설정은 닫힌 상태로 실패한다", () => {
  for (const configured of ["not-a-url", "https://user:password@console.example.com", "https://console.example.com/path", "https://console.example.com?debug=1", "https://console.example.com#token"]) {
    assert.equal(isAllowedPublicInvoiceRequest(request("https://console.example.com", "console.example.com"), configured), false);
  }
});

test("외부 Origin의 비표준 경로와 허용하지 않은 프로토콜을 거절한다", () => {
  assert.equal(isAllowedPublicInvoiceRequest(request("http://localhost:33147/", "localhost:33147")), false);
  assert.equal(isAllowedPublicInvoiceRequest(request("file://localhost", "localhost")), false);
});

test("공개 조회 토큰만 허용하고 세션이나 카드 등록 토큰을 받지 않는다", () => {
  assert.equal(isValidInvoiceViewToken("me_invoice_" + "aB7".repeat(10) + "xY"), true);
  for (const token of [null, "", "a".repeat(43), "me_session_" + "a".repeat(32), "me_invoice_" + "a".repeat(31), "me_invoice_" + "a".repeat(33), "me_invoice_" + "_".repeat(32)]) {
    assert.equal(isValidInvoiceViewToken(token), false);
  }
});
