import assert from "node:assert/strict";
import test from "node:test";
import { paymentReadiness } from "../src/app/(console)/payments/[id]/card-state.ts";

const keys = modes => ({ ok: true, data: { toss_payments_credentials: modes.map(mode => ({ mode })) } });
const card = { ok: true, data: { card_number: "4330****1234" } };
const failed = status => ({ ok: false, error: { status } });

test("a payment can be requested only with the mode's Toss key and a registered card", () => {
  assert.equal(paymentReadiness("live", keys(["test"]), card), "key-missing");
  assert.equal(paymentReadiness("live", keys(["live"]), failed(404)), "card-missing");
  assert.equal(paymentReadiness("live", keys(["live"]), card), "usable");
  assert.equal(paymentReadiness("live", failed(403), card), "usable");
  assert.equal(paymentReadiness("live", keys(["live"]), failed(500)), "unknown");
  assert.equal(paymentReadiness("live", keys(["live"]), failed(0)), "unknown");
});
