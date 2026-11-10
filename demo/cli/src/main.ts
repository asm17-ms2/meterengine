#!/usr/bin/env node
import { homedir } from "node:os";
import { join } from "node:path";
import { createInterface } from "node:readline";
import { Writable } from "node:stream";
import { run } from "./program.ts";

// 터미널이면 입력을 화면에 보이지 않게 받고, 아니면 표준 입력 전체를 읽는다.
async function readSecret(): Promise<string> {
  if (!process.stdin.isTTY) {
    let text = "";
    for await (const chunk of process.stdin) text += String(chunk);
    return text;
  }
  process.stderr.write("API 키: ");
  const silent = new Writable({ write: (_chunk, _encoding, callback) => callback() });
  const reader = createInterface({ input: process.stdin, output: silent, terminal: true });
  const line = await new Promise<string>((resolve) => reader.once("line", resolve));
  reader.close();
  process.stderr.write("\n");
  return line;
}

const CONFIG_DIR = join(process.env.XDG_CONFIG_HOME || join(homedir(), ".config"), "meterengine");

process.exitCode = await run(process.argv.slice(2), {
  env: process.env,
  configDir: CONFIG_DIR,
  stdout: (text) => process.stdout.write(text),
  stderr: (text) => process.stderr.write(text),
  readSecret,
});
