import { createHmac, randomUUID, timingSafeEqual } from "node:crypto";
import { createServer } from "node:http";
import { closeSync, existsSync, fchmodSync, mkdirSync, openSync, readFileSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";

const PORT = 38447;
const STATE_FILE = process.env.MVP_PG_STATE_FILE;
const saved = STATE_FILE && existsSync(STATE_FILE) ? JSON.parse(readFileSync(STATE_FILE, "utf8")) : {};
const payments = new Map(saved.payments ?? []);
const orders = new Map(saved.orders ?? []);
const idempotentResponses = new Map(saved.idempotentResponses ?? []);
const billingKeys = new Map(saved.billingKeys ?? []);
const webhookSecretsByChannel = new Map(saved.webhookSecretsByChannel ?? []);
const transactions = saved.transactions ?? [];
const webhooks = saved.webhooks ?? [];
let approvalCalls = saved.approvalCalls ?? 0;
let cancellationCalls = saved.cancellationCalls ?? 0;
let issuanceCalls = saved.issuanceCalls ?? 0;
let nextApproval = "done";
let nextCancellationDelayMs = 0;

function save() {
  if (!STATE_FILE) return;
  mkdirSync(dirname(STATE_FILE), { recursive: true, mode: 0o700 });
  const fd = openSync(STATE_FILE, "w", 0o600);
  try {
    fchmodSync(fd, 0o600);
    writeFileSync(fd, JSON.stringify({ payments: [...payments], orders: [...orders], idempotentResponses: [...idempotentResponses], billingKeys: [...billingKeys], webhookSecretsByChannel: [...webhookSecretsByChannel], transactions, webhooks, approvalCalls, cancellationCalls, issuanceCalls }));
  } finally { closeSync(fd); }
}

function providerTime(value) {
  return value && !/[zZ]|[+-]\d\d:\d\d$/.test(value) ? new Date(`${value}+09:00`) : new Date(value);
}

createServer(async (request, response) => {
  const url = new URL(request.url, `http://127.0.0.1:${PORT}`);
  const path = url.pathname;
  let text = "";
  for await (const chunk of request) text += chunk;
  let body;
  try { body = text ? JSON.parse(text) : {}; } catch { response.writeHead(400); response.end(); return; }
  function send(status, value) {
    save();
    response.writeHead(status, { "Content-Type": "application/json" });
    response.end(JSON.stringify(value));
  }
  function reject(status, code) { send(status, { code, message: "로컬 PG 검증 응답" }); }
  if (path === "/health") return send(200, { provider: "controlled-local-fixture" });
  if (path === "/__control") {
    if (body.webhookSecret) webhookSecretsByChannel.set(body.channel ?? "default", body.webhookSecret);
    if (body.nextApproval) nextApproval = body.nextApproval;
    if (typeof body.nextCancellationDelayMs === "number") nextCancellationDelayMs = body.nextCancellationDelayMs;
    return send(200, {});
  }
  if (path === "/__state") return send(200, { approval_calls: approvalCalls, cancellation_calls: cancellationCalls, issuance_calls: issuanceCalls, payment_count: payments.size, transaction_count: transactions.length, webhooks });
  if (path === "/meterengine-webhooks" || path.startsWith("/meterengine-webhooks/")) {
    const channel = path.split("/")[2] ?? "default";
    const webhookSecret = webhookSecretsByChannel.get(channel);
    const id = request.headers["webhook-id"];
    const timestamp = request.headers["webhook-timestamp"];
    const received = request.headers["webhook-signature"];
    const expected = webhookSecret ? `v1,${createHmac("sha256", Buffer.from(webhookSecret.slice(6), "base64")).update(`${id}.${timestamp}.${text}`).digest("base64")}` : "";
    const isValid = typeof received === "string" && received.length === expected.length && timingSafeEqual(Buffer.from(received), Buffer.from(expected));
    webhooks.push({ id, channel, type: body.type, is_valid: isValid, data: body.data });
    return send(isValid ? 200 : 400, {});
  }
  const authorization = request.headers.authorization;
  const credential = typeof authorization === "string" && authorization.startsWith("Basic ")
    ? Buffer.from(authorization.slice(6), "base64").toString().replace(/:$/, "") : "";
  if (!["test_sk_", "live_sk_"].some(prefix => credential.startsWith(prefix))) return reject(401, "UNAUTHORIZED_KEY");
  const idempotencyKey = request.headers["idempotency-key"];
  const cached = idempotencyKey && idempotentResponses.get(`${credential}/${path}/${idempotencyKey}`);
  if (cached) return send(200, cached);
  if (path === "/v1/billing/authorizations/issue" && request.method === "POST") {
    issuanceCalls++;
    if (!body.authKey?.startsWith("mvp-auth-") || !body.customerKey) return reject(400, "INVALID_AUTH_KEY");
    const billingKey = `fixture-billing-${randomUUID()}`;
    billingKeys.set(billingKey, { credential, customerKey: body.customerKey });
    return send(200, { billingKey, authenticatedAt: new Date().toISOString(), card: { issuerCode: "61", number: "43301234****123*" } });
  }
  const billingMatch = /^\/v1\/billing\/([^/]+)$/.exec(path);
  if (billingMatch && request.method === "DELETE") { billingKeys.delete(decodeURIComponent(billingMatch[1])); return send(200, {}); }
  if (billingMatch && request.method === "POST") {
    const billing = billingKeys.get(decodeURIComponent(billingMatch[1]));
    approvalCalls++;
    if (!billing || billing.credential !== credential || billing.customerKey !== body.customerKey) return reject(400, "NOT_MATCHES_CUSTOMER_KEY");
    const outcome = nextApproval;
    nextApproval = "done";
    if (outcome === "rejected" || request.headers["tosspayments-test-code"] === "REJECT_CARD_PAYMENT") return reject(403, "REJECT_CARD_PAYMENT");
    const orderKey = `${credential}/${body.orderId}`;
    if (orders.has(orderKey)) return send(200, payments.get(orders.get(orderKey)).payment);
    const paymentKey = `fixture-payment-${randomUUID()}`;
    const payment = { paymentKey, orderId: body.orderId, status: "DONE", totalAmount: body.amount, balanceAmount: body.amount, cancels: [] };
    payments.set(paymentKey, { credential, payment });
    orders.set(orderKey, paymentKey);
    transactions.push({ transactionKey: `fixture-transaction-${randomUUID()}`, paymentKey, orderId: body.orderId, status: "DONE", transactionAt: new Date().toISOString(), amount: body.amount });
    if (idempotencyKey) idempotentResponses.set(`${credential}/${path}/${idempotencyKey}`, structuredClone(payment));
    if (outcome === "response-lost") { save(); request.socket.destroy(); return; }
    return send(200, payment);
  }
  const orderMatch = /^\/v1\/payments\/orders\/([^/]+)$/.exec(path);
  if (orderMatch) {
    const paymentKey = orders.get(`${credential}/${decodeURIComponent(orderMatch[1])}`);
    return paymentKey ? send(200, payments.get(paymentKey).payment) : reject(404, "NOT_FOUND_PAYMENT");
  }
  const cancelMatch = /^\/v1\/payments\/([^/]+)\/cancel$/.exec(path);
  if (cancelMatch && request.method === "POST") {
    const stored = payments.get(decodeURIComponent(cancelMatch[1]));
    if (!stored || stored.credential !== credential) return reject(404, "NOT_FOUND_PAYMENT");
    const payment = stored.payment;
    const cancelAmount = body.cancelAmount ?? payment.balanceAmount;
    if (cancelAmount <= 0 || cancelAmount > payment.balanceAmount) return reject(400, "INVALID_CANCEL_AMOUNT");
    const delay = nextCancellationDelayMs;
    nextCancellationDelayMs = 0;
    if (delay > 0) await new Promise(resolve => setTimeout(resolve, delay));
    cancellationCalls++;
    const transactionKey = `fixture-cancellation-${randomUUID()}`;
    const canceledAt = new Date().toISOString();
    payment.balanceAmount -= cancelAmount;
    payment.status = payment.balanceAmount ? "PARTIAL_CANCELED" : "CANCELED";
    payment.cancels.push({ transactionKey, cancelAmount, canceledAt, cancelStatus: "DONE" });
    transactions.push({ transactionKey, paymentKey: payment.paymentKey, orderId: payment.orderId, status: payment.status, transactionAt: canceledAt, amount: cancelAmount });
    if (idempotencyKey) idempotentResponses.set(`${credential}/${path}/${idempotencyKey}`, structuredClone(payment));
    return send(200, payment);
  }
  const paymentMatch = /^\/v1\/payments\/([^/]+)$/.exec(path);
  if (paymentMatch) {
    const stored = payments.get(decodeURIComponent(paymentMatch[1]));
    return stored?.credential === credential ? send(200, stored.payment) : reject(404, "NOT_FOUND_PAYMENT");
  }
  if (path === "/v1/transactions") {
    const from = providerTime(url.searchParams.get("startDate"));
    const to = providerTime(url.searchParams.get("endDate"));
    const start = url.searchParams.get("startingAfter");
    const rows = transactions.filter(transaction => payments.get(transaction.paymentKey)?.credential === credential && new Date(transaction.transactionAt) >= from && new Date(transaction.transactionAt) < to);
    const offset = start ? rows.findIndex(transaction => transaction.transactionKey === start) + 1 : 0;
    return send(200, rows.slice(offset, offset + Number(url.searchParams.get("limit") ?? 1000)));
  }
  reject(404, "NOT_FOUND_PAYMENT");
}).listen(PORT, "127.0.0.1");
