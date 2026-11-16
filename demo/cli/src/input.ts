import { createInterface } from "node:readline";
import { Writable } from "node:stream";
import { UsageError } from "./exit-code.ts";

// 청크를 모아 한 번에 UTF-8로 푼다. 청크마다 풀면 경계에 걸린 한글이 깨진다.
export async function readAll(input: AsyncIterable<Buffer | string>): Promise<string> {
  const chunks: Buffer[] = [];
  for await (const chunk of input) chunks.push(typeof chunk === "string" ? Buffer.from(chunk, "utf8") : chunk);
  return Buffer.concat(chunks).toString("utf8");
}

// 터미널에서 한 줄을 화면에 보이지 않게 받는다. 입력 중 Ctrl-C나 Ctrl-D는 취소다.
export function readHiddenLine(input: NodeJS.ReadableStream): Promise<string> {
  const silent = new Writable({ write: (_chunk, _encoding, callback) => callback() });
  const reader = createInterface({ input, output: silent, terminal: true });
  return new Promise<string>((resolve, reject) => {
    let isSettled = false;
    const cancel = () => {
      if (isSettled) return;
      isSettled = true;
      reject(new UsageError("login_cancelled", "키 입력을 취소했습니다. 저장하지 않았습니다"));
      reader.close();
    };
    reader.once("line", (line) => {
      isSettled = true;
      resolve(line);
      reader.close();
    });
    reader.once("SIGINT", cancel);
    reader.once("close", cancel);
  });
}
