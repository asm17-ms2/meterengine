# MeterEngine

MeterEngine은 서비스 사용량을 고객별 월 청구액으로 계산하는 과금 플랫폼입니다. 예를 들어 AI 서비스가 고객별 토큰 사용량을 보내면, 등록한 단가에 따라 청구서를 발행하고 토스페이먼츠로 카드 결제를 요청할 수 있습니다.

서비스에 연동하려면 [빠른 시작 가이드](https://docs.meterengine.com/quickstart)를 따라 테스트 환경에서 첫 사용량 이벤트를 보내 보세요. [OpenAB 예제](demo/llm-service/README.md)에서는 AI 서비스가 사용량을 보내고 고객에게 청구 내역을 보여 주는 코드를 볼 수 있습니다.

## 시작하기

로컬에서 실행하려면 JDK 25와 Node.js 24를 설치하고 Docker Desktop(Compose 포함)을 실행하세요. 두 터미널을 각각 저장소 루트에서 엽니다.

**첫 번째 터미널: API 서버.** PostgreSQL도 함께 시작됩니다.

```bash
cd backend
./gradlew bootRun
```

**두 번째 터미널: 관리자 콘솔.**

```bash
cd frontend
corepack enable pnpm
pnpm install
pnpm dev
```

[로컬 콘솔](http://localhost:3000)에서 [로컬 계정](backend/README.md#로컬-계정과-키)으로 로그인해 고객 목록을 확인하세요. [API 문서](http://localhost:8080/scalar)는 백엔드만 실행해도 열립니다.

## 개발 문서

- [백엔드](backend/README.md)와 [OpenAPI 명세](backend/openapi.yaml): 사용량 수집, 요금 계산, 청구 API
- [프론트엔드](frontend/README.md): 관리자 콘솔의 화면과 개발 방법
- [연동 예제와 도구](demo/README.md): TypeScript SDK, CLI, Claude Code 사용량 연동
- [배포 안내](deploy/README.md), [성능 측정](measurements/), [기여 안내](CONTRIBUTING.md)
