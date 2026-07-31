# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 이 저장소에 대해

MeterEngine 제품 모노레포다. 사용량 기반 과금 플랫폼으로, raw usage event 수집 -> 미터링/집계 -> rating -> 인보이스 생성 파이프라인에 국내 PG 연동과 전자세금계산서 발행을 결합하는 것을 목표로 한다.

- `backend/`: 미터링 엔진 API 서버
- `frontend/`: 관리자 화면
- `docs/`: 코드가 원인인 문서 (OpenAPI, ADR, ERD)
- `work/`: 개인 작업 공간 (.gitignore로 제외, 아래 참조)

## 개인 작업 공간 (work/)

- `/work`는 .gitignore로 제외된 개인 공간이다. 팀원 각자 자기 work/를 만들어 자유롭게 쓴다. 개인 계정의 별도 레포로 백업해도 된다 (이 경우 work/ 안에서만 해당 레포의 git 명령을 실행한다)
- 커밋 대상이 아닌 개인 산출물(메모, 조사 자료, 스크래치 파일, 개인 보고서)은 팀 레포 트리(backend/, frontend/, docs/, 루트)에 만들지 않고 work/ 아래에 만든다. 팀 레포에는 팀이 리뷰하고 커밋할 파일만 둔다

## 커밋 / PR 사전 승인 (팀 합의)

커밋, push, PR 생성은 사용자에게 보고하고 승인을 받은 뒤에만 실행한다.

- 실행 전에 무엇을 올릴지(대상 파일, 커밋 메시지, PR 제목/본문 요지) 먼저 보고하고 명시적 승인을 기다린다
- 계획(plan) 승인은 작업 내용에 대한 승인이지 커밋/PR 실행 승인이 아니다. 계획에 커밋/PR이 포함되어 있어도 실행 직전에 다시 확인한다
- 로컬 파일 편집, 브랜치 생성/전환, 조회는 이 규칙의 대상이 아니다

## 현재 상태 (중요)

- 기술 스택 확정: 백엔드 Java 25 + Spring Boot 4 + Gradle, 프론트엔드 Next.js, 저장소 PostgreSQL 단일 (docs/adr/ 0001~0005 참조). 세부 라이브러리는 MS2-31, 프론트엔드 상세는 MS2-46에서 정한다
- 브랜치 전략: main 직접 push 금지(모든 변경은 PR로)는 확정, GitHub 브랜치 보호로 강제된다. 네이밍/머지 방식 등 나머지는 개발 환경 구축(MS2-31)에서 합의한다. 요약은 CONTRIBUTING.md, 정본은 Notion "개발 워크플로" 페이지
- MVP 범위는 MS2-24 정의서 기준으로 진행 중이며 확정본이 아니다. 문서를 쓸 때 미정 범위를 확정된 것처럼 서술하지 않는다
- 이슈의 최신 상태는 Jira(MS2 프로젝트)에서 확인한다

## 팀과 협업 도구

- 팀: 박성종(팀 리드), 문인호, 양성지 / 멘토: 장시현, 강민준, 남상수 (2026 AI·SW 마에스트로 17기)
- Jira/Confluence: https://asm17-ms2.atlassian.net (Jira 프로젝트 키 MS2, Confluence의 MS2 스페이스)
- Notion: MS2 팀 위키, https://app.notion.com/p/d210899b32b883148ab281a902fedf74 (합의가 원인인 문서의 정본, 아래 "문서 흐름" 참조)
- 팀 GitHub org: https://github.com/asm17-ms2 (meterengine, meterengine-demo, asm-crawling)

## 개발 방법론

빅뱅 설계를 하지 않는다. 얇은 수직 슬라이스 단위로 개발한다.

- 슬라이스 하나는 최소 폭으로 끝-대-끝을 관통한다
- 문서(API 명세, ADR, ERD)는 living document다. 슬라이스 착수 시점에 필요한 범위만 확정하고 구현에서 배운 것을 반영해 버전을 올린다. 문서에는 버전과 반영 범위를 명시한다
- 되돌리기 어려운 결정은 첫 슬라이스 전에 확정한다: 이벤트 멱등키(transaction_id), occurred_at/received_at 시각 필드 구분, raw 이벤트 불변성(정정은 이벤트로), 자금 미경유 구조
- 명세서나 정책 문서를 작성할 때 현재 슬라이스 범위와 미정 범위를 구분해 쓴다

## 문서 흐름

- 코드가 원인인 문서는 이 레포 `docs/`에 둔다: `docs/api/`(OpenAPI), `docs/adr/`(아키텍처 결정), `docs/erd/`(도메인 모델). 코드와 같은 PR에서 리뷰한다
- 합의가 원인인 문서(제품 정의, 슬라이스 시퀀스, 정책, 회의록)는 Notion의 MS2 팀 위키가 정본이다 (주소는 위 "팀과 협업 도구" 참조)
- 정책의 정밀 규칙(KRW 절사 방식 등)은 레포에 복사하지 않는다. 테스트가 Notion 정책 항목 번호를 참조해 고정한다. 정책 변경 순서: Notion 합의 -> 테스트/코드 반영
- `docs/api/openapi.yaml`은 수기 정본, `docs/api/generated/openapi.yaml`은 springdoc 생성물이다. 생성물은 손으로 고치지 않는다 (다음 빌드에서 덮어써진다)
- backend의 컨트롤러, DTO, OpenAPI 어노테이션을 바꿨으면 `cd backend && ./gradlew build`로 생성물을 재생성하고 **소스와 같은 커밋에 포함한다.** 빠뜨리면 CI backend job이 실패한다. 생성물을 커밋해 두는 목적은 PR diff에 API 표면 변화를 드러내는 것이다 (상세는 `docs/api/README.md`의 "구현 스냅샷")

## 출력 형식

- 가운뎃점, 엠 대시, 이모지, 스마트 따옴표를 쓰지 않는다 (공식 고유명사 제외)
- 한영 병기는 고유 제품명 첫 등장 시에만 최소한으로
- AI가 쓴 티가 나는 문구를 지양한다
