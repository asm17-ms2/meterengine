#!/usr/bin/env node
import { readFile } from "node:fs/promises";
import { homedir } from "node:os";
import { join } from "node:path";
import { readAll, readHiddenLine } from "./input.ts";
import { run } from "./program.ts";

// 터미널이면 입력을 화면에 보이지 않게 받고, 아니면 표준 입력 전체를 읽는다.
async function readSecret(): Promise<string> {
  if (!process.stdin.isTTY) return readAll(process.stdin);
  process.stderr.write("API 키: ");
  try {
    return await readHiddenLine(process.stdin);
  } finally {
    process.stderr.write("\n");
  }
}

// "-"는 표준 입력을 끝까지 읽는다.
async function readInput(path: string): Promise<string> {
  if (path === "-") return readAll(process.stdin);
  return readFile(path, "utf8");
}

const CONFIG_DIR = join(process.env.XDG_CONFIG_HOME || join(homedir(), ".config"), "meterengine");

// | head처럼 받는 쪽이 먼저 닫히면 남은 출력은 버리고 조용히 끝낸다.
for (const stream of [process.stdout, process.stderr]) {
  stream.on("error", (error: NodeJS.ErrnoException) => {
    if (error.code !== "EPIPE") throw error;
    process.exit(process.exitCode ?? 0);
  });
}

process.exitCode = await run(process.argv.slice(2), {
  env: process.env,
  configDir: CONFIG_DIR,
  stdout: (text) => process.stdout.write(text),
  stderr: (text) => process.stderr.write(text),
  readSecret,
  readFile: readInput,
});
