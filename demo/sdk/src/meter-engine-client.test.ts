import assert from "node:assert/strict";
import { test } from "node:test";
import { MeterEngineClient } from "./index.ts";


const BASE_URL = "http://localhost:8080";

test("apiKey에 줄바꿈이 있으면 키를 메시지에 넣지 않고 만들 때 던진다", () => {
  assert.throws(
    () => new MeterEngineClient({ baseUrl: BASE_URL, apiKey: "me_live_SECRET\n" }),
    (error: unknown) => error instanceof Error && !error.message.includes("SECRET"),
  );
});
