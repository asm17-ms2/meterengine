# frontend

MeterEngine 관리자 콘솔. Next.js App Router + TypeScript.

- Next.js 16, React 19, TypeScript 5, Ant Design 6 (`package.json`).
- 패키지 매니저는 pnpm이고 버전은 `package.json`의 `packageManager`가 정본이다.
- 데이터는 Server Component에서 읽고, antd를 그리는 조각은 Client Component다. 상태 관리 라이브러리는 쓰지 않는다.
  - 조회 조건은 쿼리스트링이다 (아래 "실행").
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

조회 조건은 다음 쿼리스트링으로 받는다.

| 파라미터 | 뜻 |
| --- | --- |
| `?month=` | 월 단위 화면의 조회 달(`yyyy-MM`). 없으면 KST 이번 달. 인보이스는 `?month=all`이 전체 기간 |
| `?q=` | 고객, 미터, 가격 화면의 검색어 |
| `?page=`, `?size=` | 페이지(0부터)와 페이지 크기(20, 50, 100) |
| `?customer_id=`, `?type=` | 고객 필터와 이벤트 type 필터 |
| `?sort=`, `?order=` | 정렬 열과 방향(`asc`, `desc`) |

- `?month=`는 월 단위 화면이, `?q=`는 고객, 미터, 가격 화면이 늘 읽는다. 그 밖의 조건과 `?month=all`은 아래 "목록 화면 능력 표"에서 켜진 화면만 읽는다.

## 빌드와 lint

```bash
cd frontend
pnpm lint
pnpm build
```

- `pnpm build`는 백엔드가 꺼져 있어도 통과한다. CI가 그 상태로 돌린다.
  - 조회 fetch는 `cache: "no-store"`이거나 `searchParams`를 읽은 뒤에 나가서 빌드 타임에 나가지 않는다 (`src/lib/api/client.ts`).
- 프로덕션 실행은 `pnpm start`가 아니라 standalone 서버다 (`next.config.ts`의 `output: "standalone"`). 아래 "컨테이너 이미지" 참조.

## 설정

- 값은 `frontend/.env.local`에 둔다 (커밋 대상 아님).
- 모든 변수에 `src/lib/config.ts`의 기본값이 있어서 로컬 개발은 `.env.local` 없이 돌아간다.
- 전부 서버 사이드에서만 읽는다. `NEXT_PUBLIC_` 접두사를 붙이지 않는다 (붙이면 클라이언트 번들에 들어간다).

| 환경변수 | 용도 | 기본값 |
| --- | --- | --- |
| `METERENGINE_API_BASE_URL` | 백엔드 API 주소 | `http://localhost:8080` |
| `METERENGINE_API_KEY` | 백엔드 호출에 쓰는 API 키. `Authorization: Bearer` 헤더로 나가고, 이 키의 도입사 데이터를 본다 | `backend/src/main/resources/db/local/R__local_credentials.sql`의 live 키 `me_live_QouOFuCiJyAbC0VGK1ltUCkG4IZM2LtA` (데모 도입사, admin 스코프) |
| `METERENGINE_ORGANIZATION_ID` | 셸 표시용 도입사 식별자. 백엔드 호출에는 쓰지 않는다 | `backend/src/main/resources/db/migration/R__seed.sql`의 데모 도입사 |
| `METERENGINE_ORGANIZATION_NAME` | 셸 표시용 도입사 이름. 상단 바에 나온다 | `데모 도입사` |
| `METERENGINE_PUBLIC_ORIGIN` | 공개 인보이스 조회와 결제 재시도를 받을 origin. 예: `https://meterengine.com` | 없음. localhost에서 온 요청만 받는다 |

- 셸 표시용 변수는 API 키가 속한 도입사와 같은 값으로 맞춘다. 다르면 상단 바와 화면의 데이터가 어긋난다.
- 배포할 때는 `deploy/compose.prod.yml`이 API 키와 셸 표시용 변수를 필수로 걸어 주입한다. 값이 없으면 컨테이너가 뜨지 않는다.
- `METERENGINE_PUBLIC_ORIGIN`은 배포할 때 `https://<SITE_DOMAIN>`으로 주입된다.
- 배포 환경의 백엔드 주소는 공개 도메인이 아니라 컨테이너 네트워크 안의 Caddy 내부 사이트 `http://caddy:8081`이다. Caddy가 헬스체크를 통과한 백엔드 색으로 보낸다.

## 컨테이너 이미지

배포용 실행 이미지는 `Dockerfile`이 만든다. 로컬 개발은 이 이미지를 쓰지 않는다.

```bash
cd frontend
docker build -t meterengine-frontend .
docker run -p 3000:3000 \
  -e METERENGINE_API_BASE_URL=http://host.docker.internal:8080 \
  meterengine-frontend
```

- standalone 출력에는 정적 자산이 없어 `Dockerfile`이 `.next/static`을 따로 복사한다. 빼면 화면은 뜨는데 CSS와 JS가 404다.
- 백엔드 주소, API 키, 셸 표시용 도입사 값은 이미지에 굽지 않고 런타임 환경변수로 받는다.
  - 주지 않으면 `src/lib/config.ts`의 기본값으로 떨어지고 오류가 나지 않는다.

## 백엔드 연동

```
브라우저 --> Next 서버(Node) --> Spring(:8080)
```

- 브라우저는 백엔드(:8080)를 직접 호출하지 않는다. 쓰기 화면도 같다 (각 라우트의 `actions.ts`가 Server Action).
- 모든 백엔드 호출은 Server Component에서 서버 사이드 fetch로 나간다 (`src/lib/api/client.ts`).
  - API 키를 담은 `Authorization: Bearer` 헤더도 여기서 붙어서 클라이언트에 가지 않는다.
- 필터 선택지(고객, 미터 목록)만 Next 데이터 캐시에 잠깐 둔다. 캐시 키에 `Authorization` 헤더가 들어가 API 키의 도입사끼리 섞이지 않는다.
  - 콘솔에서 고객이나 미터를 바꾸면 Server Action이 `updateTag`로 바로 비운다. 콘솔 밖에서 바꾼 값은 `CACHE_REVALIDATE_SECONDS`(`src/lib/api/client.ts`)가 지난 뒤 첫 조회 다음부터 보인다.
- `next.config.ts`의 `rewrites()`로 프록시하지 않는다.
- `src/lib/config.ts`는 `server-only`를 임포트한다. Client Component에서 쓰면 빌드가 깨진다.

## 구조

| 경로 | 내용 |
| --- | --- |
| `src/app/(console)/` | 라우트. 화면마다 `page.tsx`, 쓰기 화면은 `actions.ts`, `state.ts` |
| `src/app/health/` | `deploy.sh`가 서버 안에서 부르는 준비 확인. 백엔드를 부르지 않고 `ok`만 돌려준다. Caddy가 바깥에서는 404로 막는다 |
| `src/components/` | 화면별 컴포넌트 |
| `src/components/shell/` | antd Layout 틀, 메뉴, 개발 모드 스위치, 테마와 로케일을 거는 `AppProviders` |
| `src/components/screen/` | 화면 공용 조각: 제목 줄, 툴바, 카드, 각주, 빈 상태, 오류, 스켈레톤, 링크 버튼, 월 선택, 검색과 필터, 페이지 |
| `src/components/table/` | 표 공용 조각: 접기 상태, 조합 칸, 미터 라벨, 숫자 칸, 정렬, 표 공통값 |
| `src/lib/api/` | 백엔드 호출. `client.ts`가 공용 fetch |
| `src/lib/config.ts` | 서버 사이드 설정 |
| `src/app/layout.tsx` | antd 스타일을 SSR HTML에 넣는 `AntdRegistry` |
| `src/app/globals.css` | 기본 여백, 글꼴 다듬기, 화면 폭에 따른 본문 여백 |

화면 컴포넌트의 접미사는 다음과 같다.

- `Section`은 데이터를 기다렸다가 결과에 따라 그리는 바깥 경계다.
- `Screen`은 데이터가 도착한 뒤 `Section`이 그리는 화면이다.
- `Loading`은 `Section`이 기다리는 동안 그 자리에 보이는 것이다.
- `Frame`은 `Section`의 오류 상태와 `Loading`이 함께 쓰는 틀이다.
- `Meta`는 화면 제목 아래에 붙는 요약이다.

스타일 규칙은 다음과 같다.

- 화면은 antd 6 컴포넌트로 그린다. 스타일은 antd 런타임 CSS-in-JS가 만들고, 프로젝트 CSS는 `globals.css`뿐이다.
- 테마는 antd 기본값이다. 바꾸는 값은 `AppProviders.tsx`에만 둔다. 토큰은 `theme.useToken()`의 `token.paddingLG`처럼 멤버 접근으로 읽는다.
- antd는 `"use client"` 파일에서만 import한다. Server Component는 직렬화할 수 있는 prop만 넘겨 그 래퍼를 그린다.
- 쓰기 폼은 antd Form의 `onFinish`에서 `actions.ts`의 Server Action(`useActionState`)을 부른다. Form rules의 문구는 서버 검증 문구와 같게 둔다.
- 페이지를 옮기는 버튼은 `LinkButton`, 토스트는 `App.useApp()`의 `message`를 쓴다.
- 글꼴은 시스템 글꼴이다. 웹폰트를 받지 않는다.

## 목록 화면 능력 표

- `src/lib/api/capabilities.ts`가 백엔드 오퍼레이션마다 받는 조회 파라미터(페이지, 페이지 크기, 정렬, 검색, 고객과 type 필터, 전체 기간)를 적는다.
- 조회 컨트롤은 이 표를 보고 켜진다. 켜진 파라미터만 서버로 보내고(`src/lib/list-query.ts`), 꺼진 정렬과 검색은 불러온 행 안에서 한다. 다만 페이지가 켜진 목록은 한 페이지 안의 정렬이 전체 순서로 읽히지 않게 정렬을 숨긴다. 꺼진 페이지 크기와 필터 컨트롤은 숨긴다.
- 조회 컨트롤 줄은 모든 목록 화면이 공용 `ListControls`로 그린다. 검색창은 고객, 미터, 가격 화면만 넣는다.
- 백엔드가 파라미터를 새로 받으면 그 PR에서 표의 값을 `true`로 바꾼다. 백엔드가 다른 이름을 쓰면 해당 API 함수에서 이름을 바꿔 보낸다.
- 표의 값만 바꿔서는 열리지 않는 경우가 셋 있다.
  - `q`: 이벤트, 사용량, 청구 예정액, 인보이스 화면에는 검색창이 없다. 켜려면 그 화면의 `ListControls`에 검색창을 넣는다.
  - `month_all`: 인보이스 화면만 받는다. 다른 월 화면에서 켜려면 월 선택과 이전 달 계산을 함께 고친다.
  - `page`: 고객, 미터, 청구 예정액 목록은 다른 화면이 전체 목록으로 쓴다(필터 선택지, 가격 화면의 미터 이름, 확정 요약). 켜려면 그 호출을 `listAllInvoices`처럼 모든 페이지를 모으게 바꾼다. 검색이 꺼진 화면에서 켜면 검색이 한 페이지 안에서만 되므로 `q`도 함께 켠다.
- 표는 `backend/openapi.yaml`과 같아야 한다. 백엔드 조회 파라미터를 바꾸는 PR은 이 파일도 본다.

## 화면

| 경로 | 화면 |
| --- | --- |
| `/events` | 이벤트 로그 (페이지 나누기, 페이지 크기, 고객과 type 필터, 상세 드로어) |
| `/usage` | 사용량 집계 (고객 행 열 정렬, 고객 그룹 + 미터와 조합별 자식 행. 조합이 여럿인 미터는 소계 줄로 접어 두고 펼치면 조합별 행) |
| `/billing` | 청구 예정액 (고객 행 열 정렬, 고객 그룹 + 미터와 조합별 라인, 조합이 여럿인 미터는 소계 줄로 접어 둠, 단가 없는 사용량은 고객마다 "기타 (단가 없음)" 줄로 접어 두고 금액에 넣지 않음, 확정 전 고객만, 확인 창을 거쳐 사용량이 있는 고객의 그 달 인보이스 일괄 확정. 확정 대상에 단가 없는 사용량이 있으면 확인 창이 알린다. 확정된 달은 확정 건수와 시각을 안내하고 0원 고객 표는 접어 둔다) |
| `/invoices` | 확정 인보이스 목록 (달 선택, 전체 기간, 고객 필터, 인보이스 행 열 정렬, 고객 줄을 펼치면 미터와 조합별 라인, 공급가액, 세액. 조합이 여럿인 미터는 소계 줄로 접어 둠) |
| `/customers` | 고객 관리 (검색, 검색어 URL 유지, 열 정렬, 등록/수정 다이얼로그, 삭제. 청구 이메일 열과 칸이 있고, 수정할 때는 지금 청구 이메일을 채워 두어 이름만 고쳐도 그대로다) |
| `/billable-metrics` | 미터 관리 (이름 검색, 검색어 URL 유지, 열 정렬, 차원 키, 등록/수정 다이얼로그, 삭제) |
| `/billable-metric-prices` | 미터별 가격 (이름/코드 검색, 검색어 URL 유지, 열 정렬, 미터마다 차원 키와 조합별 단가, 단가 등록 다이얼로그, 확인 창을 거친 삭제) |

- 화면이 부르는 백엔드 오퍼레이션은 `src/lib/api/`에 있다.
