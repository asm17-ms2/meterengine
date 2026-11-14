import { chmod, lstat, realpath, rename, rm, writeFile } from "node:fs/promises";
import path from "node:path";

const isMissing = (error: unknown) => ["ENOENT", "ENOTDIR"].includes((error as NodeJS.ErrnoException).code ?? "");

// 경로가 들어 있는 git 작업 트리의 루트. 없으면 null. 아직 없는 폴더는 있는 조상에서 찾고 심볼릭 링크는 풀어서 본다.
export async function gitWorkTreeOf(file: string): Promise<string | null> {
  let existing = path.resolve(file);
  let current: string;
  for (;;) {
    try { current = await realpath(existing); break; } catch (error) {
      const parent = path.dirname(existing);
      if (!isMissing(error) || parent === existing) throw error;
      existing = parent;
    }
  }
  for (;;) {
    // 워크트리와 서브모듈은 .git이 폴더가 아니라 파일이다.
    if (await lstat(path.join(current, ".git")).then(() => true, error => { if (isMissing(error)) return false; throw error; })) return current;
    const parent = path.dirname(current);
    if (parent === current) return null;
    current = parent;
  }
}

// 출력 파일에는 대표 계정 암호와 키 원문이 들어간다. 커밋에 섞이지 않게 저장소 안의 경로는 받지 않는다.
export async function checkOutputPath(file: string): Promise<void> {
  const root = await gitWorkTreeOf(file);
  if (root) throw new Error(`출력 경로가 git 작업 트리(${root}) 안입니다. 키와 암호가 든 파일이니 저장소 밖의 경로를 쓰세요`);
}

// 소유자만 읽고 쓰는 파일(0600)로 바꿔 쓴다. 있던 파일의 권한이 넓어도 새 파일로 갈아 끼운다.
export async function writePrivate(file: string, content: string): Promise<void> {
  const temporary = `${file}.tmp`;
  // 앞선 실행이 남긴 .tmp는 권한이 넓거나 다른 파일을 가리키는 링크일 수 있다. 지우고, 새로 만들 수 있을 때만 쓴다.
  await rm(temporary, { force: true });
  await writeFile(temporary, content, { mode: 0o600, flag: "wx" });
  await chmod(temporary, 0o600);
  await rename(temporary, file);
}
