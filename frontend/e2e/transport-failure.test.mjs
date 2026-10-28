import assert from "node:assert/strict";
import test, { mock } from "node:test";
import { describeTransportFailure, maskApiPath } from "../src/lib/api/transport-failure.ts";

test("a timeout and a connection failure become different errors with status 0", () => {
  const log = mock.method(console, "error", () => {});
  try {
    const timedOut = describeTransportFailure(new DOMException("timed out", "TimeoutError"), "GET", "/v1/customers?q=secret", 5003);
    const refused = describeTransportFailure(new TypeError("fetch failed"), "GET", "/v1/customers", 12);
    const timedOutWrite = describeTransportFailure(new DOMException("timed out", "TimeoutError"), "POST", "/v1/api-keys", 5001);
    assert.deepEqual([timedOut.status, timedOut.code], [0, "timeout"]);
    assert.deepEqual([refused.status, refused.code], [0, "network_error"]);
    assert.notEqual(timedOut.message, timedOutWrite.message);
    assert.equal(log.mock.callCount(), 3);
  } finally {
    log.mock.restore();
  }
});

test("the failure log line hides tokens, ids and query strings", () => {
  const log = mock.method(console, "error", () => {});
  try {
    describeTransportFailure(new TypeError("fetch failed"), "POST", "/v1/organization-invitations/me_invite_ABCDEFGHIJKLMNOPQRSTUVWXYZ012345/accept?x=1", 40);
    const line = String(log.mock.calls[0].arguments[0]);
    assert.equal(line, "[api] POST /v1/organization-invitations/:id/accept network_error 40ms");
    assert.equal(maskApiPath("/v1/customers/7c9e6679-7425-40de-944b-e07fc1f90ae7?month=2026-10"), "/v1/customers/:id");
  } finally {
    log.mock.restore();
  }
});
