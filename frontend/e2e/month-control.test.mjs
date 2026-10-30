import assert from "node:assert/strict";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import test from "node:test";

const SRC = new URL("../src/", import.meta.url).pathname;
const files = dir => readdirSync(dir).flatMap(name => {
  const path = join(dir, name);
  return statSync(path).isDirectory() ? files(path) : name.endsWith(".tsx") ? [path] : [];
});

test("console lookup filters use MonthPicker instead of native month input", () => {
  const native = files(SRC).filter(path => /type="month"/.test(readFileSync(path, "utf8"))).map(path => relative(SRC, path));
  // 요금 설계 등록의 적용 월은 조회 필터가 아니라 min 제약과 필드 오류가 붙은 입력이라 남긴다.
  assert.deepEqual(native, ["components/pricing/PricingWorkspace.tsx"]);
});
