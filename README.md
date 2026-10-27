# MeterEngine

사용량 기반 과금(usage-based billing) 플랫폼. raw usage event 수집, 미터링과 rating 집계, 인보이스 생성까지를 자체 엔진으로 처리하고, 그 위에 국내 PG 결제 연동과 전자세금계산서 발행을 결합하는 것을 목표로 한다.

## 저장소 구조

| 경로 | 내용 |
| --- | --- |
| `backend/` | 미터링 엔진 API 서버. Java 25 + Spring Boot 4 + Gradle |
| `frontend/` | 관리자 화면. Next.js + TypeScript |
| `demo/` | 데모와 검증 CLI, Claude Code 사용량 브리지(Python 3.9+, 표준 라이브러리), TypeScript SDK와 테스트 웹(Node 24, pnpm) |
| `deploy/` | 배포 구성. compose, Caddy, 배포 스크립트 |
| `measurements/` | 측정과 측정에 쓰는 SUT |

## 시작하기

- 사전 준비: JDK 25, Node.js 24+, Docker Desktop (Compose 포함).
  - `demo/console.py`를 쓸 때만 [uv](https://docs.astral.sh/uv/)가 추가로 필요하다.

```
# backend + PostgreSQL (DB는 자동 기동)
cd backend && ./gradlew bootRun

# 백엔드 확인 (로컬 live 키)
curl -s http://localhost:8080/v1/customers -H 'Authorization: Bearer me_live_QouOFuCiJyAbC0VGK1ltUCkG4IZM2LtA'

# frontend
cd frontend && corepack enable pnpm && pnpm install && pnpm dev

# 데모/검증 CLI (백엔드가 떠 있어야 한다, 레포 루트에서)
python3 demo/meterdemo.py --help

# Claude Code 사용량을 실제로 흘려보내는 브리지 (설정은 demo/README.md 참조)
python3 demo/otel_bridge.py --help
uv run demo/console.py            # 같은 일을 화면으로

# SDK로 이벤트와 웹훅을 시험하는 테스트 웹 (http://127.0.0.1:3100, 설정은 demo/README.md 참조)
cd demo && corepack enable pnpm && pnpm install && pnpm --filter @meterengine/demo-web dev

# DB만 필요할 때
docker compose up -d
```

- 위 키는 `bootRun`이 로컬 DB에만 넣는 키이고, 로컬 계정과 키 목록은 `backend/README.md`의 "인증"에 있다.
- 자세한 내용은 `backend/README.md`, `frontend/README.md`, `demo/README.md` 참조.
  - API 계약의 정본은 `backend/openapi.yaml`이다 (컨트롤러와 DTO에서 자동 생성한다).

## 배포

- 배포 구성과 절차는 `deploy/README.md`에 있다.

## 기여

- 규칙은 [`CONTRIBUTING.md`](CONTRIBUTING.md)에서 시작한다.
