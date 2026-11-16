import assert from "node:assert/strict";
import test from "node:test";
import { shareInFlight } from "../src/lib/api/in-flight.ts";
import { searchKey } from "../src/lib/search-key.ts";

test("a request with the same key waits for the one already running instead of sending again", async () => {
  let calls = 0;
  let finish;
  const run = () => { calls += 1; return new Promise(resolve => { finish = resolve; }); };
  const first = shareInFlight("same", run);
  const second = shareInFlight("same", run);
  finish("result");
  assert.deepEqual(await Promise.all([first, second]), ["result", "result"]);
  assert.equal(calls, 1);
});

test("a finished or failed request is not kept, so the next one is sent again", async () => {
  let calls = 0;
  await shareInFlight("done", async () => { calls += 1; return 1; });
  await assert.rejects(shareInFlight("done", async () => { calls += 1; throw new Error("down"); }));
  await shareInFlight("done", async () => { calls += 1; return 2; });
  assert.equal(calls, 3);
});

test("different keys run separately", async () => {
  let calls = 0;
  const run = async () => { calls += 1; };
  await Promise.all([shareInFlight("a", run), shareInFlight("b", run)]);
  assert.equal(calls, 2);
});

test("the loading key ignores parameter order and changes when a condition changes", () => {
  assert.equal(searchKey({ month: "2026-10", page: "1" }), searchKey({ page: "1", month: "2026-10" }));
  assert.notEqual(searchKey({ month: "2026-10", page: "1" }), searchKey({ month: "2026-10", page: "2" }));
  assert.equal(searchKey({ month: "2026-10", q: undefined }), searchKey({ month: "2026-10" }));
});
