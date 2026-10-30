# frontend

MeterEngine 관리자 콘솔. Next.js App Router + TypeScript.

- Next.js 16, React 19, TypeScript 5, Ant Design 6 (`package.json`).
- 패키지 매니저는 pnpm이고 버전은 `package.json`의 `packageManager`가 정본이다.
- 데이터는 Server Component에서 읽고, antd를 그리는 조각은 Client Component다. 상태 관리 라이브러리는 쓰지 않는다.
  - 조회 조건은 쿼리스트링이다 (아래 "실행").
- API 계약은 `backend/openapi.yaml`에서 확인한다. 로그인 세션으로 도입사를 선택하고, 도입사 서버는 별도 API 키로 같은 조회 API를 쓴다.
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

- http://localhost:3000 의 `/`는 로그인 여부와 관계없이 서비스 소개를 보여 준다.
- 다른 기계에서 이 dev 서버를 볼 수 있다 (`next.config.ts`의 `allowedDevOrigins`).
- 개발 모드에서는 기존 목록의 상태를 `?state=empty`, `?state=loading`, `?state=error`로 바꿔 본다. 프로덕션 빌드에서는 무시된다.
- 로그인 세션이 없으면 `src/proxy.ts`가 공개 화면(서비스 소개, 로그인, 가입, 고객 조회 링크 등) 밖의 경로를 로그인 화면으로 보낸다.

조회 조건은 다음 쿼리스트링으로 받는다.

| 파라미터 | 뜻 |
| --- | --- |
| `?month=` | 월 단위 화면의 조회 달(`yyyy-MM`). 없으면 KST 이번 달. 인보이스는 `?month=all`이 전체 기간 |
| `?q=` | 검색을 지원하는 업무 목록의 검색어 |
| `?page=`, `?size=` | 페이지(0부터)와 페이지 크기(20, 50, 100) |
| `?customer_id=`, `?type=` | 고객 필터와 이벤트 type 필터 |
| `?sort=`, `?order=` | 정렬 열과 방향(`asc`, `desc`) |

- 각 화면에서 지원하는 조건만 BE로 보낸다. 날짜/시간 범위, 관리 상태, 결제 상태와 금액 조건은 해당 화면의 컨트롤을 따른다.

## 빌드와 lint

```bash
cd frontend
pnpm lint
pnpm typecheck
pnpm test:unit
pnpm build
```

- `pnpm test:unit`은 `e2e/*.test.mjs` 단위 검사다. 백엔드와 브라우저가 필요 없다.

- `pnpm build`는 백엔드가 꺼져 있어도 통과한다. CI가 그 상태로 돌린다.
  - 인증 쿠키와 `cache: "no-store"` 조회는 요청 시점에 읽는다 (`src/lib/auth/session.ts`, `src/lib/api/transport.ts`).
- 프로덕션 실행은 `pnpm start`가 아니라 standalone 서버다 (`next.config.ts`의 `output: "standalone"`). 아래 "컨테이너 이미지" 참조.

## 설정

`frontend/.env.local`의 `METERENGINE_API_BASE_URL`로 백엔드 주소를 정한다. 기본값은 `http://localhost:8080`이며 서버에서만 읽는다. 배포 시 컨테이너 네트워크 주소를 지정한다.

운영 compose는 `METERENGINE_PUBLIC_ORIGIN`에 `https://<SITE_DOMAIN>`을 주입한다. 외부 고객에게 인보이스 조회 링크를 제공할 때는 런타임 `METERENGINE_PUBLIC_ORIGIN`을 공개 콘솔 origin으로 지정한다. 예: `https://console.example.com`. 프록시는 해당 Host를 보존해야 한다. 공개 조회 중계는 이 origin과 Host를 검사하며 `X-Forwarded-*`는 신뢰하지 않는다. 설정이 없으면 localhost, 127.0.0.1과 ::1의 같은 origin 요청만 허용한다.

도입사와 표시 이름은 로그인 세션에서 읽는다. 기존 `METERENGINE_ORGANIZATION_ID`, `METERENGINE_ORGANIZATION_NAME`은 사용하지 않는다.

상단 업무 환경은 URL의 `environment=test|live`와 API 환경 헤더로 유지한다. 767px 이하에서는 헤더에 업무 환경만 두고 시연 안내, 권한 범위와 새로 조회, 로그아웃은 메뉴 서랍에 둔다. 기존 데이터는 별도 메뉴 범위에 보존하며 새 환경에 복사하지 않는다. 시연 안내는 화면 설명만 바꾼다.

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
- 백엔드 주소는 런타임 환경변수로 받는다. 생략하면 로컬 기본값을 쓴다.

## 백엔드 연동

```
브라우저 --> Next 서버(Node) --> Spring(:8080)
```

- Next 서버가 `me_session` 쿠키를 백엔드 Bearer로 전달한다. 쿠키는 httpOnly, SameSite=Lax이며 프로덕션에서 Secure다. 도입사 API에 공용 API 키를 대신 쓰지 않는다.
- 공개 로그인과 가입 요청에는 쿠키의 Bearer를 붙이지 않는다. 경로와 서버 액션에서 각각 인증을 검사한다.
- 로그아웃 요청이 401 밖의 이유로 실패해도 이 브라우저의 `me_session` 쿠키는 지우고, 로그인 화면에서 서버 세션 종료를 확인하지 못했다고 알린다. 그 서버 세션은 만료 시각에 끝난다.
- `src/lib/config.ts`, 인증과 API 클라이언트는 `server-only`다. 브라우저는 백엔드를 직접 호출하지 않는다.
- 필터 선택지도 `no-store`로 조회한다. 같은 세션 토큰으로 도입사를 바꿀 수 있어 Authorization만으로 응답을 캐시하지 않는다. 고객과 미터 변경 뒤에는 `revalidatePath`로 화면을 갱신한다.

사용자와 API 키의 권한은 각각 지정한다. 새 사용자와 초대, 플랫폼에서 연결한 소속의 기본 권한은 `read`다.

| 권한 | 허용하는 업무 |
| --- | --- |
| `read` | 고객, 사용량, 청구와 설정 조회. 변경 버튼은 숨김 |
| `ingest` | 고객 등록과 이벤트 단건, 배치 수집. 조회에는 `read`도 필요 |
| `admin` | 조회와 수집을 포함한 과금 업무 변경, 로그인 사용자의 API 키 발급, 권한 변경과 폐기 |
| 대표관리자 (`owner`) | 전체 관리와 사용자, 초대 관리. 전체 관리 권한 제거 불가 |
| 플랫폼 운영자 (`operator`) | 플랫폼의 도입사 승인, 소속 연결과 운영자 관리. 도입사 업무 권한과 별개 |

- 사용자의 권한은 선택한 도입사마다 다르다. 직접 생성과 초대에서 지정하며, 초대 수락 후에도 유지한다. 대표관리자가 사용자 관리에서 변경한다.
- API 키의 권한 변경은 키 원문을 다시 발급하지 않는다. 다른 키의 관리와 도입사 사용자 관리는 로그인 세션으로만 한다.
- 수집 전용 사용자는 업무 조회 권한 안내와 도입사 선택, 로그아웃을 사용할 수 있다. Server Action과 API 호출도 요청 시점의 세션 권한을 검사한다.
- 고객 인보이스 공개 링크의 조회와 결제 재시도 권한은 로그인 사용자의 업무 권한과 별도로 검사한다.

## 구조

| 경로 | 내용 |
| --- | --- |
| `src/app/(console)/` | 라우트. 화면마다 `page.tsx`, 쓰기 화면은 `actions.ts`, `state.ts` |
| `src/app/(public)/` | 로그인, 가입 신청, 승인 안내 화면과 그 Server Action |
| `src/app/organizations/` | 소속 도입사 선택과 전환 |
| `src/app/account/` | 첫 로그인 때 계정 이용 안내 확인 |
| `src/app/organization-invitations/` | 초대 링크 확인과 수락 |
| `src/app/admin/` | 플랫폼 운영자 화면 |
| `src/proxy.ts` | 세션 쿠키가 없으면 공개 화면 밖의 경로를 로그인으로 보내고, 현재 경로와 화면의 도입사를 서버에 넘긴다 |
| `src/lib/auth/` | 세션 확인, 권한, 로그인 뒤 이동 경로 |
| `src/app/health/` | `deploy.sh`가 서버 안에서 부르는 준비 확인. 백엔드를 부르지 않고 `ok`만 돌려준다. Caddy가 바깥에서는 404로 막는다 |
| `src/components/` | 화면별 컴포넌트 |
| `src/components/shell/` | 콘솔과 운영자 셸, 메뉴, 안내 모드, 테마와 로케일을 거는 `AppProviders` |
| `src/components/screen/` | 화면 공용 조각: 제목 줄, 툴바, 카드, 각주, 빈 상태, 오류, 스켈레톤, 링크 버튼, 월 선택, 검색과 필터, 페이지 |
| `src/components/table/` | 표 공용 조각: 접기 상태, 조합 칸, 미터 라벨, 숫자 칸, 정렬, 표 공통값 |
| `src/lib/api/` | 백엔드 호출. `client.ts`가 공용 fetch |
| `src/lib/config.ts` | 서버 사이드 설정 |
| `src/app/layout.tsx` | antd 스타일을 SSR HTML에 넣는 `AntdRegistry`, 로컬 Pretendard 글꼴 변수 |
| `src/app/globals.css` | 콘솔 셸과 인증 화면의 공통 배치, 색 변수, 화면 폭에 따른 본문 여백 |

화면 컴포넌트의 접미사는 다음과 같다.

- `Section`은 데이터를 기다렸다가 결과에 따라 그리는 바깥 경계다.
- `Screen`은 데이터가 도착한 뒤 `Section`이 그리는 화면이다.
- `Loading`은 `Section`이 기다리는 동안 그 자리에 보이는 것이다.
- `Frame`은 `Section`의 오류 상태와 `Loading`이 함께 쓰는 틀이다.
- `Meta`는 화면 제목 아래에 붙는 요약이다.

스타일 규칙은 다음과 같다.

- 업무 화면은 antd 6 컴포넌트로 그린다. 공통 배치는 `globals.css`, 페이지 고유 스타일은 같은 폴더의 CSS Modules(예: `HomePage.module.css`)에 둔다.
- 테마는 보라색 강조다. 컴포넌트 토큰은 `AppProviders.tsx`, 인증과 셸의 배치는 `globals.css`에 둔다. 같은 색을 쓰는 CSS 변수는 `globals.css`와 CSS Modules에도 있으니 함께 바꾼다. `globals.css` 안에서는 `:root` 토큰과 같은 색을 리터럴로 다시 적지 않고 `var()`로 쓴다. 토큰은 `theme.useToken()`의 `token.paddingLG`처럼 멤버 접근으로 읽는다.
- antd는 `"use client"` 파일에서만 import한다. Server Component는 직렬화할 수 있는 prop만 넘겨 그 래퍼를 그린다.
- 폼은 HTML form 또는 antd Form과 antd 입력을 쓴다. 서버 액션과 작업 상태는 `useActionState`로 연결한다.
- 페이지를 옮기는 버튼은 `LinkButton`, 토스트는 `App.useApp()`의 `message`를 쓴다.
- 본문과 UI는 로컬 Pretendard Variable 1.3.9(https://github.com/orioncactus/pretendard)를 `next/font/local`로 쓴다. 로고는 기존 서체, 코드와 JSON은 고정폭 서체를 유지한다. 글꼴 파일과 OFL 라이선스는 `src/app/fonts/`에 있다.

## 목록 조회

- `src/lib/api/capabilities.ts`와 `src/lib/list-query.ts`가 화면의 조건과 BE 파라미터를 연결한다. UI 페이지는 0부터, 새 `/search` API는 1부터 시작하며 API 어댑터에서 변환한다.
- 고객, 미터, 가격, 사용량, 청구 예정액과 인보이스는 서버 검색, 필터, 정렬, 페이지 처리와 조건 전체 CSV를 사용한다. 월, 고객, 검색어 등은 함께 선택한 뒤 `조건 적용`으로 제출한다. 청구 확정은 조회한 한 페이지가 아니라 해당 월 전체 대상에 적용하며 전체 규모를 확인한다.
- 선택지와 조회 가능한 월은 서버에서 받는다. 이벤트도 같은 조건으로 CSV를 내려받고 원본 JSON 포함 여부를 선택한다. CSV는 최대 10,000행, 한도를 넘으면 조건을 좁혀야 한다.
- 이벤트는 월별 조회 또는 날짜/초 단위 직접 범위를 선택한다. 상세를 닫으면 조회 조건과 페이지로 돌아간다.
- URL과 제출 폼은 `organization_id`와 `environment`를 보존한다. 다른 탭에서 도입사가 바뀐 오래된 폼과 내보내기는 거절한다. 업무 메뉴는 모두 선택한 환경의 데이터를 보이고, 계정과 권한은 환경에 관계없이 같다.
- 수집 성공 후 매핑되지 않은 문제는 `/event-issues`, 수집 단계의 거절은 `/event-rejections`에서 구분한다.

## 화면

| 경로 | 사용자 과업 |
| --- | --- |
| `/` | 서비스 소개 (로그인 없이 보는 첫 화면, 로그인과 가입 신청 링크) |
| `/overview` | 미완료 설정, 과금 경고와 월별 예정액, 확정액, 결제 대기액, 순수납 확인, 전체 월의 미해결 결제 |
| `/customers`, `/customers/{id}` | 외부 ID로 고객 연결, 보관과 복원, 사용량, 청구, 수납과 카드 등록 연결 |
| `/usage`, `/events` | 집계, 미터 미연결, 값 오류, 미래 시각과 음수 정정 확인, 원본 로그 조회 |
| `/pricing`, `/billable-metrics` | 월과 고객별 단가 이력, 구간 요금, 기본료, 최소 과금과 미터 설정, 보관과 복원 |
| `/billing`, `/invoices` | 단가 없는 조합과 음수 합을 검토한 뒤 확정, 계산 근거 조회 |
| `/payments`, `/payments/{id}` | 결제 요청과 수동 재시도, 결과 확인, 결제 실패 건만 보기, 부분과 전액 취소, 시도와 취소 이력, 고객 조회 링크의 재시도 권한 선택, 발급과 폐기 |
| `/invoice-links` | 로그인 없이 확정 청구와 결제 상태 확인. 별도 허용된 링크에서 실패 결제의 금액을 확인한 뒤 등록 카드로 직접 재시도. 링크 원문은 URL fragment로 받고 주소창에서 제거하며 현재 탭의 sessionStorage에만 보존 |
| `/integrations`, `/integrations/{id}` | API 키 발급과 교체, 권한 변경, 폐기, 웹훅 등록과 발송 이력, 마지막 시도가 실패한 수신 주소 표시, 시험 통보와 실패 재전송 |
| `/members` | 사용자 관리 (계정 직접 생성, 초대 링크, 업무 권한과 역할 변경, 소속 보관, 초대 이력) |
| `/audit` | 감사 기록 (도입사의 변경 기록과 API 요청 기록 조회) |
| `/settings` | 환경별 공급자와 토스 키, 기존 데이터의 결제 모드 설정 |
| `/admin`, `/admin/organizations`, `/admin/users` | 전체 운영 현황, 가입 승인, 사용자 권한과 소속 연결 관리 |
| `/admin/audit` | 운영자의 플랫폼 감사 기록 조회 |
| `/payment-method-registrations/{token}` | 일회용 링크 확인과 동의, 토스 카드 인증, 등록 결과 확인 |
| `/login` | 로그인. 계정 역할에 따라 도입사 업무나 플랫폼 운영 화면으로 간다 |
| `/signup` | 도입사 가입 신청과 필수 동의 |
| `/pending` | 가입 신청 뒤 승인 안내 |
| `/organizations` | 소속 도입사 선택과 전환 |
| `/account/consents` | 첫 로그인 때 새 비밀번호 설정과 계정 이용 안내 확인 |
| `/organization-invitations/{token}` | 초대 링크 확인과 수락 (기존 계정 로그인 또는 새 계정 설정) |

- `/billable-metric-prices`는 월과 고객 조건을 유지해 `/pricing`으로 이동한다.
- API 키의 분당 한도는 양수 또는 무제한으로 정한다. 유한 한도는 인스턴스별이다.
- 콘솔 결제성 요청은 75초, 고객 직접 재시도는 120초까지 기다린다. 응답 유실은 실패로 단정하지 않고 상태를 새로 조회하기 전 재요청을 막는다. 취소는 같은 요청 식별자를 유지한다.
- 고객 조회 링크는 기본 조회 전용이다. 도입사가 발급할 때 재시도를 별도로 허용해도 실패한 결제만 대상으로 하며, 처음 결제하거나 취소액을 다시 청구하는 동작은 제공하지 않는다.
- API 키와 웹훅 비밀값은 발급 직후 한 번만 보여 준다. 서버 호출은 로그인 세션을 다시 검사하며, 공용 API 키로 사용자 인증을 대신하지 않는다.
- 예정액은 공급가액, 확정액과 수납액은 세금 포함 금액이다. 취소해도 확정 인보이스는 바뀌지 않는다.
- 토스 인증 뒤 서버가 빌링키를 발급한다. 완료 주소에는 인증키를 남기지 않으며, 새로고침으로 다시 발급하지 않는다.
- 실제 토스 카드 인증은 상점 테스트 키와 카드 인증이 필요하다. 로컬 PG 대역 검증과 구분한다.

## 인증 흐름 검증

```bash
pnpm exec playwright install chromium
pnpm build
pnpm test:e2e:contract
```

계약 테스트는 임시 HTTP 대역과 standalone FE를 `38148`, `33148` 포트에서 실행하고 종료한다. 실제 Spring 검증과 구분한다.

카드 등록과 업무 화면의 계약 테스트는 설정 파일을 지정해 돌린다. 둘 다 `pnpm build` 뒤 대역과 FE를 스스로 띄우고 끝내며 백엔드가 필요 없다.

```bash
pnpm exec playwright test -c playwright.payment.config.ts    # 대역 38149, FE 33149
pnpm exec playwright test -c playwright.workspace.config.ts  # 대역 38150, FE 33150. 결과 폴더는 WORKSPACE_CONTRACT_OUTPUT_DIR
```

실제 S1 Spring 서버를 별도 테스트 DB로 `38147`에 실행한 뒤 아래를 실행한다. 테스트는 가입, 승인, 로그인과 고객 등록을 수행하므로 운영 환경을 지정하지 않는다. 테스트 설정은 로컬 HTTP 주소만 허용한다.

```bash
S1_API_URL=http://127.0.0.1:38147 \
E2E_OPERATOR_EMAIL=operator@fe-e2e.local \
E2E_OPERATOR_PASSWORD=local-operator-e2e-password \
pnpm test:e2e:real
```

결제와 환경 분리까지 이어지는 실제 스택 시나리오는 같은 변수로 `pnpm exec playwright test -c playwright.mvp.config.ts`를 돌린다. 백엔드는 `local` 프로파일(사설 주소 웹훅 허용)로, `TOSSPAYMENTS_BASE_URL=http://127.0.0.1:38447`과 `UNBILLED_USAGE_REPORT_CRON='*/20 * * * * *'`를 주고 띄운다. 미청구 사용량 통보의 기본 주기(매일 09:00)로는 `mvp.real.spec.ts`의 통보 대기 단계가 실패한다. 토스 대역 `38447`은 설정이 띄우며 `MVP_EXTERNAL_PG_FIXTURE=1`이면 띄우지 않는다. `MVP_REUSE_FRONTEND=1`이면 이미 뜬 `33147` FE를 쓴다.

`playwright.mvp.config.ts` 시나리오 가운데 `customer-payment-retry`, `invoice-links`, `permissions`는 테스트 DB를 `docker exec ... psql`로 직접 고친다. `E2E_DB_CONTAINER`에 `S1_API_URL` 백엔드가 쓰는 PostgreSQL 컨테이너 이름(기본 `meterengine-0696-e2e`)을, `E2E_BACKEND_ENV_FILE`에 백엔드를 띄운 `TOSSPAYMENTS_BASE_URL`을 담은 JSON 파일(기본 `/private/tmp/meterengine-orchestration-0696/backend-e2e-environment.json`, 값은 `{"TOSSPAYMENTS_BASE_URL":"http://127.0.0.1:38447"}`)을 지정한다.

실제 검증용 FE는 `33147`을 쓴다. `E2E_OPERATOR_EMAIL`, `E2E_OPERATOR_PASSWORD`는 해당 테스트 DB의 소속 없는 운영자 계정이어야 한다. 결과는 `/tmp/meterengine-fe-onboarding-results-real`, 계약 테스트 결과는 같은 경로의 `-contract`에 둔다.

가입 동의 안내는 `2026-10-26-preview` 개발 검증용이다. 정식 약관과 개인정보 처리방침은 서비스 공개 전에 확정해야 한다. S1에 동의 이력 조회 API가 없어 승인 화면에서 동의 내역은 조회하지 않는다.
