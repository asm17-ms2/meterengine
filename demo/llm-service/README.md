# OpenAB LLM 서비스 예제

가상의 LLM 회사 OpenAB가 고객사에 여는 서비스다. 고객사 계정으로 들어와 대화, API 플레이그라운드, 일괄 처리를 쓰고 본인의 사용량과 청구를 확인한다. 이벤트 전송 도구인 `demo/web`과 별도 서버로 실행한다.
응답은 실제 모델 대신 로컬 모형이 만들고, 토큰 수도 글자 수로 어림한 시연용 값이다. 사용량 수집, 단가와 청구 조회는 실제 MeterEngine API를 쓴다.
세션, 대화, 요청 기록과 전송 대기는 자체 SQLite에 둔다. MeterEngine DB에 직접 접근하지 않으며 실행 묶음을 다른 서버로 복사할 수 있다.

회사 이름, 모델 목록, 이벤트 유형과 미터 코드는 `src/lib/catalog.ts` 한 곳에서 정한다. 화면과 이벤트는 이 파일을 읽는다.

## 화면

| 경로 | 화면 | 하는 일 |
| --- | --- | --- |
| `/` | 대화 | 왼쪽 목록에서 지난 대화를 고르거나 새 대화를 연다. 위에서 모델을 고르고, 응답은 글자가 차례로 나타난다. 동작 줄이기 설정이면 한 번에 보인다. |
| `/playground` | API 플레이그라운드 | 모델, 시스템 지시, 메시지로 보낼 요청 본문과 응답 JSON, 토큰 넷의 사용량을 보여 준다. 임베딩 모델을 고르면 임베딩 요청으로 바뀐다. |
| `/batch` | 일괄 처리 | 한 줄에 하나씩 최대 20건을 한 번에 처리한다. 항목마다 이벤트를 하나씩 보내고, 같은 모델의 API 단가와 절반 단가를 나란히 보여 준다. |
| `/usage` | 사용량 | 이번 달 토큰 넷의 합계와 모델, 용도별 내역, 적용 단가 |
| `/billing` | 청구 내역 | 청구 예정액, 확정 청구서, 납부 상태, 결제 카드 |
| `/history` | 요청 기록 | 용도, 모델, 검색어로 거른 요청과 응답, 사용량 기록 상태 |

모든 화면 맨 위에 시연용 서비스라는 띠가 붙어 있고, 스크롤해도 사라지지 않는다.

## 사용량 이벤트

요청 하나가 끝나면 이벤트 하나를 보낸다. 일괄 처리는 항목마다 하나다. 이벤트 속성은 `src/lib/metering-event.ts`의 `usageProperties` 한 곳에서 만든다.

| 필드 | 값 |
| --- | --- |
| `type` | `llm_request` |
| `transaction_id` | `catalog.ts`의 `transactionId()`가 요청 ID로 만든 값 |
| `properties` | `model`, `surface`, `input_tokens`, `output_tokens`, `cache_read_tokens`, `cache_write_tokens`, `latency_ms`. 일곱을 늘 모두 보낸다. |

`surface`는 `chat`, `api`, `batch` 가운데 하나다. 임베딩은 입력 토큰만 세고 출력과 캐시 토큰은 0으로 보낸다.

미터는 토큰 넷(입력, 출력, 캐시 읽기, 캐시 쓰기)이다. 모두 이벤트 속성 값을 더하고, 과금 단위는 100만 토큰, 차원 키는 `model`과 `surface`다. 앱은 이 계약과 다른 미터를 받으면 사용량이나 요금을 표시하지 않고 안내 문구를 보인다.

토큰은 용도마다 이렇게 센다.

- 대화: 새 메시지는 입력, 응답은 출력이다. 시스템 지시와 지난 대화는 캐시 읽기, 바로 앞 차례에 새로 캐시에 들어간 부분은 캐시 쓰기다. 대화 도중 모델을 바꾸면 캐시를 새로 만든다.
- API: 메시지는 입력, 응답은 출력이다. 시스템 지시가 1,024토큰 이상이면 처음 보낼 때 캐시 쓰기, 5분 안에 같은 모델과 지시로 다시 보내면 캐시 읽기다.
- 일괄 처리: 항목마다 입력과 출력만 센다.

## 로컬 실행

Node 24.22.1, pnpm 11.18.0과 실행 중인 MeterEngine이 필요하다. 저장소 루트에서 실행한다.

```sh
cd demo
pnpm install --frozen-lockfile
pnpm --filter @meterengine/llm-service build
```

초기화가 만든 비공개 설정 파일로 앱을 띄운다. 기본 주소는 `http://localhost:3200`이다. 초기화는 아래 "시연 데이터 초기화"를 따른다.

```sh
node llm-service/scripts/start.mjs --config /절대경로/config.json
```

## 시연 데이터 초기화

초기화 스크립트는 MeterEngine에 가상 LLM 회사 OpenAB의 조직을 새로 만들고, 그 조직의 테스트 환경에 아래를 넣는다. 다른 조직은 건드리지 않는다. `--existing-owner`로 화면에서 가입한 조직을 이어받을 때만 새로 만들지 않고 그 조직에 넣는다. 실제 환경(live)에는 어느 주소에서도 넣지 않는다.

- 고객 40곳(큰 곳 5, 중간 15, 작은 곳 12, 신규 8). 이 가운데 4곳이 앱에서 고르는 고객사 계정이다
- 토큰 미터 넷(입력, 출력, 캐시 읽기, 캐시 쓰기). 모델과 용도(`model`, `surface`)로 나누고 100만 토큰 단위로 센다
- 모델과 용도별 공통 단가, 큰 고객 2곳의 ab-5 고객 단가
- 앱이 쓰는 서버 API 키 둘(수집과 조회, 카드 등록). 아래 "키" 참고
- 이번 달 1일(한국 시간)부터 실행 시각까지의 사용량 이벤트. 평일 하루 6,500건 안팎이다

```sh
cd demo
export METERENGINE_API_BASE_URL=http://127.0.0.1:38147
node llm-service/scripts/seed-demo.ts --output ~/.openab-demo/config.json
```

- 가입한 조직은 승인 없이 바로 쓴다. 운영자 계정은 가입 응답을 받지 못한 채 끊겼을 때(시간 초과 등) 만든 조직을 찾는 데만 쓴다. 그때 스크립트가 `METERENGINE_OPERATOR_EMAIL`, `METERENGINE_OPERATOR_PASSWORD`를 달라고 하고 멈춘다. 로컬 계정은 `backend/README.md` "로컬 계정과 키"에 있다
- 암호를 `이름=값` 꼴로 명령줄에 적으면 셸 기록에 남는다. `read -rs 이름 && export 이름`으로 받고 끝나면 `unset 이름`한다(bash, zsh)
- `--output`에 앱 설정 JSON(`OPENAB_*`, `METERENGINE_*`)을 쓰고, 옆의 `<output>.seed-state.json`에 이어서 실행할 상태(대표 계정 이메일과 암호, 키 ID와 원문)를 둔다
  - 두 파일에는 암호와 키 원문이 평문으로 들어간다. 소유자만 읽고 쓰는 권한(0600)으로 만든다
  - 출력 경로가 git 저장소 안이면 서버에 요청하기 전에 멈춘다. 홈 폴더 아래의 저장소가 아닌 폴더(예: `~/.openab-demo/`)에 둔다. 앱 DB(`openab.sqlite`)도 기본으로 그 옆에 생긴다
- 앱 주소, 앱 DB 경로, 콘솔 주소는 `OPENAB_APP_ORIGIN`, `OPENAB_DATABASE_PATH`, `METERENGINE_CONSOLE_URL`로 바꾼다. 기본값은 `http://localhost:3200`, 출력 파일 옆의 `openab.sqlite`, `http://localhost:33147`이다
- 조직 이름은 `OPENAB_ORGANIZATION_NAME`으로 바꾼다. 기본값은 `OpenAB`다. 한 서버에 시연용과 검증용 조직을 따로 둘 때 쓴다
- `METERENGINE_ENVIRONMENT`가 `test`가 아닌 값으로 잡혀 있으면 멈춘다. 앱 설정을 불러온 셸에서 돌릴 때 실제 환경에 넣는 일을 막는다
- 끝나면 조직 ID, 고객 수, 보낸 이벤트 수와 중복 수, 걸린 시간, 청구 예정액 합계, 남은 키의 ID를 JSON으로 낸다. 키 원문과 암호는 찍지 않는다
- 종료 코드: 0은 정상, 1은 오류이거나 거절된 이벤트가 있거나 수집 키를 폐기하지 못함, 2는 끝까지 넣었지만 서버의 청구 예정액이 스크립트의 추정값과 다르거나 단가 없는 사용량이 있음. 앱에서 요청을 보낸 뒤 다시 돌리면 그만큼 예정액이 커지고, 이번 달 인보이스를 확정한 뒤에는 예정액이 0원이라 2로 끝난다
- 고객 이름, 단가, 분포는 `scripts/demo-data/`에 있다. 고객 이름은 가상의 이름이다

### 다시 실행

- 같은 `--output`으로 다시 돌리면 같은 조직에 이어서 넣는다. 미터, 단가, 고객(외부 ID `openab:<슬러그>`), 앱 키는 있으면 다시 만들지 않는다. 중간에 실패한 실행도 같은 명령으로 이어 간다
- 이벤트는 씨앗과 고객, 날짜에서 만든다. 같은 씨앗이면 거래 ID도 같아서 이미 넣은 이벤트는 중복으로 처리되고, 지난 실행 뒤의 시간대만 새로 들어간다
  - 상태 파일에 그 달 1일 하루치의 지문을 달과 함께 남기고, 같은 달에 다시 돌릴 때 생성기가 같은 값을 내는지 본다. 다르면(Node 버전이 바뀐 경우 등) 멈춘다
  - 달이 바뀐 뒤 같은 `--output`으로 돌리면 같은 조직에 새 달의 이벤트를 넣는다. 고객과 앱 키는 그대로 쓰고, 끝나는 달을 정하지 않은 단가도 다음 달에 그대로 적용된다
- 새 조직을 만들려면 다른 `--output`을 쓴다. 상태 파일 없이 이미 있는 설정 파일은 덮어쓰지 않는다
- 상태 파일을 잃거나 다른 `--output`으로 돌리면 같은 이름의 조직이 하나 더 생긴다. 스크립트는 같은 이름의 조직이 있는지 보지 않는다. 운영자 화면에서 조직 ID로 구분하고, 쓰지 않는 쪽은 운영자가 정리한다

### 키

| 키 | 권한 | 분당 한도 | 쓰임 |
| --- | --- | --- | --- |
| `OpenAB 초기화 수집(끝나면 폐기)` | ingest | 없음(테스트 환경 상한) | 초기화가 이벤트를 넣는 동안만 쓴다. 수집이 끝나면, 도중에 실패해도 스크립트가 폐기한다 |
| `OpenAB 서비스 수집과 조회` | read, ingest | 300 | 앱의 `METERENGINE_API_KEY` |
| `OpenAB 카드 등록 서버 전용` | admin | 60 | 앱의 `METERENGINE_BILLING_API_KEY` |

- 테스트 환경 키에는 서버의 분당 상한(기본 300)이 걸린다. 키의 한도와 이 상한 가운데 작은 쪽이 적용된다. 초기화는 상한에 걸린 묶음(429)을 `Retry-After`만큼 기다렸다 다시 보낸다. 상한 300에서 이벤트 7만여 건을 넣는 데 1분 30초 남짓 걸린다
- 상태 파일의 `keys`에 키마다 ID와 원문을 남긴다. 끝난 뒤의 출력에도 남은 키의 ID가 있다
- 키 발급 응답을 잃으면(시간 초과 등) 원문을 모르는 키가 서버에 남는다. 다시 돌리면 위 라벨인데 상태 파일에 없는 키를 폐기하고 새로 발급한다
- 수집이 도중에 실패해도 스크립트는 한도 없는 초기화 수집 키를 폐기하고 끝난다. 폐기 요청까지 실패했을 때만(서버에 닿지 않는 경우 등) 이 키가 상태 파일에 남고 "폐기하지 못했습니다"라고 알린다. 같은 명령으로 다시 돌리면 이어 쓴 뒤 폐기한다. 다시 돌리지 않을 것이면 아래 절차로 폐기한다
- 이 API에는 키 만료 시각을 정하는 입력이 없다. 앱 키 둘은 폐기할 때까지 산다
- 시연이 끝나면 키를 폐기한다
  1. 콘솔에 그 조직의 대표 계정으로 들어가 테스트 환경을 고른다. 스크립트가 만든 조직은 상태 파일의 `ownerEmail`, `ownerPassword`로, `--existing-owner`로 이어받은 조직은 그 계정의 암호로 들어간다
  2. "API 키와 통보"에서 위 라벨의 키를 폐기한다. ID는 상태 파일의 `keys`와 같다
  3. 앱을 내리고 출력 폴더(설정, 상태 파일, `openab.sqlite`)를 지운다
- 앱은 계정을 고르기만 하면 들어가는 시연용 로그인이다. 공개 주소에 띄우면 누구나 앱 키의 한도 안에서 이벤트를 넣을 수 있으니, 시연하는 동안만 띄운다

### 인자

| 인자 | 뜻 |
| --- | --- |
| `--output <경로>` | 앱 설정 JSON 경로. git 저장소 밖이어야 한다. `--dry-run`이 아니면 필수 |
| `--seed <값>` | 난수 씨앗. 기본값 `openab-2026`. 진행 중인 출력 경로와 다른 씨앗은 받지 않는다 |
| `--until <ISO 시각>` | 이 시각 전의 이벤트만 넣는다. 기본값은 지금이다. 미래 시각과 지난달(한국 시간) 시각은 서버에 요청하기 전에 거절한다 |
| `--concurrency <1~8>` | 동시에 보내는 묶음(100건) 수. 기본값 4 |
| `--dry-run` | 서버에 접속하지 않고 만들 것의 수(고객, 미터, 단가, 날짜와 용도, 모델별 이벤트, 토큰 합계, 예상 청구액)를 보여 준다. `--until`의 날짜 제한 없이 계산만 한다 |
| `--base-url <주소>`, `--confirm-host <호스트>` | 로컬이 아닌 서버를 고를 때 둘 다 준다. 아래 참고 |
| `--existing-owner`, `--organization-id <조직 ID>` | 가입하지 않고 화면에서 가입해 둔 대표 계정과 조직으로 이어 간다. 아래 "운영에서 돌리기 전에 확인할 것" 참고 |

### 로컬이 아닌 서버

- 기본 대상은 로컬(`localhost`, `127.0.0.1`, `[::1]`)이다. `METERENGINE_API_BASE_URL`이 로컬이 아니면 실행하지 않는다
- 로컬이 아닌 서버는 `--base-url https://<호스트>`와 `--confirm-host <같은 호스트 이름>`을 둘 다 줘야 하고 https만 받는다. 앱 설정에 들어갈 `OPENAB_APP_ORIGIN`과 `METERENGINE_CONSOLE_URL`도 줘야 한다
- 로컬 주소가 건너뛰는 것은 https와 `--confirm-host`뿐이다. 운영 서버 안에서 `127.0.0.1`로 돌리거나 SSH 터널을 걸면 주소는 로컬이어도 대상은 운영이다. 그때도 테스트 환경에만 들어가지만, 대상을 직접 확인하고 돌린다
- 지난달 날짜의 이벤트는 넣지 않는다. `--until`이 지난달이면 요청 전에 멈춘다. 그 달 집계 마감(다음 달 1일 13:00 KST) 뒤에 받은 이벤트는 그 달 사용량과 청구에 들어가지 않고, 지난달 단가도 등록할 수 없기 때문이다

### 운영에서 돌리기 전에 확인할 것

- 가입 API에 기댄다. 스크립트는 `POST /v1/signups`로 조직과 대표 계정을 만들고 바로 로그인한다. 로그인이 `organization_not_active`로 거절되면(조직 이용 정지) 멈춘다
- 약관 판이 코드에 박혀 있다. `scripts/seed-demo.ts`의 `CONSENT_VERSION`(`2026-11-11`)을 대표 계정이 동의한 판으로 보낸다. 서버는 이 값을 검사하지 않고 그대로 기록하므로 값이 달라도 가입은 된다. 다만 동의 기록에 옛 판이 남으니, 콘솔의 `frontend/src/lib/auth/consents.ts`와 같은 값인지 보고 다르면 맞춘다
- 가입에 이메일 인증이나 캡차가 켜진 서버에서는 스크립트로 가입할 수 없다. 그때는 화면에서 조직을 가입하고 이메일 인증까지 마친 뒤 그 대표 계정으로 이어 간다

  ```sh
  export OPENAB_OWNER_EMAIL=<대표 계정 이메일>
  export OPENAB_APP_ORIGIN=https://<앱 주소> METERENGINE_CONSOLE_URL=https://<콘솔 주소>
  # 대표 계정 암호를 입력하고 Enter를 누른다. 화면에도 셸 기록에도 남지 않는다
  read -rs OPENAB_OWNER_PASSWORD && export OPENAB_OWNER_PASSWORD
  node llm-service/scripts/seed-demo.ts --existing-owner --organization-id <조직 ID> \
    --base-url https://<호스트> --confirm-host <호스트> --output ~/.openab-demo/config.json
  unset OPENAB_OWNER_PASSWORD
  ```

  - 가입하지 않는다. 로그인은 계정이 속한 첫 사용 중 조직으로 들어가므로, 이어받을 조직을 `--organization-id`로 고르고 세션을 그 조직으로 옮긴다. 조직 ID를 모르면 `--organization-id` 없이 한 번 돌린다. 계정이 속한 조직의 이름과 ID를 보여 주고 아무것도 넣지 않은 채 멈춘다
  - 처음 이어받을 때 테스트 환경에 고객과 미터가 하나도 없는 조직만 받는다. 쓰던 조직에 시연 데이터를 섞지 않기 위해서다
  - 암호는 상태 파일에 저장하지 않는다. 다시 돌릴 때마다 `OPENAB_OWNER_PASSWORD`를 준다
  - 상태 파일을 잃으면 그 조직은 이미 고객이 있어 이어받을 수 없다. 새 조직을 가입해 다시 한다
  - `OPENAB_OWNER_EMAIL`, `OPENAB_OWNER_PASSWORD`는 `--existing-owner` 없이 주면 받지 않고 멈춘다
- 새 키가 여러 서버에 퍼지기 전에는 수집이 401로 거절될 수 있다. 스크립트는 첫 묶음이 받아들여질 때까지만 401을 다시 시도한다
- 같은 Node 버전(24.22.1)으로 돌린다. 이벤트 값이 `Math.exp`, `Math.log`에 기대므로 엔진이 바뀌면 같은 씨앗에서 다른 값이 나올 수 있다

## 서버 설정

JSON 설정 파일은 다음 환경변수의 문자열 맵이다. `.env.example`은 컨테이너 환경변수 형식이다.

| 이름 | 용도 |
| --- | --- |
| `OPENAB_APP_ORIGIN` | 브라우저가 접근할 정확한 origin. 다른 서버로 옮길 때 바꾼다. |
| `OPENAB_DATABASE_PATH` | 자체 SQLite의 절대 경로. 서버를 다시 띄워도 남는 볼륨을 쓴다. 생략하면 앱 폴더의 `data/openab.sqlite`다. |
| `OPENAB_ACCOUNTS` | `{id,name,email,customerId}` 객체의 JSON 배열. 고객사 계정과 MeterEngine 고객을 연결한다. 계정 선택 화면은 `name`을 회사 이름으로 보여 준다. |
| `METERENGINE_API_BASE_URL` | 서버에서 접근하는 MeterEngine API 주소. |
| `METERENGINE_ENVIRONMENT` | `test` 또는 `live`. 생략하면 API 키의 환경을 쓴다. |
| `METERENGINE_API_KEY` | 수집과 조회용 `read`, `ingest` 키. 브라우저에 전달하지 않는다. |
| `METERENGINE_BILLING_API_KEY` | 카드 등록 링크 발급용 선택 키. 현재 API 계약의 `admin` 권한이 필요하며 본인 고객의 링크에만 쓴다. 실제 E2E는 이 키로 이번 달 인보이스 확정도 검증한다. |
| `METERENGINE_CONSOLE_URL` | 카드를 등록할 MeterEngine 화면 주소. |
| `OPENAB_AUTO_FLUSH_INTERVAL_MS` | 주기 재전송 간격. 기본 3000ms, 0이면 주기 작업만 끈다. 생성 직후와 직접 재전송은 실행한다. |

카드 등록은 지정한 업무 환경의 PG 설정을 쓴다. 환경을 생략한 시연 연결은 테스트 카드 등록만 허용한다. 이 앱은 결제 실행이나 인보이스 확정을 하지 않는다. 실제 E2E만 검증을 위해 이번 달 인보이스를 되돌릴 수 없게 확정하므로, 같은 달에 다시 돌리려면 새 출력 경로로 초기화하고 앱을 다시 띄운다.

SQLite는 API 주소, 업무 환경과 서버 키에 고정된다. 연결 설정을 바꿀 때는 기존 파일을 남기고 다른 `OPENAB_DATABASE_PATH`를 지정한다.

SQLite 구조는 대화, 일괄 처리, 토큰 넷을 담도록 바뀌었고 이전 파일을 자동으로 옮기지 않는다. 이전 버전이 만든 파일은 쓰지 말고 새 경로로 시작한다.

## 별도 서버로 옮기기

production build 뒤 실행 묶음을 새 폴더로 내보낸다.

```sh
node llm-service/scripts/export-standalone.mjs --output /private/tmp/openab-bundle
node /private/tmp/openab-bundle/start.mjs --config /절대경로/config.json
```

묶음, Node 24.22.1, 비공개 설정과 SQLite 볼륨으로 실행한다. 원래 저장소나 MeterEngine의 소스, DB 접속 정보는 필요하지 않다. SDK는 묶음에 들어 있고 MeterEngine에는 HTTP로 접속한다.

다른 포트로 검사할 때는 origin도 같이 바꾼다. 외부에서 직접 접속받으려면 `--host 0.0.0.0`도 지정한다.

```sh
node /private/tmp/openab-bundle/start.mjs --config /절대경로/config.json --port 3201 --origin http://localhost:3201
```

컨테이너 빌드의 context는 `demo`다.

```sh
docker build -f llm-service/Dockerfile -t openab-llm-demo .
docker run --rm -p 3200:3200 --env-file llm-service/.env -v openab-data:/data openab-llm-demo
```

컨테이너의 API 주소는 컨테이너에서 접근할 수 있는 주소여야 한다. 로컬 호스트의 MeterEngine을 쓸 때는 Docker 환경에 맞춰 `host.docker.internal` 등을 지정한다. HTTPS 프록시를 쓸 때는 외부 origin과 Host를 보존한다.

## 동작과 경계

- 계정 선택은 고객 인증을 흉내 낸 것이다. 실제 고객용 인증 시스템으로 주장하지 않는다. 고객 ID는 브라우저 입력으로 고르지 않고 서버 세션에서 정한다.
- 프롬프트와 응답은 이 앱에만 저장한다. MeterEngine에는 고객, 거래 ID, 발생 시각과 위 이벤트 속성만 보낸다.
- 요청 ID가 같으면 같은 결과를 돌려주고 내용이 다르면 거절한다. 수집 실패와 재기동 뒤에도 원래 거래 ID와 발생 시각으로 다시 보낸다.
- MeterEngine이 다시 보내도 같은 이유로 거절하는 사용량(보관했거나 없는 고객, 받는 기간이 지난 이벤트, 잘못된 값)은 자동 재전송을 멈춘다. 원인을 고친 뒤 사용량 다시 보내기로 직접 보낸다.
- 전송 대기와 청구 조회 실패는 실제 반영 완료, 사용량 0과 구분한다. 월별 예상액과 확정 청구서도 나누어 보여 준다.
- 조회 월은 한국 시간의 월초 이상, 다음 달 월초 미만이다. 사용량, 청구, 요청 기록은 `?month=YYYY-MM` 선택을 유지하고 대화, API, 일괄 처리 화면은 이번 달을 보여 준다.
- 응답의 글자가 차례로 나타나는 것은 브라우저가 다 받은 응답을 나눠 보여 주는 것이다. 서버 스트리밍이 아니다.
- 기본 모형은 외부 API를 호출하지 않는다. 실제 추론은 `src/lib/provider.ts`의 `GenerationProvider` 구현을 바꿔 끼우는 자리다.
- 내장 SQLite API는 [Node 24.22.1 문서](https://github.com/nodejs/node/blob/v24.22.1/doc/api/sqlite.md)를 기준으로 쓴다. 이 예제의 전용 저장소이며 MeterEngine의 DB 분리 방식을 정하지 않는다.

## 검증

```sh
pnpm --filter @meterengine/llm-service typecheck
pnpm --filter @meterengine/llm-service lint
pnpm --filter @meterengine/llm-service test
```

실제 E2E(`e2e/*.real.spec.ts`)는 로컬 MeterEngine의 검증용 조직과 자체 SQLite를 쓴다. 고객사 계정이 둘 이상 있어야 한다. 첫 계정에는 초기화 스크립트가 넣은 이번 달 사용량이 있어야 하고(작은 요청 몇 건만으로는 청구액이 0원이라 금액 검증이 실패한다), 그 조직의 이번 달 인보이스가 확정되지 않은 상태여야 한다.

| 이름 | 용도 |
| --- | --- |
| `OPENAB_E2E_ENV_FILE` | 앱에 넣은 비공개 JSON 설정의 경로. 필수. |
| `OPENAB_E2E_URL` | 검사할 앱 주소. 기본 `http://localhost:3200`. |
| `OPENAB_E2E_STANDALONE_URL` | 있으면 같은 SQLite를 연결한 실행 묶음의 세션과 요청 보존도 확인한다. |
| `OPENAB_E2E_SERVER_ENTRY` | 재전송 복구 검증(`metering-recovery.real.spec.ts`)이 직접 띄울 standalone `server.js`의 절대 경로. 그 검증에는 필수. |
| `OPENAB_E2E_ARTIFACT_DIR` | 화면과 결과 파일을 남길 폴더. 기본은 OS 임시 폴더 아래 `meterengine-openab-service`. |
| `OPENAB_E2E_CAPTURE_PREFIX` | 화면 파일 이름 앞에 붙일 문자열. |
