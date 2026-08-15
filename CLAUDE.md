# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 이 저장소에 대해

MeterEngine 제품 모노레포다. 사용량 기반 과금 플랫폼으로, raw usage event 수집 -> 미터링/집계 -> rating -> 인보이스 생성 파이프라인에 국내 PG 연동과 전자세금계산서 발행을 결합하는 것을 목표로 한다.

- `backend/`: 미터링 엔진 API 서버
- `frontend/`: 관리자 화면
- `demo/`: 데모 시연용 CLI (Python, 이벤트 전송과 청구 예정액 검증)
- `docs/`: 무엇을 둘지 미정 (아래 "문서 흐름" 참조)
- `work/`: 개인 작업 공간 (.gitignore로 제외, 아래 참조)

## 개인 작업 공간 (work/)

- `/work`는 .gitignore로 제외된 개인 공간이다. 팀원 각자 자기 work/를 만들어 자유롭게 쓴다. 개인 계정의 별도 레포로 백업해도 된다 (이 경우 work/ 안에서만 해당 레포의 git 명령을 실행한다)
- 커밋 대상이 아닌 개인 산출물(메모, 조사 자료, 스크래치 파일, 개인 보고서)은 팀 레포 트리(backend/, frontend/, demo/, docs/, 루트)에 만들지 않고 work/ 아래에 만든다. 팀 레포에는 팀이 리뷰하고 커밋할 파일만 둔다

## 커밋 / PR 사전 승인 (팀 합의)

커밋, push, PR 생성은 사용자에게 보고하고 승인을 받은 뒤에만 실행한다.

- 실행 전에 무엇을 올릴지(대상 파일, 커밋 메시지, PR 제목/본문 요지) 먼저 보고하고 명시적 승인을 기다린다
- 계획(plan) 승인은 작업 내용에 대한 승인이지 커밋/PR 실행 승인이 아니다. 계획에 커밋/PR이 포함되어 있어도 실행 직전에 다시 확인한다
- 로컬 파일 편집, 브랜치 생성/전환, 조회는 이 규칙의 대상이 아니다

## 현재 상태 (중요)

- 기술 스택 확정: 백엔드 Java 25 + Spring Boot 4 + Gradle, 프론트엔드 Next.js, 저장소 PostgreSQL 단일. 세부 라이브러리는 MS2-31, 프론트엔드 상세는 MS2-46에서 정한다
- 브랜치 전략: main 직접 push 금지(모든 변경은 PR로)는 확정, GitHub 브랜치 보호로 강제된다. 네이밍/머지 방식 등 나머지는 개발 환경 구축(MS2-31)에서 합의한다. 정본은 CONTRIBUTING.md
- 문서를 쓸 때 미정 범위를 확정된 것처럼 서술하지 않는다
- 이슈의 최신 상태는 Jira(MS2 프로젝트)에서 확인한다

## 팀과 협업 도구

- 팀: 박성종(팀 리드), 문인호, 양성지 / 멘토: 장시현, 강민준, 남상수 (2026 AI·SW 마에스트로 17기)
- Jira/Confluence: https://asm17-ms2.atlassian.net (Jira 프로젝트 키 MS2, Confluence의 MS2 스페이스)
- Notion: MS2 팀 위키, https://app.notion.com/p/MS2-3af0899b32b881f199ede2a87ac32a30 (어떤 문서를 여기에 둘지는 미정, 아래 "문서 흐름" 참조)
- 팀 GitHub org: https://github.com/asm17-ms2 (meterengine, meterengine-demo, asm-crawling)

## 개발 방법론

빅뱅 설계를 하지 않는다. 얇은 수직 슬라이스 단위로 개발한다.

- 슬라이스 하나는 최소 폭으로 끝-대-끝을 관통한다
- 명세서나 정책 문서를 작성할 때 현재 슬라이스 범위와 미정 범위를 구분해 쓴다

## 문서 흐름

TODO (MS2-116): 문서를 어디에 둘지 미정이다. 규칙의 정본은 `docs/document-rules.md`이며, 새 문서를 만들기 전에 그 파일을 보고 없는 내용이면 먼저 물어본다.

## 출력 형식

- 가운뎃점, 엠 대시, 이모지, 스마트 따옴표를 쓰지 않는다 (공식 고유명사 제외)
- 한영 병기는 고유 제품명 첫 등장 시에만 최소한으로
- AI가 쓴 티가 나는 문구를 지양한다
