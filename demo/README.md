# demo

API 연동 도구와 도입사의 고객용 서비스 예제를 실행한다.

| 앱 | 기본 포트 | 용도 |
| --- | --- | --- |
| `web` | 3100 | SDK 전송, CSV, 브리지, 웹훅과 고객 조회를 확인하는 개발자 도구 |
| [LLM 서비스](llm-service/README.md) | 3200 | 고객 로그인, 모델 사용, 본인 사용량과 청구를 연결한 도입사 예제 |

확정 인보이스를 MeterEngine 화면으로 전달하려면 서버 SDK의 `createInvoiceViewLink`로 링크를 발급한다. 발급과 폐기는 `admin`, 목록 조회는 `read` 권한이 필요하다. 기본 유효기간은 30일이며 최대 90일이다.

```ts
const link = await client.createInvoiceViewLink(invoiceId, { expires_in_days: 30 });
const url = new URL("/invoice-links", consoleUrl);
url.hash = link.token;
```

고객에게 `url.toString()`을 전달한다. 링크를 가진 사람은 해당 인보이스를 볼 수 있으므로 공개 게시하지 않는다. 토큰은 발급 응답에서만 받으며 `listInvoiceViewLinks`로 복원할 수 없다. `revokeInvoiceViewLink`로 폐기한다. 이 기능은 메일 발송을 하지 않는다.

고객의 실패 결제 재시도도 허용하려면 발급 요청에 `allow_payment_retry: true`를 명시한다. 기본값과 기존 링크는 조회 전용이다. 허용한 링크를 가진 사람은 금액을 확인한 뒤 해당 인보이스의 등록 카드로 결제를 다시 요청할 수 있다. 진행 중이거나 승인 또는 취소된 결제는 다시 요청할 수 없다.

## 사전 준비

- Python 3.9 이상.
  - venv 없이 실행된다. macOS 기본 `python3`로 된다.
  - `console.py`만 [uv](https://docs.astral.sh/uv/)가 필요하다 (`brew install uv`).
- 백엔드가 로컬에서 떠 있어야 한다.

```bash
cd backend && ./gradlew bootRun
```

- IntelliJ에서 `MeterEngineApplication`을 실행해도 된다.
- spring-boot-docker-compose가 postgres 컨테이너를 기동하고 Flyway가 시드를 적용한다.
- 고객과 미터는 부트 시드(`backend/src/main/resources/db/migration/R__seed.sql`)에 들어 있다. 따로 주입할 것이 없다.
- `event` 테이블은 append-only 트리거가 걸려 있어 DELETE가 되지 않는다. 잘못 보낸 데이터는 DB를 통째로 지워야 사라진다.

```bash
docker compose down -v
```

## 빠른 시작

- 아래 명령은 모두 레포 루트에서 실행한다.
- `demo/sample-events.csv`는 100행이고 그중 20행이 같은 `transaction_id` 재전송이다. 한 번 보낸 뒤에는 몇 번을 재전송해도 전부 중복이라 DB가 바뀌지 않는다.
- 표본 CSV의 시각은 고정값이다. 백엔드는 35일보다 오래된 시각을 400으로 거절하므로 시간이 지나면 날짜를 옮겨야 한다.

```bash
# 1. 전송 없이 확인 게이트와 미리보기만 본다
python3 demo/meterdemo.py send --csv demo/sample-events.csv --dry-run

# 2. 전송한다. y 입력 게이트를 거치고 마지막에 로그 경로가 찍힌다
python3 demo/meterdemo.py send --csv demo/sample-events.csv

# 3. 실시간 유입처럼 천천히 보낸다 (100건에 2분쯤)
python3 demo/meterdemo.py send --csv demo/sample-events.csv --yes --interval 0.7 --jitter 0.5

# 4. 2에서 찍힌 로그 경로로 검증한다
python3 demo/meterdemo.py verify --log demo/logs/send-<타임스탬프>.jsonl
echo $?

# 5. CSV를 소스로 검증한다 (서버 판정 시뮬레이션)
python3 demo/meterdemo.py verify --csv demo/sample-events.csv

# 6. 전송 기록을 열어 본다
python3 -m json.tool --json-lines demo/logs/send-<타임스탬프>.jsonl | head -40

# 7. 단위 테스트
cd demo && python3 -m unittest && cd ..
```

- 깨끗한 DB에서 위 순서를 밟으면 전부 일치가 나온다. DB에 이전 이벤트가 남아 있으면 불일치와 함께 소스 밖 건수가 진단에 찍힌다.
- `verify`는 신규 저장이 담긴 로그로 한다. 재전송 로그(전부 중복)를 넣으면 "저장될 이벤트가 없습니다"로 끝난다.
- 경계 케이스는 `demo/sample-events-edge.csv`(10행, 전부 정상 저장)로 같은 순서를 반복한다.
  - KST 월 경계, UTC 표기 타임스탬프, 소수 토큰 절사, 저장은 되지만 집계에서 빠지는 행.
  - `verify`가 9월과 10월을 이어서 검증하는 것도 이 샘플에서 보인다.

## 명령

```
python3 demo/meterdemo.py send   --csv <CSV> [옵션]
python3 demo/meterdemo.py verify (--log <JSONL> | --csv <CSV>) [옵션]
```

### 공통 옵션

| 옵션 | 기본값 | 내용 |
|---|---|---|
| `--base-url` | `http://localhost:8080` | 백엔드 주소. `verify`는 로그 헤더 값을 우선한다 |
| `--api-key` | 환경변수 `METERENGINE_API_KEY`, 없으면 로컬 live 키 | `Authorization: Bearer`로 보내는 API 키 |
| `--environment` | 환경변수 `METERENGINE_ENVIRONMENT`, 없으면 키의 환경 | `test` 또는 `live`. 키와 다른 환경 요청은 거절된다. |
| `--timeout` | `10` | HTTP 타임아웃 초 |
| `--no-color` | 끔 | ANSI 색 비활성화 |

### API 키

- 형식이 틀리면 아무것도 보내지 않고 끝난다.
- 로컬 키는 `backend/src/main/resources/db/local/R__local_credentials.sql`에 있다. 배포 환경 키는 운영자 세션으로 발급한다(스코프 `ingest`, `read`).
  - 로컬 시드 키는 기존 데이터(legacy) 범위 키다. test나 live 환경 대상에는 콘솔에서 그 업무 환경을 고른 뒤 발급한 키를 쓴다. 시드 키에 환경을 지정하면 403 `environment_mismatch`다.
- 시작할 때 `GET /v1/organization`으로 키의 도입사를 확인한다. 이때나 `send` 전송 중에 401이나 403을 받으면 code를 보여 주고 멈춘다. 종료 코드는 2다.
- 화면과 기록에는 키를 `me_live_****abcd`처럼 가려서 보인다. 실행 기록 머리말에는 키 대신 도입사 id(`organization_id`)를 남긴다. 옛 기록의 도입사 값은 읽지 않는다.

### send

- 기본값은 `POST /v1/events` 단건 전송이다. `--batch-size 2`부터 `100`까지는 `POST /v1/event-batches`로 묶어 보내고 행별 판정을 기록한다.
- 전송 중 끊긴 묶음은 `UNKNOWN`, 아직 보내지 않은 행은 `NOT_SENT`로 구분해 원래 순서대로 모두 기록한다. 두 상태의 로그 `outcome`은 `error`이고 `error`에 구분값이 남는다.
- 전송마다 요청 요약과 서버 응답을 `[NEW]` `[DUP]` `[REJ]` 태그로 출력한다.
- 모든 요청과 응답 쌍을 `demo/logs/send-YYYYMMDD-HHMMSS.jsonl`에 항상 기록한다. 끄는 옵션은 없다.
- 전송 전 확인 게이트에서 대상 서버, 도입사 이름과 id, 가린 API 키, 건수, KST 월별 분포, 서버에 이미 있는 건수, 예상 결과(신규/중복/거절)를 보여주고 `y` 입력을 기다린다.
  - 문제로 예측된 행은 미리보기 범위 밖이어도 사유와 함께 표시된다.
- 잘못된 값(빈 필드, 미등록 고객)도 거르지 않고 그대로 보낸다. 판정은 서버가 한다.

| 옵션 | 기본값 | 내용 |
|---|---|---|
| `--csv` | 필수 | 이벤트 CSV 경로 |
| `--batch-size` | `1` | 한 요청에 보낼 이벤트 수. 1부터 100까지 |
| `--interval` | `0` | 전송 간격 초 |
| `--jitter` | `0` | 간격에 더할 랜덤 슬립 상한 초 |
| `--yes` | 끔 | 확인 게이트 생략 |
| `--dry-run` | 끔 | 게이트와 미리보기만. 전송도 로그 기록도 하지 않는다. 도입사 확인과 미리보기 때문에 백엔드에는 연결한다 |
| `--verbose` | 끔 | 요청과 응답 JSON 전문도 출력 |

### verify

- Python Decimal로 고객별 사용량과 청구 예정액을 독립 계산해 `GET /v1/usage`, `GET /v1/invoices/draft`와 비교한다. 미터, 월별 공통 단가, 고객별 단가와 요금제는 별도 조회 API에서 받는다.
- KST 월별로 `sum`, `count`, `count_distinct`, `min`, `max`, `avg`, `first`, `last`, `median`, `percentile`을 계산하고 `unit_size`로 나눈 뒤 반올림한다. COUNT는 전체 이벤트, COUNT DISTINCT는 숫자, 문자열, 불리언의 고유 값을 센다. 나머지는 숫자만 집계한다. 중복, 거절과 청구 계산 시각 이후 이벤트는 제외하고, 원본 집계값이 음수인 조합은 과금하지 않는다.
- `per_unit`, `graduated`, `volume`을 계산하며 각 과금 라인의 10원 미만을 절사한다. 고객 단가가 공통 단가보다 우선하고, 양수 사용량이 있으면 최소 과금을 적용한 뒤 기본료를 더한다. 단가 없는 조합은 별도로 표시한다.
- 고객 전체 집계와 조합별 청구 수량은 따로 검증한다. 평균, 고유 값 수와 반올림 결과는 조합별 합과 다를 수 있다. 표시용 소수와 별개로 정확한 분자와 분모로 금액과 구간 경계를 계산한다. 숫자 차원의 `9`와 `9.0`은 같은 조합이다.
- `--log`와 `--csv`를 함께 주면 로그를 쓴다. 깨진 `send` 로그는 종료 코드 2, 여러 브리지 헤더와 도입사 불일치는 경고로 표시한다. CSV는 서버 판정을 예측하므로 NUL 같은 일부 저장 거절은 재현하지 못한다.

| 옵션 | 기본값 | 내용 |
|---|---|---|
| `--log` | | `send`나 브리지가 남긴 JSONL |
| `--csv` | | 원본 데이터 CSV |
| `--month` | 소스의 모든 월 | 검증할 월 `yyyy-MM` |

### 종료 코드

| 코드 | 뜻 |
|---|---|
| 0 | 전부 일치. `send`는 정상 완료 |
| 1 | 불일치 있음 |
| 2 | 실행 오류 (서버 미기동, 소스 없음, `send` 로그 중간 손상) |

### CSV 스키마

- 헤더 행이 필수이고 컬럼 순서는 무관하다.

| 컬럼 | 내용 |
|---|---|
| `transaction_id` | 멱등 키. 같은 값 재전송은 중복 처리 |
| `customer_id` | 고객 UUID. 시드가 발급한 `customer.id` |
| `event_type` | 미터의 `event_type`. 부트 시드에 `chat_completion`, `llm_request`, `network_traffic`이 있다 |
| `timestamp` | RFC 3339, 오프셋 포함. `occurred_at`이 된다 |
| `properties` | JSON 객체 문자열. CSV 셀 안에서는 큰따옴표를 두 번 쓴다 |
| `note` | 선택. 행 설명이고 콘솔 출력에만 쓴다. 서버로 보내지 않는다 |

```csv
transaction_id,customer_id,event_type,timestamp,properties,note
demo-001,35bc8d12-9d38-57ab-bc9b-bbd35d779a26,llm_request,2026-10-05T10:00:00+09:00,"{""input_tokens"": 101, ""output_tokens"": 51, ""model"": ""claude-sonnet-5""}",
demo-bad,9f31c2aa-0000-0000-0000-000000000000,llm_request,2026-10-05T11:00:00+09:00,"{""input_tokens"": 120, ""model"": ""claude-sonnet-5""}",미등록 고객
```

## otel_bridge.py

Claude Code가 OTLP로 보낸 사용량을 받아 `POST /v1/events`로 흘려보내는 로컬 상주 프로세스. 백엔드와 배포 구성은 건드리지 않는다.

```
Claude Code --UserPromptSubmit hook--> 브리지: 세션 abc는 meterengine 폴더
            --OTLP/JSON--------------> 브리지: {session.id: abc, input_tokens: ...}
                                          |
                                          v
                                POST <base_url>/v1/events
```

### 설치

```bash
# 1. 누가 어디로 보낼지 정한다 (~/.meterengine/bridge.json)
python3 demo/otel_bridge.py config --owner "박성종" --allow "meterengine,meterengine-demo" --api-key <키>

# 2. ~/.claude/settings.json에 OTel 설정과 hook을 병합한다 (기존 내용은 보존된다)
python3 demo/otel_bridge.py setup
```

- `setup`은 반영 내용을 먼저 보여주고 `y`를 기다린다. `--yes`로 생략한다.
- 첫 반영 때 원본 백업을 남긴다. 다시 실행해도 덮어쓰지 않는다.
- 새 Claude 세션부터 적용된다.
- API 키 없이 등록해 둔 브리지는 뜨지 못한다. 키를 저장하고 껐다 켠다.

```zsh
read -rs '?API 키: ' api_key && echo
python3 demo/otel_bridge.py config --api-key "$api_key"
python3 demo/otel_bridge.py stop && python3 demo/otel_bridge.py start
```

- 첫 줄은 zsh 문법이다. bash에서는 `read -rsp 'API 키: ' api_key && echo`를 쓴다.

| `config` 옵션 | 내용 |
|---|---|
| `--owner` | 이 기계의 주인. 고객 이름에 들어간다 |
| `--base-url` | 전송 대상. 기본 `http://localhost:8080` |
| `--api-key` | API 키. 설정 파일에 저장하고 출력에는 가려서 보인다 |
| `--allow` | 실명으로 보낼 레포 이름들, 쉼표 구분. 비우면 전부 실명 |
| `--deny` | 아예 보내지 않을 레포 이름들, 쉼표 구분 |
| `--fallback-project` | 허용 목록 밖 프로젝트를 합칠 이름 |
| `--config` | 설정 파일 경로. 기본 `~/.meterengine/bridge.json` |

- 설정 파일은 권한 600으로 쓴다. 옛 설정 파일의 도입사 값은 읽지 않는다.
- 키는 `METERENGINE_API_KEY`, 설정 파일 순서로 고른다. 둘 다 없으면 안내를 띄우고 시작하지 않는다.
  - launchd로 띄운 브리지는 환경변수를 받지 않는다. 그래서 설정 파일에 키가 없으면 `install`과 `start`를 거부한다. `console.py`의 등록과 시작도 같다.
- 설정을 바꿔도 이미 도는 브리지에는 반영되지 않는다. `stop` 후 `start`를 한다.
- 배포 주소는 로컬에서 확인한 뒤 손으로 적는다.

```bash
python3 demo/otel_bridge.py config --base-url https://meterengine.com
```

### 실행

```bash
python3 demo/otel_bridge.py serve       # 포그라운드
python3 demo/otel_bridge.py install     # launchd에 등록해 로그인할 때 자동 시작
python3 demo/otel_bridge.py uninstall   # 자동 시작 해제
python3 demo/otel_bridge.py start       # 켜기
python3 demo/otel_bridge.py stop        # 끄기
python3 demo/otel_bridge.py status      # 상태와 누적 건수
```

- 모든 하위 명령이 `--host`(기본 `127.0.0.1`)와 `--port`(기본 `4318`)를 받는다. `config`는 받지 않는다.
- `serve`는 `--config`, `--state`(기본 `~/.meterengine/state.json`), `--base-url`을 추가로 받는다. `--base-url`은 이번 실행에만 적용된다.
- 키의 도입사는 첫 전송 때 `GET /v1/organization`으로 확인한다. 실패하면(연결 실패, 401, 403) 브리지는 떠 있고 이벤트를 보내지 않은 채 오류로 센 뒤 60초 뒤에 다시 확인한다. 확인한 뒤 전송이 401이나 403을 받아도(키 폐기, 도입사 정지) 그 이벤트를 거절로 세고 같은 방식으로 다시 확인한다. `status`의 `organization_error`에 마지막 실패 code가 나온다.
- `install`은 워크트리에서 실행하면 거부한다. 본 저장소의 `demo/otel_bridge.py`로 등록한다. `--force`로 무시할 수 있다.
- `status`는 브리지가 응답하지 않으면 종료 코드 1로 끝난다.
- 브리지가 꺼져 있으면 Claude Code는 그대로 동작하고 사용량만 수집되지 않는다.

### 브리지가 여는 포트

- `127.0.0.1:4318`에만 바인딩하고 인증은 없다.

| 경로 | 받는 것 |
|---|---|
| `/v1/logs` | Claude Code OTLP exporter의 사용량 |
| `/meterengine/session` | `UserPromptSubmit` hook의 세션과 폴더 매핑 |
| `/meterengine/health` | `status`와 `console.py`가 읽는 상태 |
| `/meterengine/config` | 테스트 웹의 설정 조회(GET)와 저장(PUT) |
| `/meterengine/restart` | 테스트 웹의 재시작 요청(POST) |

- `PUT /meterengine/config`는 바꿀 필드만 받아 설정 파일에 쓰고 `{"saved": true, "restart_required": true}`로 답한다.
  - 모르는 필드나 타입이 틀린 값은 400과 `field`로 답하고 파일을 바꾸지 않는다.
- `POST /meterengine/restart`는 `install`이나 `start`로 launchd가 띄운 브리지만 받는다. 202 `{"restarting": true}`로 답하고 내려가면 launchd가 다시 띄운다.
  - `serve`로 띄운 브리지는 409 `{"error": "not_launchd"}`로 답한다.

- 브라우저가 보낸 요청은 받지 않는다. `Origin`이 붙어 있거나 `Sec-Fetch-Site`가 `none`이 아니거나 `Host`가 이 기계를 가리키지 않으면 403이다.
- 세션 매핑은 하루 동안만 붙들고 버린다. 버려도 다음 프롬프트에서 다시 묶인다.

### 고객 지정

- 고객 이름은 `<프로젝트>(<주인>)` 형식이다. 예: `meterengine(박성종)`.
- 프로젝트 이름은 git 레포 루트 이름이다.
  - `--git-common-dir`을 보므로 워크트리에서 일해도 전부 한 이름으로 모인다.
- `allow`에 적은 레포만 실명이고 나머지는 `fallback_project` 하나로 합쳐진다. `allow`가 비면 전부 실명이다.
- `deny`에 적은 레포는 보내지 않는다. hook이 세션을 묶을 때와 전송 직전에 각각 본다.
- 고객은 이름을 `external_id`로 실어 `POST /v1/customers`로 등록한다. 같은 도입사와 외부 ID면 200으로 기존 고객을 받아 여러 프로세스가 등록해도 중복 고객을 만들지 않는다.
- 찾아낸 고객 id는 `~/.meterengine/state.json`에 전송 대상, 도입사 id와 API 키 해시별로 담는다. 키를 바꾸면 고객을 다시 찾고, 서버가 404 `customer_not_found`로 답하면 캐시를 버린다.

### 보내는 이벤트와 properties

| OTel 이벤트 | event_type | 집계 |
|---|---|---|
| `api_request` | `llm_request` | 된다. 아래 "단가"의 미터에 잡힌다 |
| `tool_result` | `tool_call` | 안 된다. 미터가 없어 저장만 된다 |
| `api_error` | `llm_error` | 안 된다 |
| `api_refusal` | `llm_refusal` | 안 된다 |

- 미터가 없는 이벤트도 저장은 된다. 나중에 미터를 만들면 이미 쌓인 이벤트가 그때부터 함께 집계된다.
- 키의 점은 언더스코어로 바꾼다 (`agent.name` -> `agent_name`).
- 수치로 쓸 키는 JSON number로 못박아 보낸다. 같은 이름이라도 이벤트마다 OTLP 타입이 달라서(`duration_ms`가 `api_request`에서는 정수, `tool_result`에서는 문자열) 그대로 두면 미터가 조용히 집계에서 뺀다.
- 보내지 않는 것: `user.email`, `user.id`, `user.account_uuid`, `user.account_id`, `organization.id`.
  - 프롬프트와 응답 본문(`user_prompt`, `assistant_response`)도 보내지 않는다.

```json
{
  "model": "claude-opus-5", "speed": "normal", "effort": "high",
  "query_source": "sdk", "terminal_type": "Orca",
  "input_tokens": 2, "output_tokens": 3,
  "cache_read_tokens": 41268, "cache_creation_tokens": 7614,
  "cost_usd": 0.096859, "duration_ms": 1187,
  "project": "meterengine", "owner": "박성종",
  "request_id": "req_...", "session_id": "...", "otel_event": "api_request"
}
```

### 단가

- 단가는 `backend/src/main/resources/db/migration/R__seed.sql`이 정한다.
- `llm_request` 이벤트 하나가 `input-tokens`, `output-tokens`, `cache-creation-tokens`, `cache-read-tokens` 미터에 함께 잡힌다.
- `input-tokens`, `output-tokens`는 차원 키가 `model`이라 모델마다 단가가 다르다.
  - Claude 공시가(달러, 100만 토큰당)를 1달러 1,590원으로 환산한 토큰당 원 단가다. 1,590원은 Claude 결제 창의 부가세 포함 원화 가격을 1.1로 나눈 환율이다. 인보이스가 세액 10%를 따로 더하기 때문이다.
  - Claude Haiku 5.5는 프롬프트가 100K 토큰을 넘으면 가격이 오르지만, 토큰당 단가 하나로는 나타낼 수 없어 기본 구간 가격만 넣었다.
  - 시드에는 배포 서버 이벤트와 샘플 CSV에 나온 모델의 단가가 있다. `model` 값이 글자까지 같아야 하고, 목록에 없는 모델은 청구 예정액에서 빠진다.
- `cache-creation-tokens`, `cache-read-tokens`는 모델과 상관없이 단가 한 행이고, 예전 기준(Claude Opus 5 공시가, 1달러 1,400원)을 그대로 둔다.
  - 캐시 쓰기 가격은 5분 캐시와 1시간 캐시가 다른데 OTel의 `cache_creation_tokens`가 둘을 구분하지 않는다. 그래서 캐시 미터를 모델별 단가로 바꾸는 일은 미뤘다. 지금 값은 5분 캐시 기준이다.
- 금액은 실제 가격이라 작다. 라인마다 10원 미만을 버리므로 입력 토큰처럼 적은 항목은 0원이 되기도 한다.

### 검증

- 브리지는 `send`와 같은 JSONL 포맷으로 `demo/logs/bridge-YYYYMMDD.jsonl`에 하루 한 파일로 이어쓴다. 레코드 형식과 outcome 값의 정본은 `demo/core/jsonl_log.py`다.
- 재시작할 때마다 실행 헤더가 하나씩 더 붙고, 읽는 쪽은 마지막 헤더를 그 파일의 헤더로 본다.

```bash
python3 demo/meterdemo.py verify --log demo/logs/bridge-20260824.jsonl
```

- 서버에 소스 밖 이벤트(이전 CSV 전송분)가 있으면 그 고객은 불일치로 나오고 몇 건이 소스 밖인지 진단에 찍힌다.

## console.py

브리지를 화면으로 다루는 TUI. 명령줄과 같은 코드(`bridge/admin.py`)를 쓴다.

```bash
uv run demo/console.py
```

- 의존성이 파일 안에 적혀 있어(PEP 723) uv가 받아서 실행한다. demo의 나머지는 `python3`로 돈다.
- `--config`, `--state`를 받는다.
- 상태가 2초마다 갱신된다. 가린 API 키와 도입사 확인 결과(실패하면 code)가 함께 나온다.
- 설정에 적은 프로젝트와 브리지가 실제로 본 프로젝트가 함께 뜬다. 콘솔을 켜 둔 사이에 처음 본 레포도 다음 갱신에 목록으로 붙는다.
- 커서를 놓고 스페이스를 누르면 세 상태를 돈다.

```
● meterengine        실명으로 보냄
○ notes              기타 프로젝트로 합침
✕ 비밀레포            보내지 않음
```

| 키 | 동작 |
|---|---|
| `s` | 시작 |
| `x` | 중지 |
| `w` | 설정 저장 |
| `space` | 상태 전환 |
| `k` | Claude 설정 |
| `i` | 자동시작 등록 |
| `u` | 등록 해제 |
| `q` | 종료 |

- 허용 목록이 비면 모든 프로젝트가 실명으로 간다. 목록을 전부 합침으로 바꾸면 화면이 규칙 줄에 그렇게 알린다.

## sdk

백엔드 API를 부르는 TypeScript 클라이언트 `@meterengine/sdk`. 배포하지 않고 이 workspace 안에서만 쓴다.

- 쓰는 패키지의 `package.json`에 `"@meterengine/sdk": "workspace:*"`를 넣고 `demo/`에서 `corepack pnpm install`을 한다. 빌드 없이 `.ts` 소스를 그대로 import한다.
  - Next.js에서 쓸 때는 `next.config.ts`의 `transpilePackages`에 넣는다.
  - 새로 쓰는 패키지는 `pnpm-workspace.yaml`의 `packages`에 더하고 `tsconfig.json`에 `allowImportingTsExtensions`(와 `noEmit`)를 켠다. SDK 소스가 `./errors.ts`처럼 확장자를 붙여 import한다.

```ts
import { MeterEngineClient } from "@meterengine/sdk";

const client = new MeterEngineClient({ baseUrl: "http://localhost:8080", apiKey: "<API 키>" });
await client.ingestEvent({
  customer_id: "<고객 UUID>",
  type: "llm_request",
  timestamp: new Date().toISOString(),
  properties: { input_tokens: 101, model: "claude-sonnet-5" },
});
```

- 요청마다 `Authorization: Bearer <API 키>`를 붙인다.
- `environment: "test"` 또는 `"live"`를 지정하면 모든 요청에 환경 헤더를 붙인다. 생략하면 API 키의 환경을 사용한다. 같은 조직이라도 다른 환경의 키와 고객을 섞어 쓸 수 없다.
- 재시도
  - 연결 오류, 타임아웃(기본 10초), 429, 5xx만 다시 보낸다. 기본 3번까지다(`maxRetries`).
  - `Retry-After`가 있으면 그만큼 기다리고, 없으면 최대 5초인 지수 백오프에 무작위 지연을 섞는다.
  - 나머지 4xx는 한 번만 보낸다.
  - 이벤트를 다시 보낼 때도 `transaction_id`가 같아 서버가 중복으로 처리한다. 생략하면 SDK가 UUID를 만든다.
- 고객, 사용량, 청구 예정과 인보이스, 결제, 단가와 요금제, 웹훅 전송 기록을 API로 조회한다. 고객 생성은 `external_id`를 줄 때만 자동 재시도하고, 카드 등록 링크 생성은 자동 재시도하지 않는다.
- 이벤트 목록은 월 또는 `from`/`to` 기간과 검색/속성/정렬을 받는다. `listEventTypes`는 같은 기간과 고객/검색/속성 조건의 실제 유형을, `getEvent(transactionId)`는 기간과 무관한 단건을 조회한다. 기간 응답의 `month`는 null이다.
  - `properties` 객체는 JSON으로 직렬화하고, JSON 문자열은 그대로 보낸다. 서버는 JSON 객체 여부를 검증한다. 고객, 카드와 요금제 목록은 `status`(`active`, `archived`, `all`)를 받지만 미터별 가격 목록은 상태 필터를 받지 않는다.
- 배치: `ingestEventBatch`는 100건씩 나눠 `POST /v1/event-batches`로 보내고, 결과의 `index`를 입력 전체의 순서로 맞춰 합친다. 중간 묶음이 실패하면 `MeterEngineBatchError`의 `results`에 앞 묶음 결과가 남는다.
- 웹훅 검증: `verifyWebhookSignature({ payload, headers, secret })`는 Standard Webhooks v1 서명과 시각 차(기본 300초까지)를 확인하고 `{environment, type, timestamp, data}`를 돌려준다. `environment`는 `legacy`, `test`, `live` 중 하나다. 실패하면 `WebhookVerificationError`를 던진다.
  - `payload`는 받은 본문 문자열 그대로 넘긴다. JSON을 파싱해 다시 직렬화하면 서명이 맞지 않는다.
  - `signWebhookPayload`는 같은 규칙으로 서명을 만든다. 시험 전송에 쓴다.

## 테스트 웹

도입사 서버가 API를 조회해 고객의 사용량과 청구, 수납 화면을 그리는 Next.js 앱(`web/`)이다. 이벤트 전송과 웹훅 수신 도구도 함께 둔다. `127.0.0.1:3100`에만 붙는다.

- 사전 준비: Node 24 이상, corepack.

```bash
cd demo
corepack pnpm install
cp web/.env.example web/.env.local     # 아래 변수를 채운다
corepack pnpm --filter @meterengine/demo-web dev
```

| 변수 | 내용 |
|---|---|
| `METERENGINE_API_KEY` | `http://localhost:8080` 백엔드에 쓸 API 키. 대상 이름은 `local` |
| `METERENGINE_ENVIRONMENT` | 단일 대상의 `test` 또는 `live`. 비우면 API 키의 환경을 사용한다 |
| `METERENGINE_TARGETS` | `[{"name": "local-test", "base_url": "http://localhost:8080", "api_key": "...", "environment": "test", "webhook_secret": "whsec_..."}]`. `api_key`는 그 `environment`에서 발급한 키여야 한다. API 키와 웹훅 비밀값은 서버에서만 쓴다. 단일 대상 설정보다 먼저 적용한다 |
| `METERENGINE_LEGACY_WEBHOOK_TARGET` | 기존 전역 비밀값과 수신 기록을 사용할 원래 무환경 대상 이름. 예: `e2e` |
| `METERENGINE_WEBHOOK_SECRET` | 위에서 지정한 기존 대상의 웹훅 비밀값. 새 `test/live` 대상에는 적용하지 않는다 |
| `METERENGINE_BRIDGE_URL` | 브리지 주소. 기본 `http://127.0.0.1:4318` |
| `METERENGINE_CONSOLE_URL` | 공개 카드 등록 화면이 있는 콘솔 주소. 기본 `http://127.0.0.1:3000` |

| 화면 | 경로 | 하는 일 |
|---|---|---|
| 개요 | `/` | 대상마다 연결 여부와 고객 수, 브리지 상태 |
| 고객 사용량과 납부 | `/customers` | 고객과 월 선택, 사용량, 청구 예정과 확정 내역, 수납 상태, 카드 등록 링크 |
| 이벤트 보내기 | `/events` | 한 건을 `ingestEvent`로 보내고 응답을 본다 |
| CSV 보내기 | `/events/csv` | 위 CSV 스키마의 파일을 `ingestEventBatch`로 보내고 행별 결과를 본다 |
| 웹훅 수신 | `/webhooks` | 대상을 선택해 해당 수신 URL과 서명 검증 기록 조회 |
| 브리지 설정 | `/bridge` | 브리지 설정 조회, 저장, 재시작 |

- 키는 시연 서버에만 두고 브라우저에 전달하지 않는다. 조회에는 `read`, 이벤트 전송에는 `ingest`, 카드 등록 링크 생성에는 `admin` 스코프가 필요하다.
- 고객 화면의 카드는 공개 등록 화면에서 고객이 동의하고 인증한 뒤 등록된다. 링크 생성이나 화면 조회만으로 카드 등록과 결제 성공을 판정하지 않는다.

### 웹훅 받기

대상마다 `webhook_secret`과 `?target=대상이름` 수신 URL을 사용한다. 등록 응답의 비밀값을 해당 대상 설정에 저장하고 서버를 다시 띄운다. 아래 `<API 키>`는 대상과 같은 환경의 키여야 한다. 기존 데이터 범위의 시드 키로 등록한 수신처는 legacy 통보만 보내 test, live 대상에서 `environment_mismatch`로 거절된다.

```bash
curl -X POST http://localhost:8080/v1/webhook-endpoints \
  -H "Authorization: Bearer <API 키>" -H "Content-Type: application/json" \
  -d '{"url": "http://127.0.0.1:3100/api/webhooks?target=local-test", "event_types": ["webhook_test"]}'

curl -X POST http://localhost:8080/v1/webhook-endpoints/<id>/test-events \
  -H "Authorization: Bearer <API 키>"
```

- 서명 검증 뒤 본문의 `environment`가 선택한 대상과 같은지 확인한다. 대상이 없거나 중복된 URL은 거절한다.
- 기록은 대상 이름, API 주소, 환경과 키별로 분리한다. 같은 `webhook-id`의 재전송도 수신 기록에 남으므로 업무 처리의 중복 방지는 별도로 한다.
- 기존 `.data/webhooks.jsonl`은 `METERENGINE_LEGACY_WEBHOOK_TARGET`으로 지정한 원래 legacy 연결에만 고정한다. 기존 파일을 복사하거나 `test/live`에 자동 배정하지 않는다. 연결 키나 주소를 바꾼 뒤에도 원래 파일은 보존한다.
- 백엔드 없이 검증하려면 대상 설정을 담은 비공개 환경 파일로 시험 통보를 보낸다.

```bash
cd demo/web
node --env-file=.env.local scripts/send-test-webhook.ts --target=local-test           # 맞는 서명
node --env-file=.env.local scripts/send-test-webhook.ts --target=local-test --tamper  # 본문을 바꿔 서명 불일치
node --env-file=.env.local scripts/send-test-webhook.ts --target=local-test --old     # 10분 전 시각
```

## 파일 구성

| 경로 | 내용 |
|---|---|
| `meterdemo.py` | CSV 데모/검증 진입점 (`send`, `verify`) |
| `otel_bridge.py` | 브리지 명령줄 진입점 |
| `console.py` | 브리지 화면 진입점 (uv로 실행) |
| `core/` | 양쪽이 함께 쓰는 것. 이벤트 모델, 백엔드 HTTP 래퍼, 전송 기록 JSONL |
| `csvdemo/` | CSV를 흘려보내고 대조하는 쪽 |
| `bridge/` | Claude Code 사용량을 보내는 쪽 |
| `sdk/` | TypeScript SDK (`@meterengine/sdk`) |
| `web/` | 테스트 웹 (`@meterengine/demo-web`) |
| `llm-service/` | Lumen LLM 서비스 예제 (`@meterengine/llm-service`) |
| `package.json`, `pnpm-workspace.yaml`, `pnpm-lock.yaml` | `sdk/`, `web/`, `llm-service/`를 묶는 pnpm workspace |
| `eslint-naming-rules.mjs` | `sdk/`, `web/`, `llm-service/`가 같이 쓰는 식별자 이름 규칙(`@typescript-eslint/naming-convention`) |
| `sample-events.csv`, `sample-events-edge.csv` | 샘플 이벤트와 경계 케이스 |
| `logs/` | 전송 기록 (gitignore) |

- 진입점이 최상위에 있어야 `python3 demo/meterdemo.py`로 실행할 때 `demo/`가 import 경로가 된다.
- 테스트는 각 패키지 안에 있다.

```bash
cd demo && python3 -m unittest discover
```

- 콘솔 테스트(`test_console.py`)는 textual이 있어야 돌고 없으면 건너뛴다. 전부 돌리려면 아래를 쓴다.

```bash
cd demo && uv run --with textual python3 -m unittest
```

- SDK와 테스트 웹의 테스트는 아래로 돌린다.

```bash
cd demo && corepack pnpm -r test
```
