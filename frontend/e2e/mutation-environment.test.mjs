import assert from "node:assert/strict";
import { registerHooks } from "node:module";
import test from "node:test";

registerHooks({ resolve(specifier, context, next) {
  if (specifier === "server-only") return { url: "data:text/javascript,export{}", shortCircuit: true };
  if (specifier === "next/headers") return { url: "data:text/javascript,export async function headers(){return globalThis.scopeHeaders}", shortCircuit: true };
  return next(specifier.startsWith("@/") ? new URL(`../src/${specifier.slice(2)}.ts`, import.meta.url).href : specifier, context);
} });

test("business mutations require one exact matching form and original URL environment", async () => {
  const { mutationScopeError } = await import("../src/lib/auth/mutation-scope.ts");
  for (const urlEnvironment of [null, "", "legacy", "test", "live"]) {
    globalThis.scopeHeaders = new Headers(urlEnvironment === null ? {} : { "x-meterengine-environment": urlEnvironment });
    for (const formEnvironment of [null, "", "legacy", "test", "live", " test ", " live "]) {
      const form = new FormData(); form.set("organization_id", "original-org");
      if (formEnvironment !== null) form.set("environment", formEnvironment);
      const error = await mutationScopeError(form, "original-org");
      assert.equal(error === null, ["test", "live"].includes(urlEnvironment) && formEnvironment === urlEnvironment, JSON.stringify({ urlEnvironment, formEnvironment }));
    }
  }
  globalThis.scopeHeaders = new Headers({ "x-meterengine-environment": "test" });
  const form = new FormData(); form.set("organization_id", "original-org"); form.append("environment", "test"); form.append("environment", "live");
  assert.notEqual(await mutationScopeError(form, "original-org"), null);
});

test("shared mutations omit environment but retain the original organization guard", async () => {
  const { mutationScopeError } = await import("../src/lib/auth/mutation-scope.ts");
  globalThis.scopeHeaders = new Headers();
  const form = new FormData(); form.set("organization_id", "original-org");
  assert.equal(await mutationScopeError(form, "original-org", { requiresEnvironment: false }), null);
  assert.notEqual(await mutationScopeError(form, "changed-org", { requiresEnvironment: false }), null);
  form.append("organization_id", "other-org");
  assert.notEqual(await mutationScopeError(form, "original-org", { requiresEnvironment: false }), null);
});
