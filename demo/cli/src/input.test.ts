import assert from "node:assert/strict";
import { PassThrough, Readable } from "node:stream";
import { test } from "node:test";
import { UsageError } from "./exit-code.ts";
import { readAll, readHiddenLine } from "./input.ts";

test("readAll은 청크 경계에 걸린 한글을 깨뜨리지 않는다", async () => {
  const text = `id-${"가".repeat(30000)}\nid-나\n`;
  const bytes = Buffer.from(text, "utf8");
  // 64KB 청크로 나누면 3바이트 글자 하나가 경계에 걸린다.
  const chunks = [bytes.subarray(0, 65536), bytes.subarray(65536)];

  assert.equal(await readAll(Readable.from(chunks)), text);
});

test("readHiddenLine은 입력한 한 줄을 돌려준다", async () => {
  const input = new PassThrough();
  const line = readHiddenLine(input);
  input.write("me_test_abc\n");

  assert.equal(await line, "me_test_abc");
});

test("readHiddenLine은 Ctrl-C와 입력 끝을 취소로 끝낸다", async () => {
  for (const finish of [(input: PassThrough) => input.write("\x03"), (input: PassThrough) => input.end()]) {
    const input = new PassThrough();
    const line = readHiddenLine(input);
    finish(input);

    await assert.rejects(line, (error: unknown) => error instanceof UsageError && error.code === "login_cancelled");
  }
});
