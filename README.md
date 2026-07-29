# MeterEngine

사용량 기반 과금(usage-based billing) 플랫폼. raw usage event 수집, 미터링과 rating 집계, 인보이스 생성까지를 자체 엔진으로 처리하고, 그 위에 국내 PG 결제 연동과 전자세금계산서 발행을 결합하는 것을 목표로 한다.

## 저장소 구조

| 경로 | 내용 |
| --- | --- |
| `backend/` | 미터링 엔진 API 서버. 기술 스택은 ADR 확정 후 초기화한다 |
| `frontend/` | 관리자 화면. 기술 스택은 ADR 확정 후 초기화한다 |
| `docs/` | 코드가 원인인 문서: OpenAPI 명세, ADR, ERD |
| `work/` | 개인 작업 공간. .gitignore로 제외되며 각자 만들어 쓴다 (CLAUDE.md 참조) |

## 개발 방식

빅뱅 설계(명세와 정책을 전부 확정한 뒤 개발 시작)를 하지 않는다. 얇은 수직 슬라이스 단위로 개발한다.

- 슬라이스 하나는 최소 폭으로 끝-대-끝을 관통한다 (예: 이벤트 수집 -> 집계 -> rating -> draft 인보이스)
- 문서(API 명세, ADR, ERD)는 living document다. 슬라이스 착수 시점에 그 슬라이스에 필요한 범위만 확정하고, 구현에서 배운 것을 반영해 버전을 올린다
- 단, 되돌리기 어려운 결정은 첫 슬라이스 전에 확정한다: 이벤트 멱등키(transaction_id), occurred_at/received_at 시각 필드, raw 이벤트 불변성, 자금 미경유 구조

## 문서 위치 규칙

무엇이 바뀔 때 문서가 바뀌는지로 나눈다.

- 코드가 원인인 문서(OpenAPI, ADR, ERD): 이 레포의 `docs/`. 코드와 같은 PR에서 리뷰한다
- 합의가 원인인 문서(제품 정의, 슬라이스 시퀀스, 정책, 회의록): Notion의 MS2 팀 위키가 정본이다
- 정책의 정밀 규칙(절사 방식 등)은 레포에 복사하지 않는다. 테스트가 Notion 정책 항목 번호를 참조해 고정한다

## 시작하기

기술 스택이 ADR(MS2-28)에서 확정된 뒤 로컬 실행 방법을 여기에 작성한다. 목표는 클론 후 한 명령으로 로컬 실행(MS2-31 완료 조건)이다.

## 진행 상태

이슈 진행은 Jira MS2 프로젝트에서 관리한다: https://asm17-ms2.atlassian.net

## License

Copyright (c) 2026 asm17-ms2 team. All rights reserved.

이 저장소는 포트폴리오 열람 목적으로만 공개되어 있습니다.
사전 서면 동의 없이 소스 코드의 복제, 수정, 재배포, 상업적 이용을 금지합니다.

This repository is publicly visible for portfolio purposes only.
Unauthorized copying, modification, redistribution, or commercial use
of this source code is strictly prohibited without prior written consent.
