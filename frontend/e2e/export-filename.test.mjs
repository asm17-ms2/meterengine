import assert from "node:assert/strict";
import test from "node:test";
import { exportFilename, filenameFromDisposition } from "../src/lib/export-filename.ts";

test("month exports carry the month and the business environment", () => {
  assert.equal(exportFilename("invoices", { month: "2026-10", environment: "live" }), "invoices-2026-10-live.csv");
  assert.equal(exportFilename("usage", { month: "2026-10", environment: "test" }), "usage-2026-10-test.csv");
});

test("legacy exports leave the environment out and ranges replace the month", () => {
  assert.equal(exportFilename("events", { month: "2026-10", environment: "legacy" }), "events-2026-10.csv");
  assert.equal(exportFilename("events", { month: "2026-10", from: "2026-10-01", to: "2026-10-07" }), "events-2026-10-01_2026-10-07.csv");
  assert.equal(exportFilename("customers", {}), "customers.csv");
});

test("unsafe characters never reach the file name", () => {
  assert.equal(exportFilename("events", { from: "2026-10-01T00:00:00+09:00", to: "2026-10-02" }), "events-2026-10-01T00-00-00-09-00_2026-10-02.csv");
});

test("the download keeps the server file name and falls back to the resource name", () => {
  assert.equal(filenameFromDisposition('attachment; filename="invoices-2026-10-live.csv"', "invoices"), "invoices-2026-10-live.csv");
  assert.equal(filenameFromDisposition(null, "invoices"), "invoices.csv");
});
