# MeterEngine

사용량 기반 과금(usage-based billing) 플랫폼. raw usage event 수집, 미터링과 rating 집계, 인보이스 생성까지를 자체 엔진으로 처리하고, 그 위에 국내 PG 결제 연동과 전자세금계산서 발행을 결합하는 것을 목표로 한다.

## 저장소 구조

| 경로 | 내용 |
| --- | --- |
| `backend/` | 미터링 엔진 API 서버. Java 25 + Spring Boot 4 + Gradle |
| `frontend/` | 관리자 화면. Next.js + TypeScript |
| `demo/` | 수집-조회 데모/검증 CLI. Python 3.9+, 표준 라이브러리만 쓴다 |
| `deploy/` | 운영 배포 구성. compose, Caddy, 배포 스크립트 |
| `docs/` | 무엇을 둘지 미정 (`docs/document-rules.md`) |
| `work/` | 개인 작업 공간. .gitignore로 제외되며 각자 만들어 쓴다 (CLAUDE.md 참조) |

## 개발 방식

빅뱅 설계(명세와 정책을 전부 확정한 뒤 개발 시작)를 하지 않는다. 얇은 수직 슬라이스 단위로 개발한다. 슬라이스 하나는 최소 폭으로 끝-대-끝을 관통한다 (예: 이벤트 수집 -> 집계 -> rating -> draft 인보이스).

## 문서 위치 규칙

코드를 고칠 때 같이 고쳐야 하는 문서는 레포에, 논의해서 정하는 것은 Notion MS2 팀 위키에 둔다. 규칙의 정본은 [`docs/document-rules.md`](docs/document-rules.md)이며, 새 문서를 만들기 전에 그 파일을 본다.

레포 안 정본은 이렇다. API 계약은 `backend/openapi.yaml`, 브랜치와 PR 규칙은 [`CONTRIBUTING.md`](CONTRIBUTING.md), 각 디렉터리의 실행법과 구조는 그 디렉터리의 README다.

## 시작하기

사전 준비: JDK 25, Node.js 24+, Docker Desktop (Compose 포함).

```
# backend + PostgreSQL (DB는 자동 기동)
cd backend && ./gradlew bootRun

# frontend
cd frontend && corepack enable pnpm && pnpm install && pnpm dev

# 데모/검증 CLI (백엔드가 떠 있어야 한다, 레포 루트에서)
python3 demo/meterdemo.py --help

# DB만 필요할 때
docker compose up -d
```

MS2-31 완료 조건이던 "클론 후 한 명령으로 로컬 실행"은 backend + DB 기준 `./gradlew bootRun` 한 명령으로 충족한다 (DB가 자동 기동된다). frontend는 별도 명령으로 실행한다.

자세한 내용은 `backend/README.md`, `frontend/README.md`, `demo/README.md` 참조. API 계약의 정본은 `backend/openapi.yaml`이다 (컨트롤러와 DTO에서 자동 생성한다). CI는 `.github/workflows/ci.yml`에서 PR마다 backend 빌드/테스트와 frontend lint/빌드를 실행한다. main에 머지되면 `.github/workflows/cd.yml`이 배포까지 한다.

## 배포

https://meterengine.com 에 배포한다. AWS EC2 한 대에서 Caddy(HTTPS와 경로 분배), 백엔드,
프론트엔드 컨테이너가 돌고, DB는 RDS PostgreSQL이다. 이미지는 ECR에 있고 태그는 배포한
커밋의 git SHA다.

구성, 배포 절차, 서버 접속(SSH가 아니라 SSM이다)은 `deploy/README.md`에 있다.

## 진행 상태

이슈 진행은 Jira MS2 프로젝트에서 관리한다: https://asm17-ms2.atlassian.net

## License

Copyright (c) 2026 asm17-ms2 team. All rights reserved.

이 저장소는 포트폴리오 열람 목적으로만 공개되어 있습니다.
사전 서면 동의 없이 소스 코드의 복제, 수정, 재배포, 상업적 이용을 금지합니다.

This repository is publicly visible for portfolio purposes only.
Unauthorized copying, modification, redistribution, or commercial use
of this source code is strictly prohibited without prior written consent.
