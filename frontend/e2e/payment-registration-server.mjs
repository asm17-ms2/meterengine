import { createServer } from "node:http";

const TOKEN = "a".repeat(43);
const CUSTOMER_ID = "9a85005e-e1f2-40cd-a6a8-cb3ee3d643a1";
let state;

function reset() {
  state = { used: false, requests: [], status: 201 };
}
reset();

createServer(async (request, response) => {
  const path = new URL(request.url, "http://127.0.0.1:38149").pathname;
  let text = "";
  for await (const chunk of request) text += chunk;
  const body = text ? JSON.parse(text) : {};
  response.setHeader("Content-Type", "application/json");
  function send(status, value) { response.writeHead(status); response.end(JSON.stringify(value)); }
  if (path === "/health") return send(200, {});
  if (path === "/__control") {
    if (body.reset) reset();
    if (body.status) state.status = body.status;
    if (body.used !== undefined) state.used = body.used;
    return send(200, {});
  }
  if (path === "/__requests") return send(200, state.requests);
  if (path === `/v1/payment-method-registrations/${TOKEN}` && request.method === "GET") {
    if (state.used) return send(404, { code: "payment_method_registration_not_found", message: "등록 링크 없음" });
    return send(200, { customer_id: CUSTOMER_ID, customer_key: CUSTOMER_ID, customer_name: "시연 고객", organization_name: "시연 도입사", client_key: "test_ck_fixture", mode: "test", expires_at: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString() });
  }
  if (path === `/v1/payment-method-registrations/${TOKEN}/billing-keys` && request.method === "POST") {
    state.requests.push({ path, method: request.method, body, hasAuthorization: Boolean(request.headers.authorization) });
    if (state.used) return send(404, { code: "payment_method_registration_not_found", message: "등록 링크 없음" });
    if (state.status !== 201) return send(state.status, { code: "toss_payments_unavailable", message: "토스 응답 미확인" });
    if (body.customer_key !== CUSTOMER_ID || !body.consent_document_version || body.auth_key !== "fixture-auth-key") return send(400, { code: "validation_error", message: "인증 정보 불일치" });
    state.used = true;
    return send(201, { customer_id: CUSTOMER_ID, mode: "test", card_issuer_code: "61", card_number: "43301234****123*", authenticated_at: new Date().toISOString() });
  }
  send(404, { code: "endpoint_not_found", message: "경로 없음" });
}).listen(38149, "127.0.0.1");
