import { cp, mkdir, readdir, lstat, readlink, symlink, unlink, writeFile, stat } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const app = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const args = process.argv.slice(2).filter(value => value !== "--");
if (args.length !== 2 || args[0] !== "--output") throw new Error("사용법: node scripts/export-standalone.mjs --output /새/절대경로");
const output = path.resolve(args[1]);
if (await stat(output).then(() => true, () => false)) throw new Error("기존 폴더는 덮어쓰지 않습니다");
const source = path.join(app, ".next", "standalone");
await stat(path.join(source, "llm-service", "server.js"));
await mkdir(path.dirname(output), { recursive: true });
// 앱 폴더의 .env와 로컬 DB는 build가 standalone에 함께 복사하므로 묶음에서 뺀다.
const excluded = [];
function isLocalSecret(file) {
  const relative = path.relative(source, file).split(path.sep).join("/");
  return /^llm-service\/\.env(\..+)?$/.test(relative) || relative === "llm-service/data";
}
await cp(source, output, { recursive: true, verbatimSymlinks: true, filter: file => { if (!isLocalSecret(file)) return true; excluded.push(path.relative(source, file)); return false; } });
await cp(path.join(app, ".next", "static"), path.join(output, "llm-service", ".next", "static"), { recursive: true });
if (await stat(path.join(app, "public")).then(() => true, () => false)) await cp(path.join(app, "public"), path.join(output, "llm-service", "public"), { recursive: true });
await cp(path.join(app, "scripts", "start.mjs"), path.join(output, "start.mjs"));
let links = 0;
let prunedUntracedLinks = 0;
async function checkLinks(directory) {
  for (const name of await readdir(directory)) {
    const target = path.join(directory, name);
    const info = await lstat(target);
    if (info.isSymbolicLink()) {
      links += 1;
      const value = await readlink(target);
      let resolved = path.resolve(path.dirname(target), value);
      if (path.isAbsolute(value) && (value === source || value.startsWith(source + path.sep))) {
        resolved = path.join(output, path.relative(source, value));
        await unlink(target);
        await symlink(path.relative(path.dirname(target), resolved), target);
      }
      if (!resolved.startsWith(output + path.sep)) throw new Error("묶음 밖을 참조하는 링크가 있습니다");
      try {
        await stat(resolved);
      } catch (error) {
        if (error.code !== "ENOENT") throw error;
        const original = path.join(source, path.relative(output, target));
        const originalExists = await stat(original).then(() => true, cause => { if (cause.code === "ENOENT") return false; throw cause; });
        if (originalExists) throw new Error("실행 묶음에 필요한 파일이 복사되지 않았습니다");
        await unlink(target);
        prunedUntracedLinks += 1;
      }
    } else if (info.isDirectory()) await checkLinks(target);
  }
}
await checkLinks(output);
await writeFile(path.join(output, "bundle.json"), JSON.stringify({ application: "lumen-llm-service", node: ">=24.22.1", links, prunedUntracedLinks, excludedLocalFiles: excluded.length, configuration: "external JSON environment file", database: "external LUMEN_DATABASE_PATH", entrypoint: "start.mjs" }, null, 2) + "\n");
console.log(JSON.stringify({ output, portableLinksChecked: links, prunedUntracedLinks, excludedLocalFiles: excluded, entrypoint: "start.mjs" }));
