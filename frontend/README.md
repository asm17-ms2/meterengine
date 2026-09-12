# frontend

MeterEngine 관리자 콘솔. Next.js App Router + TypeScript.

- Next.js 16, React 19, TypeScript 5 (`package.json`).
- 패키지 매니저는 pnpm이고 버전은 `package.json`의 `packageManager`가 정본이다.
- 데이터를 읽는 화면은 Server Component에서 그리고, 상태 관리 라이브러리는 쓰지 않는다.
  - 조회 조건은 쿼리스트링(`?month=`, `?page=`).
  - 접기/펼치기 같은 화면 안의 상태만 Client Component.
- API 계약의 정본은 `backend/openapi.yaml`이다 (백엔드를 띄우지 않고 이 파일로 읽는다).
- 코드와 문서 작성 규칙은 `docs/contributing/`에 있다.

## 사전 준비

- Node.js 24 (`Dockerfile`의 빌드 이미지가 `node:24-alpine`).
- 백엔드는 띄우지 않아도 `pnpm dev`와 `pnpm build`가 돈다. 화면에 데이터를 채우려면 `backend/README.md`의 실행 절차가 필요하다.

```bash
corepack enable pnpm
```

## 실행

```bash
cd frontend
pnpm install
pnpm dev
```

- http://localhost:3000 에서 열린다. `/`는 `/usage`로 리다이렉트한다.
- 다른 기계에서 이 dev 서버를 볼 수 있다 (`next.config.ts`의 `allowedDevOrigins`).
- 개발 모드에서만 사이드바 하단에 표 상태(정상, 빈 상태, 로딩, 에러) 스위치가 뜬다.
  - `?state=`만 바꾸며 프로덕션 빌드에서는 제거된다.

## 빌드와 lint

```bash
cd frontend
pnpm lint
pnpm build
```

- `pnpm build`는 백엔드가 꺼져 있어도 통과한다. CI가 그 상태로 돌린다.
  - 조회 fetch가 전부 `cache: "no-store"`라 빌드 타임에 나가지 않는다 (`src/lib/api/client.ts`).
- 프로덕션 실행은 `pnpm start`가 아니라 standalone 서버다 (`next.config.ts`의 `output: "standalone"`). 아래 "컨테이너 이미지" 참조.

## 설정

- 값은 `frontend/.env.local`에 둔다 (커밋 대상 아님).
- 세 변수 모두 `src/lib/config.ts`에 기본값이 있어서 로컬 개발은 `.env.local` 없이 돌아간다.
- 전부 서버 사이드에서만 읽는다. `NEXT_PUBLIC_` 접두사를 붙이지 않는다 (붙이면 클라이언트 번들에 들어간다).

| 환경변수 | 용도 | 기본값 |
| --- | --- | --- |
| `METERENGINE_API_BASE_URL` | 백엔드 API 주소 | `http://localhost:8080` |
| `METERENGINE_ORGANIZATION_ID` | 조회할 도입사. `X-Organization-Id` 헤더로 나간다 | `backend/src/main/resources/db/migration/R__seed.sql`의 데모 도입사 |
| `METERENGINE_ORGANIZATION_NAME` | 상단 바에 표시할 도입사 이름 | `데모 도입사` |

- 운영에서는 `deploy/compose.prod.yml`이 세 변수를 필수로 걸어 주입한다. 값이 없으면 컨테이너가 뜨지 않는다.
- 운영의 백엔드 주소는 공개 도메인이 아니라 컨테이너 네트워크 안의 `http://backend:8080`이다

## 컨테이너 이미지

배포용 실행 이미지는 `Dockerfile`이 만든다. 로컬 개발은 이 이미지를 쓰지 않는다.

```bash
cd frontend
docker build -t meterengine-frontend .
docker run -p 3000:3000 \
  -e METERENGINE_API_BASE_URL=http://host.docker.internal:8080 \
  meterengine-frontend
```

- standalone 출력에는 정적 자산이 없어 `Dockerfile`이 `.next/static`과 `public`을 따로 복사한다. 빼면 화면은 뜨는데 CSS와 JS가 404다.
- 백엔드 주소와 도입사 식별자는 이미지에 굽지 않고 런타임 환경변수로 받는다.
  - 주지 않으면 `src/lib/config.ts`의 기본값으로 떨어지고 오류가 나지 않는다.

## 백엔드 연동

```
브라우저 --> Next 서버(Node) --> Spring(:8080)
```

- 브라우저는 백엔드(:8080)를 직접 호출하지 않는다. 쓰기 화면도 같다 (각 라우트의 `actions.ts`가 Server Action).
- 모든 백엔드 호출은 Server Component에서 서버 사이드 fetch로 나간다 (`src/lib/api/client.ts`).
  - `X-Organization-Id` 헤더도 여기서 붙어서 클라이언트에 가지 않는다.
- `next.config.ts`의 `rewrites()`로 프록시하지 않는다.
- `src/lib/config.ts`는 `server-only`를 임포트한다. Client Component에서 쓰면 빌드가 깨진다.

## 구조

| 경로 | 내용 |
| --- | --- |
| `src/app/(console)/` | 라우트. 화면마다 `page.tsx`, 쓰기 화면은 `actions.ts`, `state.ts` |
| `src/components/` | 화면별 컴포넌트와 공용 셸, 표, 다이얼로그 |
| `src/lib/api/` | 백엔드 호출. `client.ts`가 공용 fetch |
| `src/lib/config.ts` | 서버 사이드 설정 |
| `src/styles/` | `modernist.css`(디자인 시스템 이식본), `console.css`(콘솔 전용 클래스) |
| `src/app/globals.css` | Tailwind 임포트와 테마 조정 |

스타일 규칙은 다음과 같다.

- 컴포넌트 클래스(`.btn`, `.input`, `.tag`, `.nav`, `.card`, `.table`, `.dialog`)는 `modernist.css`의 것을 그대로 쓴다.
  - `modernist.css`는 원본과 diff를 유지해야 해서 폰트 토큰 두 줄 말고는 수정하지 않는다.
- Tailwind는 레이아웃 유틸(`flex`, `grid`, `gap-*`)로만 쓴다.
  - 색, 모서리, 그림자 네임스페이스를 `globals.css`에서 지웠다. `bg-neutral-200`, `rounded-lg` 같은 클래스는 CSS가 생성되지 않는다.
  - 색, 모서리, 그림자는 modernist 토큰(`var(--color-accent)` 등)을 직접 쓴다.
- 폰트는 라틴과 숫자가 Archivo, 한글이 Pretendard다.
  - Archivo는 `next/font/google`로 자체 호스팅한다.
  - Pretendard는 `cdn.jsdelivr.net`에서 런타임에 받는다 (`@v1.3.9` 고정). CSP를 걸거나 폐쇄망에 배포하면 폰트를 레포로 가져와야 한다.

## 화면

| 경로 | 화면 | 백엔드 |
| --- | --- | --- |
| `/events` | 이벤트 로그 (페이지 나누기, 상세 드로어) | `GET /v1/events` |
| `/usage` | 사용량 집계 (고객 그룹 + 미터 자식 행) | `GET /v1/usage` |
| `/billing` | 청구 예정액 | `GET /v1/invoices/draft` |
| `/customers` | 고객 관리 (검색, 등록/수정 다이얼로그, 삭제) | `GET/POST /v1/customers`, `PUT/DELETE /v1/customers/{id}` |
| `/billable-metrics` | 미터 관리 (이름 검색, 등록/수정 다이얼로그, 삭제) | `GET/POST /v1/billable-metrics`, `PUT/DELETE /v1/billable-metrics/{code}` |
