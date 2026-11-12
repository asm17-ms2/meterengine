import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import test from "node:test";

const root = fileURLToPath(new URL("../src/", import.meta.url));
const sources = readdirSync(root, { recursive: true })
  .map(name => String(name).replaceAll("\\", "/"))
  .filter(name => /\.(ts|tsx|css)$/.test(name))
  .map(name => ({ name, lines: readFileSync(root + name, "utf8").split("\n") }));

// frontend/src의 .ts, .tsx, .css만 주석까지 본다. 서버가 보내는 오류 문구는 이 검사 밖이다. 걸린 곳은 src/파일:줄 번호로 나온다.
// public/은 브랜드 이미지 둘뿐이고 e2e의 .mjs는 금지어가 없음을 단언하거나 서버 문구를 흉내 내느라 금지어를 담고 있어 넣지 않는다.
function hits(pattern, allowed = []) {
  return sources
    .filter(({ name }) => !allowed.includes(name))
    .flatMap(({ name, lines }) => lines.flatMap((line, index) => pattern.test(line) ? [`src/${name}:${index + 1}`] : []));
}

test("the scan reads the frontend sources", () => {
  assert.ok(sources.length > 100 && sources.some(({ name }) => name === "lib/environment.ts"));
});

// 이용약관과 개인정보 처리방침은 계약 당사자를 "도입사"로 정해 쓰는 문서라 본문을 예외로 둔다. 고치려면 판(CONSENT_VERSION)을 올려야 한다.
const LEGAL_DOCUMENTS = ["app/(public)/terms/page.tsx", "app/(public)/privacy/page.tsx"];

test("frontend sources call the reader's company an organization, never the vendor-side word", () => {
  assert.deepEqual(hits(/도입사/, LEGAL_DOCUMENTS), []);
});

// 환경 이름표 한 곳만 예외다. 서버 설정을 켠 곳의 환경 목록에서만 보인다.
test("in frontend sources the pre-environment data label lives only in the environment label table", () => {
  assert.deepEqual(hits(/기존 데이터/, ["lib/environment.ts"]), []);
});

// 약관의 "데모 릴리스"는 서비스 단계의 이름이라 남긴다.
test("frontend sources carry no demo-only wording", () => {
  assert.deepEqual(hits(/시연/), []);
  assert.deepEqual(hits(/데모/, ["app/(public)/terms/page.tsx"]), []);
});

// 텍스트 모양이 기본인 이모지(U+26A0 등)와 가운뎃점 대용 글자(U+318D, U+30FB, U+2027)도 잡는다. 저작권, 등록상표, 상표 기호는 이모지가 아니라 뺀다.
const BANNED_CHARACTERS = /[\u2014\u2013\u00B7\u201C\u201D\u2018\u2019\u318D\u30FB\u2027]|\p{Emoji_Presentation}|(?![\u00A9\u00AE\u2122])\p{Extended_Pictographic}/u;

test("the banned character pattern catches text-style emoji and middle-dot lookalikes but not ordinary symbols", () => {
  for (const sample of ["\u26A0", "\u26A0\uFE0F", "\u2714", "\u2764", "\u{1F600}", "\u318D", "\u30FB", "\u2027", "\u00B7", "\u2014"]) assert.ok(BANNED_CHARACTERS.test(sample), sample);
  for (const sample of ["0", "#", "*", "\u00A9", "\u00AE", "\u2122", "\u2192", "-", ":"]) assert.ok(!BANNED_CHARACTERS.test(sample), sample);
});

test("frontend sources have no banned characters", () => {
  assert.deepEqual(hits(BANNED_CHARACTERS), []);
});
