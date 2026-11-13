import assert from "node:assert/strict";
import { chmod, link, lstat, mkdir, mkdtemp, readFile, realpath, rm, stat, symlink, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { test } from "node:test";
import { checkOutputPath, gitWorkTreeOf, writePrivate } from "./output.ts";

async function temporary(run: (directory: string) => Promise<void>) {
  const directory = await realpath(await mkdtemp(path.join(os.tmpdir(), "openab-output-")));
  try { await run(directory); } finally { await rm(directory, { recursive: true, force: true }); }
}

test("git 작업 트리 안의 출력 경로는 받지 않는다", () => temporary(async directory => {
  const repository = path.join(directory, "repository");
  await mkdir(path.join(repository, ".git"), { recursive: true });
  await mkdir(path.join(repository, "demo"));
  for (const file of ["openab-config.json", "demo/openab-config.json", "demo/not-yet/made/openab-config.json"]) {
    await assert.rejects(() => checkOutputPath(path.join(repository, file)), /git 작업 트리/, file);
    assert.equal(await gitWorkTreeOf(path.join(repository, file)), repository);
  }
}));

test(".git이 파일인 워크트리와 심볼릭 링크로 들어가는 경로도 받지 않는다", () => temporary(async directory => {
  const worktree = path.join(directory, "worktree");
  await mkdir(path.join(worktree, "nested"), { recursive: true });
  await writeFile(path.join(worktree, ".git"), "gitdir: /somewhere/else\n");
  await assert.rejects(() => checkOutputPath(path.join(worktree, "nested", "openab-config.json")), /git 작업 트리/);
  await symlink(path.join(worktree, "nested"), path.join(directory, "link"));
  await assert.rejects(() => checkOutputPath(path.join(directory, "link", "openab-config.json")), /git 작업 트리/);
}));

test("저장소 밖의 경로는 받는다", () => temporary(async directory => {
  // 임시 폴더가 저장소 안인 환경에서는 이 검사가 뜻이 없어 건너뛴다.
  if (await gitWorkTreeOf(directory)) return;
  await checkOutputPath(path.join(directory, "private", "openab-config.json"));
  assert.equal(await gitWorkTreeOf(path.join(directory, "private", "openab-config.json")), null);
}));

test("비밀 파일은 소유자만 읽고 쓴다. 권한이 넓은 파일이 있었어도 같다", { skip: process.platform === "win32" }, () => temporary(async directory => {
  const file = path.join(directory, "openab-config.json");
  const previous = process.umask(0);
  try {
    await writePrivate(file, "{}\n");
    assert.equal((await stat(file)).mode & 0o777, 0o600);
    await chmod(file, 0o644);
    await writePrivate(file, "{\"again\":true}\n");
    assert.equal((await stat(file)).mode & 0o777, 0o600);
    await assert.rejects(() => stat(`${file}.tmp`), /ENOENT/);
  } finally { process.umask(previous); }
}));

test("남아 있던 .tmp가 권한이 넓은 파일이든 링크든 그 파일에는 쓰지 않는다", { skip: process.platform === "win32" }, () => temporary(async directory => {
  const file = path.join(directory, "openab-config.json");
  const SECRET = "{\"secret\":true}\n";
  // 하드 링크: 옛 .tmp를 그대로 열어 쓰면 같은 내용을 가리키는 다른 이름으로도 비밀이 보인다.
  const leftover = path.join(directory, "leftover");
  await writeFile(leftover, "old\n", { mode: 0o644 });
  await link(leftover, `${file}.tmp`);
  await writePrivate(file, SECRET);
  assert.equal(await readFile(leftover, "utf8"), "old\n");
  assert.equal(await readFile(file, "utf8"), SECRET);
  assert.equal((await stat(file)).mode & 0o777, 0o600);
  // 심볼릭 링크: 링크를 따라가 대상 파일에 쓰지 않는다.
  const target = path.join(directory, "target");
  await writeFile(target, "target\n", { mode: 0o644 });
  await symlink(target, `${file}.tmp`);
  await writePrivate(file, SECRET);
  assert.equal(await readFile(target, "utf8"), "target\n");
  assert.equal((await lstat(file)).isSymbolicLink(), false);
  assert.equal((await stat(file)).mode & 0o777, 0o600);
  await assert.rejects(() => lstat(`${file}.tmp`), /ENOENT/);
}));
