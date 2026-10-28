import assert from "node:assert/strict";
import test from "node:test";
import { expiredLoginHref } from "../src/lib/auth/navigation.ts";

test("an expired session login keeps a safe return path", () => {
  assert.equal(expiredLoginHref("/billing?month=2026-09&organization_id=A"), `/login?reason=expired&returnTo=${encodeURIComponent("/billing?month=2026-09&organization_id=A")}`);
  assert.equal(expiredLoginHref("/admin/users"), `/login?reason=expired&returnTo=${encodeURIComponent("/admin/users")}`);
  for (const raw of [null, "//evil.example", "/login", "https://evil.example/customers"]) assert.equal(expiredLoginHref(raw), "/login?reason=expired");
});
