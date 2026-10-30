import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = path => readFileSync(new URL(`../src/${path}`, import.meta.url), "utf8");

test("invoice link API types use the backend schema names", () => {
  const types = read("lib/invoice-view-links.ts");
  for (const name of ["InvoiceViewLinkResponse", "InvoiceViewLinkSecretResponse", "PublicInvoiceResponse"]) assert.match(types, new RegExp(`export type ${name}\\b`));
  for (const name of ["InvoiceViewLink", "CreatedInvoiceViewLink", "PublicInvoice"]) assert.doesNotMatch(types, new RegExp(`export type ${name} `));
});

test("link results are announced through a status region that is always present and re-announced on repeat", () => {
  for (const path of ["components/workspace/InvoiceViewLinks.tsx", "components/invoice-links/PublicInvoiceView.tsx"]) {
    const source = read(path);
    assert.doesNotMatch(source, /\{feedback && <p role="status"/, path);
    assert.match(source, /requestAnimationFrame\(\(\) => setFeedback\(/, path);
  }
});

test("revoking a link asks in the console dialog with a danger confirmation instead of the browser", () => {
  const source = read("components/workspace/InvoiceViewLinks.tsx");
  assert.doesNotMatch(source, /window\.confirm\(/);
  assert.match(source, /<Button type="link" size="small" danger/);
});
