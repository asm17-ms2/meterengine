# demo

MeterEngine 수집-조회 데모 도구. CSV 이벤트를 백엔드로 보내고(`meterdemo.py send`), 독립 계산한 기대값과 서버 응답을 대조하고(`meterdemo.py verify`), Claude Code 사용량을 실시간으로 흘려보낸다(`otel_bridge.py`).

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
- `demo/sample-events.csv`를 한 번 보낸 뒤 나오는 값 (전부 2026년 8월 귀속).

| 고객 | 사용량 | 청구 예정액 |
|---|---|---|
| 이슬비랩스 | input 4,820, output 2,820 | 33 + 98 = 131원 |
| 도담헬스 | input 5,010 | 35원 |
| 한들물류 | egress 25.0GB | 3,000원 |
| 아크메 주식회사, 베타 스튜디오 | 없음 | 0원 |

- 금액은 라인마다 버림(ROUND_DOWN)이라 33.74원이 33원이 된다.
- 캐시 미터는 이 CSV에 해당 속성이 없어 0원이고 브리지가 보내는 이벤트에서만 잡힌다.
- 경계 케이스는 `demo/sample-events-edge.csv`(10행, 전부 정상 저장)로 같은 순서를 반복한다.
  - KST 월 경계, UTC 표기 타임스탬프, 소수 토큰 절사, 저장은 되지만 집계에서 빠지는 행.
  - `verify`가 8월과 9월을 이어서 검증하는 것도 이 샘플에서 보인다.

## 명령

```
python3 demo/meterdemo.py send   --csv <CSV> [옵션]
python3 demo/meterdemo.py verify (--log <JSONL> | --csv <CSV>) [옵션]
```

### 공통 옵션

| 옵션 | 기본값 | 내용 |
|---|---|---|
| `--base-url` | `http://localhost:8080` | 백엔드 주소. `verify`는 로그 헤더 값을 우선한다 |
| `--org-id` | 시드 도입사 | `X-Organization-Id` 헤더 |
| `--timeout` | `10` | HTTP 타임아웃 초 |
| `--no-color` | 끔 | ANSI 색 비활성화 |

### send

- CSV의 이벤트를 `POST /v1/events`로 순차 전송한다.
- 전송마다 요청 요약과 서버 응답을 `[NEW]` `[DUP]` `[REJ]` 태그로 출력한다.
- 모든 요청과 응답 쌍을 `demo/logs/send-YYYYMMDD-HHMMSS.jsonl`에 항상 기록한다. 끄는 옵션은 없다.
- 전송 전 확인 게이트에서 대상 서버, 도입사, 건수, KST 월별 분포, 서버에 이미 있는 건수, 예상 결과(신규/중복/거절)를 보여주고 `y` 입력을 기다린다.
  - 문제로 예측된 행은 미리보기 범위 밖이어도 사유와 함께 표시된다.
- 잘못된 값(빈 필드, 미등록 고객)도 거르지 않고 그대로 보낸다. 판정은 서버가 한다.

| 옵션 | 기본값 | 내용 |
|---|---|---|
| `--csv` | 필수 | 이벤트 CSV 경로 |
| `--interval` | `0` | 전송 간격 초 |
| `--jitter` | `0` | 간격에 더할 랜덤 슬립 상한 초 |
| `--yes` | 끔 | 확인 게이트 생략 |
| `--dry-run` | 끔 | 게이트와 미리보기만. 전송도 로그 기록도 하지 않는다 |
| `--verbose` | 끔 | 요청과 응답 JSON 전문도 출력 |

### verify

- 소스에서 이벤트를 읽어 Python이 Decimal로 독립 계산한 고객별 사용량과 청구 예정액을 `GET /v1/usage`, `GET /v1/invoices/draft` 응답과 표로 나란히 비교한다.
- 미터 정의(`event_type`, `target_property`, 단가)는 서버 응답에서 유도해 화면에 출력한다.
- 독립 계산 규칙.
  - 중복 `transaction_id` 제거. 로그 소스는 서버 판정 그대로 `outcome=new`만 집계한다.
  - `occurred_at`의 KST 자정 경계로 월 귀속.
  - 4xx 거절분 제외.
  - `target_property` 값이 JSON number가 아닌 이벤트 제외. 저장은 되지만 합산에서 빠진다.
  - 청구 예정액은 수량 x 단가를 라인마다 절사한 정수.
- `--log`와 `--csv`를 둘 다 주면 로그를 쓴다.
- CSV 소스는 서버 판정을 시뮬레이션한다. 필수 필드 검증과 서버 고객 명단 대조로 거절을 예측하되 `invalid_event` 같은 희귀 거절은 예측하지 못하고 그 한계를 출력에 적는다.
- 중간이 깨진 `send` 로그로는 판정하지 않고 종료 코드 2로 끝낸다. 헤더가 여럿인 브리지 로그는 경고까지만 한다.

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
demo-001,35bc8d12-9d38-57ab-bc9b-bbd35d779a26,llm_request,2026-08-05T10:00:00+09:00,"{""input_tokens"": 101, ""output_tokens"": 51}",
demo-bad,9f31c2aa-0000-0000-0000-000000000000,llm_request,2026-08-05T11:00:00+09:00,"{""input_tokens"": 120}",미등록 고객
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
python3 demo/otel_bridge.py config --owner "박성종" --allow "meterengine,meterengine-demo"

# 2. ~/.claude/settings.json에 OTel 설정과 hook을 병합한다 (기존 내용은 보존된다)
python3 demo/otel_bridge.py setup
```

- `setup`은 반영 내용을 먼저 보여주고 `y`를 기다린다. `--yes`로 생략한다.
- 첫 반영 때 원본 백업을 남긴다. 다시 실행해도 덮어쓰지 않는다.
- 새 Claude 세션부터 적용된다.

| `config` 옵션 | 내용 |
|---|---|
| `--owner` | 이 기계의 주인. 고객 이름에 들어간다 |
| `--base-url` | 전송 대상. 기본 `http://localhost:8080` |
| `--org-id` | `X-Organization-Id` |
| `--allow` | 실명으로 보낼 레포 이름들, 쉼표 구분. 비우면 전부 실명 |
| `--deny` | 아예 보내지 않을 레포 이름들, 쉼표 구분 |
| `--fallback-project` | 허용 목록 밖 프로젝트를 합칠 이름 |
| `--config` | 설정 파일 경로. 기본 `~/.meterengine/bridge.json` |

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

- 브라우저가 보낸 요청은 받지 않는다. `Origin`이 붙어 있거나 `Sec-Fetch-Site`가 `none`이 아니거나 `Host`가 이 기계를 가리키지 않으면 403이다.
- 세션 매핑은 하루 동안만 붙들고 버린다. 버려도 다음 프롬프트에서 다시 묶인다.

### 고객 지정

- 고객 이름은 `<프로젝트>(<주인>)` 형식이다. 예: `meterengine(박성종)`.
- 프로젝트 이름은 git 레포 루트 이름이다.
  - `--git-common-dir`을 보므로 워크트리에서 일해도 전부 한 이름으로 모인다.
- `allow`에 적은 레포만 실명이고 나머지는 `fallback_project` 하나로 합쳐진다. `allow`가 비면 전부 실명이다.
- `deny`에 적은 레포는 보내지 않는다. hook이 세션을 묶을 때와 전송 직전에 각각 본다.
- 고객은 브리지가 만든다. `GET /v1/customers`로 같은 이름을 찾고 없을 때만 `POST /v1/customers`로 등록한다.
- 찾아낸 고객 id는 `~/.meterengine/state.json`에 담는다. 서버가 404 `customer_not_found`로 답하면 그 자리에서 캐시를 버리고 다음 이벤트에서 다시 찾는다.

### 보내는 이벤트와 properties

| OTel 이벤트 | event_type | 집계 |
|---|---|---|
| `api_request` | `llm_request` | 된다. 아래 단가 표의 미터에 잡힌다 |
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

- Anthropic 공시가를 역산한 값이다. 기준은 Claude Opus 5, 1 MTok = 100만 토큰, 1달러 1,400원.

| 미터 | 공시가 | 계산 | 원/토큰 |
|---|---|---|---|
| `input-tokens` | $5 / MTok | 5 x 1400 / 1,000,000 | 0.007 |
| `cache-creation-tokens` | $6.25 / MTok | 6.25 x 1400 / 1,000,000 | 0.00875 |
| `cache-read-tokens` | $0.50 / MTok | 0.5 x 1400 / 1,000,000 | 0.0007 |
| `output-tokens` | $25 / MTok | 25 x 1400 / 1,000,000 | 0.035 |

- `llm_request` 이벤트 하나가 이 미터들에 함께 잡힌다.
- 캐시 쓰기는 5분 캐시 기준이다. OTel의 `cache_creation_tokens`가 5분과 1시간을 구분하지 않는다.
- 모델별 단가는 켜지지 않는다. `properties`에 `model`이 실려 있어도 `PriceRateRepository.findBaseUnitPrices`가 `dimension_values = '{}'` 행만 읽는다.
  - 시드에는 기본 단가 한 행만 둔다. 기본 단가 행을 지우면 그 미터가 인보이스에서 통째로 빠진다.
- 금액은 실제 가격이라 작다. 라인마다 절사하므로 입력 토큰처럼 적은 항목은 0원이 되기도 한다.

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
- 상태가 2초마다 갱신된다.
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

## 파일 구성

```
demo/
  meterdemo.py      CSV 데모/검증 진입점 (send, verify)
  otel_bridge.py    브리지 명령줄 진입점 (serve, config, setup, install, uninstall, start, stop, status)
  console.py        브리지 화면 진입점 (uv로 실행)

  core/             양쪽이 함께 쓰는 것
    model.py        KST 상수, Event, RFC3339 파싱, 와이어 바디 조립
    api_client.py   백엔드 HTTP 래퍼
    jsonl_log.py    전송 기록 JSONL 쓰기/읽기, outcome 판정
    files.py        JSON 파일 원자적 쓰기

  csvdemo/          CSV를 흘려보내고 대조하는 쪽
    csvio.py        CSV 소스 읽기
    expected.py     기대값 독립 계산
    render.py       콘솔 출력
    send_cmd.py / verify_cmd.py

  bridge/           Claude Code 사용량을 보내는 쪽
    const.py        경로와 주소
    otel_map.py     OTLP 페이로드를 Event로 변환
    state.py        설정, 세션 매핑, 고객 해석
    server.py       OTLP와 hook을 받는 수집 서버
    admin.py        켜고 끄고 설정하는 일

  sample-events.csv       100행. 신규 80, 중복 20
  sample-events-edge.csv  경계 케이스 10행
  logs/                   전송 기록 (gitignore)
```

- 진입점이 최상위에 있어야 `python3 demo/meterdemo.py`로 실행할 때 `demo/`가 import 경로가 된다.
- 테스트는 각 패키지 안에 있다.

```bash
cd demo && python3 -m unittest discover
```

- 콘솔 테스트(`test_console.py`)는 textual이 있어야 돌고 없으면 건너뛴다. 전부 돌리려면 아래를 쓴다.

```bash
cd demo && uv run --with textual python3 -m unittest
```
