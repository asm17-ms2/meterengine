import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const VIEW = readFileSync(new URL("../src/components/workspace/WorkspaceView.tsx", import.meta.url), "utf8");

test("workspace mobile record title is not a heading, so cards do not skip levels after the page h1", () => {
  const record = VIEW.match(/className="workspace-mobile-record">(.*?)<dl>/s)?.[1];
  assert.ok(record, "모바일 카드 마크업을 찾지 못했다");
  assert.doesNotMatch(record, /<h[1-6][\s>]/);
});
