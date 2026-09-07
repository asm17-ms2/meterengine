# frontend

관리자 화면. Next.js + TypeScript.

create-next-app 스캐폴드로 시작했고, 화면을 만들면서 아래로 굳었다.

- App Router. 데이터를 읽는 화면은 Server Component에서 그린다
- 상태 관리 라이브러리는 쓰지 않는다. 조회 조건은 쿼리스트링(`?month=`, `?page=`)이 들고, 접기/펼치기 같은 화면 안의 상태만 Client Component가 쓴다
- 스타일은 Modernist 디자인 시스템이 정본이고 Tailwind는 레이아웃 유틸로만 쓴다 (아래 "디자인 시스템" 참조)

화면은 API 계약을 기준으로 backend와 병렬 개발한다. 계약의 정본은 `backend/openapi.yaml`이다 (백엔드를 띄우지 않고 이 파일로 읽는다).

## 실행

Node.js 24+가 필요하다. 패키지 매니저는 pnpm이고, 버전은 `package.json`의 `packageManager` 필드로 고정한다.

```
corepack enable pnpm   # 최초 1회
pnpm install
pnpm dev               # http://localhost:3000
```

## 빌드와 lint

```
pnpm lint
pnpm build
```

`pnpm build`는 백엔드가 꺼져 있어도 통과해야 한다. CI가 그 상태로 돌린다.

## 컨테이너 이미지

배포용 실행 이미지는 `Dockerfile`이 만든다. 로컬 개발은 이 이미지를 쓰지 않고
위의 `pnpm dev` 그대로다.

```
docker build -t meterengine-frontend .
docker run -p 3000:3000 -e METERENGINE_API_BASE_URL=http://host.docker.internal:8080 meterengine-frontend
```

`next.config.ts`의 `output: "standalone"`이 이 이미지를 위한 설정이다. 백엔드 주소와 도입사
식별자는 이미지에 굽지 않고 런타임 환경변수로 받는다. 운영에서는 `deploy/compose.prod.yml`이
주입하며, 값은 SSM Parameter Store에서 온다. 백엔드 주소는 공개 도메인이 아니라
컨테이너 네트워크 안의 `http://backend:8080`이다.

`src/lib/config.ts`의 기본값 때문에 변수를 빠뜨려도 오류 없이 로컬 주소와 데모 도입사로
떨어진다. 운영 compose는 이 조용한 실패를 막으려고 세 변수를 필수로 걸어 두었다
(값이 없으면 컨테이너가 아예 뜨지 않는다).

## 환경변수

변수 목록과 설명은 `.env.example`에 있다. 값은 `.env.local`에 둔다 (커밋 대상 아님).
`src/lib/config.ts`가 기본값을 들고 있어서 로컬 개발은 `.env.local` 없이도 된다.

## 백엔드 연동

브라우저는 백엔드(:8080)를 직접 호출하지 않는다. 모든 백엔드 호출은 Server Component에서
서버 사이드 fetch로 나간다 (`src/lib/api/client.ts`).

```
브라우저 --> Next 서버(Node) --> Spring(:8080)
```

이렇게 하는 이유는 두 가지다.

- CORS가 아예 없다. 백엔드에 CORS 설정이 없고, `X-Organization-Id`가 커스텀 헤더라
  브라우저가 직접 부르면 preflight에서 막힌다.
- 조회 대상 도입사를 정하는 `X-Organization-Id`가 클라이언트에 가지 않는다. 인증이 아직
  없어서 쓰는 임시 헤더라, 브라우저에 두면 devtools에서 바꿔 다른 테넌트를 조회할 수 있다.

`next.config.ts`의 `rewrites()`로 프록시하지 않는 것도 같은 이유다. rewrites는 요청 헤더를
주입할 수 없어서 헤더를 브라우저가 붙여야 한다.

`src/lib/config.ts`는 `server-only`를 임포트한다. 클라이언트 컴포넌트에서 실수로 쓰면
빌드가 깨진다.

## 디자인 시스템

화면은 Claude Design 프로젝트 "관리자 콘솔"의 Modernist 디자인 시스템을 따른다.

- `src/styles/modernist.css` - 디자인 프로젝트의 `styles.css` 이식본. 원본과 diff를
  유지해야 해서 폰트 토큰 두 줄 말고는 손대지 않는다. 컴포넌트 클래스(`.btn`, `.input`,
  `.tag`, `.nav`, `.card`, `.table`, `.dialog`)를 그대로 쓴다.
- `src/styles/console.css` - 디자인 시스템에 없는 콘솔 전용 클래스. 프로토타입이
  인라인 style로 쓴 레이아웃, hover, 그리드 표를 옮긴 것이다.
- Tailwind는 레이아웃 유틸(`flex`, `grid`, `gap-*`)로만 쓴다. 색, 모서리, 그림자
  네임스페이스는 `globals.css`에서 지웠다. modernist가 같은 CSS 변수를 덮어써서
  `rounded-lg`가 조용히 `0px`이 되는 식의 사고를 막기 위해서다. `bg-neutral-200` 같은
  클래스를 쓰면 CSS가 아예 생성되지 않는다.

### 폰트

두 벌을 쓴다. 브라우저가 글리프 단위로 고른다.

- **Archivo** - 디자인 시스템 서체. 라틴 문자와 숫자를 그린다. `next/font/google`로
  자체 호스팅한다.
- **Pretendard** - 한글. Archivo에 한글 글리프가 없어서 붙였다. jsDelivr CDN에서
  가변 동적 subset판(`pretendardvariable-dynamic-subset.min.css`)을 받는다.

동적 subset을 쓰는 이유는 용량이다. 브라우저가 `unicode-range`를 보고 실제로 쓰는
조각만 받는다.

| 배포본 | CSS | 실제 다운로드 |
| --- | --- | --- |
| 정적 풀셋 (`pretendard.min.css`) | 3 KB | 굵기당 약 750 KB (400 + 800이면 1.5 MB) |
| 가변 풀셋 | 0.6 KB | 2 MB 한 파일 |
| 가변 동적 subset (**사용 중**) | 52 KB | 조각당 약 31 KB, 이 화면 기준 7조각 |

CDN 버전을 `@v1.3.9`로 고정했다. 이 화면은 런타임에 `cdn.jsdelivr.net`에 의존한다.
CSP를 걸거나 폐쇄망에 배포하게 되면 폰트를 레포로 가져와야 한다.

## 화면

라우트는 `src/app/(console)/` 아래에 있다.

| 경로 | 화면 | 백엔드 |
| --- | --- | --- |
| `/events` | 이벤트 로그 (페이지 나누기, 상세 드로어) | `GET /v1/events` |
| `/usage` | 사용량 집계 (고객 그룹 + 미터 자식 행) | `GET /v1/usage` |
| `/billing` | 청구 예정액 | `GET /v1/invoices/draft` |
| `/customers` | 고객 관리 (검색, 등록/수정 다이얼로그, 삭제) | `GET/POST/PUT/DELETE /v1/customers` |
| `/billable-metrics` | 미터 (목록, 이름 검색, 등록/수정 다이얼로그, 삭제) | `GET/POST /v1/billable-metrics`, `PUT/DELETE /v1/billable-metrics/{code}` |

`/customers`와 `/billable-metrics`는 쓰기 화면이다. 저장 버튼은 브라우저에서 시작하므로 서버로
돌아올 길이 필요하고, 그 길이 Server Action이다 (각 라우트의 `actions.ts`). 브라우저는
여전히 백엔드를 직접 부르지 않는다 - 아래 "백엔드 연동"의 이유가 쓰기에도 그대로 적용된다.

`/`는 `/usage`로 리다이렉트한다 (`src/app/page.tsx`).

개발 모드에서는 사이드바 하단에 표 상태(정상/빈 상태/로딩/에러) 스위치가 뜬다.
`?state=`만 바꾸며, 프로덕션 빌드에서는 통째로 제거된다.
