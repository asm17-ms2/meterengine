import assert from "node:assert/strict";
import test from "node:test";
import { clientAddressFromForwardedFor, withForwardedClientAddress } from "../src/lib/client-address.ts";

test("the client address is the rightmost forwarded value", () => {
  assert.equal(clientAddressFromForwardedFor("203.0.113.9, 198.51.100.77"), "198.51.100.77");
  assert.equal(clientAddressFromForwardedFor("198.51.100.77"), "198.51.100.77");
  assert.equal(clientAddressFromForwardedFor("203.0.113.9,2001:db8::7"), "2001:db8::7");
});

test("a missing or non-numeric rightmost value gives no client address", () => {
  for (const value of [null, "", "203.0.113.9, ", "203.0.113.9, localhost", "198.51.100.77:443", "unknown"]) {
    assert.equal(clientAddressFromForwardedFor(value), undefined, String(value));
  }
});

test("a client address the visitor sent is replaced by the rightmost forwarded value", () => {
  const forwarded = withForwardedClientAddress(new Headers({ "x-forwarded-for": "203.0.113.9, 198.51.100.77", "x-meterengine-client-ip": "203.0.113.10" }));
  assert.equal(forwarded.get("x-meterengine-client-ip"), "198.51.100.77");
});

test("a client address the visitor sent is dropped when no forwarded value can be trusted", () => {
  const forwarded = withForwardedClientAddress(new Headers({ "x-meterengine-client-ip": "203.0.113.10" }));
  assert.equal(forwarded.has("x-meterengine-client-ip"), false);
});
