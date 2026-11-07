# 가이드 MDX를 검사한다: 금지 문자, 자리 문구, 내부 경로와 Jira 키, docs.json 페이지 파일 유무, 내부 링크 대상.
# 실행: python3 -I guide/scripts/lint_guide.py  (표준 라이브러리만 쓴다, 오류가 있으면 1로 끝난다)
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent

FORBIDDEN = {
    "\u00b7": "가운뎃점", "\u2027": "가운뎃점", "\u30fb": "가운뎃점",
    "\u2014": "엠 대시", "\u2013": "엔 대시",
    "\u2018": "둥근 따옴표", "\u2019": "둥근 따옴표", "\u201c": "둥근 따옴표", "\u201d": "둥근 따옴표",
    "\ufeff": "BOM",
}
EMOJI = re.compile("[\U0001F300-\U0001FAFF\u2600-\u27bf\u2b50\u2b55\u2705\u274c\u26a0\ufe0f]")
PATTERNS = [
    (re.compile(r"작성 중"), "자리 문구"),
    (re.compile(r"\bTODO\b|\bFIXME\b"), "TODO"),
    (re.compile(r"\bbackend/|\bfrontend/|\bdeploy/|\bdemo/"), "레포 경로"),
    (re.compile(r"\.java\b|\.tsx\b|\.sql\b"), "소스 파일 확장자"),
    (re.compile(r"\bMS2-\d+"), "Jira 키"),
    (re.compile(r"(?<![\w/])#\d{3}\b"), "PR 번호처럼 보이는 값"),
]
COUNTING = re.compile(r"(두|세|네|다섯|여섯|일곱|여덟|아홉|열) ?(가지|종류|개|종)(?![가-힣])|\d+ ?종(?![가-힣])|\d+가지")
LINK = re.compile(r"\]\((/[^)\s#]*)(#[^)\s]*)?\)|href=\"(/[^\"#]*)")


def pages_from(node, out):
    if isinstance(node, dict):
        for key, value in node.items():
            if key == "pages" and isinstance(value, list):
                for item in value:
                    if isinstance(item, str):
                        out.append(item)
                    else:
                        pages_from(item, out)
            else:
                pages_from(value, out)
    elif isinstance(node, list):
        for item in node:
            pages_from(item, out)
    return out


def main():
    docs = json.loads((ROOT / "docs.json").read_text(encoding="utf-8"))
    pages = pages_from(docs["navigation"], [])
    errors, warnings = [], []
    for page in pages:
        if not (ROOT / f"{page}.mdx").exists():
            errors.append(f"docs.json 페이지 파일 없음: {page}")
    valid = {"/" + p for p in pages} | {"/"}
    files = sorted(p for p in ROOT.rglob("*.mdx") if "_meta" not in p.parts)
    for path in files:
        rel = path.relative_to(ROOT).as_posix()
        if rel[:-4] not in pages:
            warnings.append(f"{rel}: docs.json에 없는 MDX")
        text = path.read_text(encoding="utf-8")
        if not text.startswith("---\n") or "\ntitle:" not in text.split("---", 2)[1] or "\ndescription:" not in text.split("---", 2)[1]:
            errors.append(f"{rel}: frontmatter title 또는 description 없음")
        in_code = False
        for no, line in enumerate(text.splitlines(), 1):
            if line.lstrip().startswith("```"):
                in_code = not in_code
            for ch, name in FORBIDDEN.items():
                if ch in line:
                    errors.append(f"{rel}:{no}: {name}")
            if EMOJI.search(line):
                errors.append(f"{rel}:{no}: 이모지")
            for pattern, name in PATTERNS:
                if pattern.search(line):
                    errors.append(f"{rel}:{no}: {name}: {line.strip()[:80]}")
            if not in_code and COUNTING.search(line):
                warnings.append(f"{rel}:{no}: 개수 세기 의심: {COUNTING.search(line).group(0)}")
            for match in LINK.finditer(line):
                target = (match.group(1) or match.group(3) or "").rstrip("/") or "/"
                if target.startswith("/v1") or target.startswith("/customers") or target.startswith("/organization"):
                    continue
                if target not in valid:
                    errors.append(f"{rel}:{no}: docs.json에 없는 링크 {target}")
                if target.startswith("/api-reference/") and target != "/api-reference/introduction":
                    errors.append(f"{rel}:{no}: 개별 API 참조 링크 {target}")
    for message in warnings:
        print("경고", message)
    for message in errors:
        print("오류", message)
    print(f"페이지 {len(pages)}, MDX {len(files)}, 오류 {len(errors)}, 경고 {len(warnings)}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
