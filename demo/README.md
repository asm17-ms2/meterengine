# demo: 수집-조회 데모/검증 툴 (MS2-128)

이벤트를 실제 HTTP로 백엔드에 흘려보내고(send), 보낸 요청과 서버 응답을 1:1로 대조해 보여주며,
소스 기반으로 독립 계산한 기대값과 서버의 사용량/청구 예정액 응답이 일치하는지 확인한다(verify).
스토리 MS2-121의 인수조건을 사람이 눈으로 확인하는 시연 겸 검증 도구다.

여기에 더해 `otel_bridge.py`가 우리가 Claude Code를 쓰면서 실제로 태운 토큰을 사용량
이벤트로 보낸다 (MS2-169, 아래 "otel_bridge.py" 절). 만들어 둔 CSV가 아니라 진짜 사용량이
청구 예정액이 되는 것을 확인하는 쪽이다.

- Python 3.9 이상. venv 없이 바로 실행된다 (macOS 기본 python3 가능)
- `console.py`만 예외로 [uv](https://docs.astral.sh/uv/)가 필요하다 (`brew install uv`).
  나머지는 전부 표준 라이브러리만 쓴다
- 백엔드는 로컬에서 떠 있어야 한다: IntelliJ에서 MeterEngineApplication 실행 또는 backend/에서 ./gradlew bootRun
  (spring-boot-docker-compose가 postgres 컨테이너를 자동 기동하고 Flyway가 시드를 적용한다)

## 사용법

```
python3 demo/meterdemo.py send --csv <이벤트 CSV> [--interval 0.5] [--jitter 0.3] [--yes] [--dry-run]
python3 demo/meterdemo.py verify --log demo/logs/send-<타임스탬프>.jsonl [--month 2026-08]
python3 demo/meterdemo.py verify --csv <이벤트 CSV>
python3 demo/meterdemo.py convert ...   (준비 중)

uv run demo/console.py                  (화면으로 다루기, 아래 참조)
python3 demo/otel_bridge.py config --owner <이름> --allow <레포들>
python3 demo/otel_bridge.py setup
python3 demo/otel_bridge.py serve | install | start | stop | status
```

공통 옵션: --base-url (기본 http://localhost:8080), --org-id (기본: 시드 도입사), --no-color, --timeout

### send

CSV의 이벤트를 POST /v1/events로 순차 전송한다. 전송마다 요청 요약과 서버 응답을
[NEW] [DUP] [400] 태그로 구분해 출력하고, 모든 요청:응답 쌍을 demo/logs/ 아래
JSONL 파일로 항상 저장한다 (옵션이 아니다).

- 전송 전 확인 게이트: 대상 서버, 도입사, 건수, KST 월별 분포, 서버에 이미 있는 건수와
  CSV 기준 예상 결과(신규/중복/400 거절)를 보여주고 y 입력을 기다린다. 문제로 예측된
  행은 미리보기 범위 밖이어도 사유와 함께 반드시 표시된다. --yes로 생략한다.
  --dry-run은 게이트와 미리보기만 하고 전송도 로그 기록도 하지 않는다
- CSV에 선택 컬럼 note로 행 설명을 적어두면 그 행을 출력할 때 transaction_id보다
  앞에 표시된다 (예: "미등록 고객"). 비워두면 아무것도 표시하지 않는다. 시연 데이터에
  의도를 적어 두는 용도이고 서버로는 전송되지 않는다. 서버의 실제 거절 사유는
  응답 줄(=>)에 그대로 나온다
- --interval은 전송 간격 초, --jitter는 간격에 더할 랜덤 슬립 상한이다 (데모에서 실시간
  유입처럼 보이게 하는 용도)
- 주의: usage_event 테이블은 append-only 트리거로 DELETE가 불가능하다. 잘못 보낸 데이터는
  docker compose down -v로 DB를 초기화해야만 지울 수 있다

#### CSV 스키마

MS2-142(Mock 데이터) 산출물이 이 스키마 그대로 나와서 잠정 표기를 뗐다 (csvio 모듈도
교체 없이 유지). 헤더 행 필수, 컬럼 순서는 무관하다.

| 컬럼 | 내용 |
|---|---|
| transaction_id | 멱등 키 (같은 값 재전송은 중복 처리) |
| customer_id | 고객 UUID (시드가 발급한 customer.id) |
| event_type | 미터의 event_type (부트 시드에 chat_completion, llm_request, network_traffic이 있다) |
| timestamp | RFC 3339, 오프셋 포함 (occurred_at이 된다) |
| properties | JSON 객체 문자열. CSV 셀 안에서는 큰따옴표를 두 번 쓴다 |
| note (선택) | 행 설명, 콘솔 표시 전용. 서버로 전송하지 않으며 없거나 비면 표시 생략 |

```csv
transaction_id,customer_id,event_type,timestamp,properties,note
demo-001,35bc8d12-9d38-57ab-bc9b-bbd35d779a26,llm_request,2026-08-05T10:00:00+09:00,"{""input_tokens"": 101, ""output_tokens"": 51}",
demo-bad,9f31c2aa-0000-0000-0000-000000000000,llm_request,2026-08-05T11:00:00+09:00,"{""input_tokens"": 120}",미등록 고객
```

잘못된 값(빈 필드, 미등록 고객 등)도 거르지 않고 그대로 보낸다. 400 거절을
시연하는 것도 이 툴의 목적이라 판정은 서버가 한다.

### verify

소스에서 이벤트를 읽어 Python이 독립 계산한 고객별 사용량 합과 청구 예정액을
GET /v1/usage, GET /v1/invoice 응답과 표로 나란히 비교하고 일치/불일치를 표시한다.

- 소스는 --log(send가 남긴 JSONL)와 --csv(원본 데이터)를 지원하고, 둘 다 주면 로그를
  우선한다 (로그가 실제 전송분이다). CSV 소스는 서버 판정을 시뮬레이션한다: 필수 필드
  검증과 서버 고객 명단 대조로 거절을 예측하되, invalid_event 같은 희귀 거절은 예측하지
  못하며 그 한계를 출력에 명시한다
- 독립 계산 규칙 (서버 코드와 동일한 규칙을 Decimal 연산으로 복제, float 미사용):
  - 중복 transaction_id 제거: 로그 소스는 서버 판정 그대로 outcome=new만 집계한다
  - occurred_at(요청의 timestamp)의 KST 자정 경계 월 귀속
  - 400 거절분 제외
  - target_property 값이 JSON number가 아닌 이벤트 제외 (저장은 되지만 합산에서 빠진다)
  - 청구 예정액은 수량 x 단가를 라인마다 절사한 정수
- 미터 정의(event_type, target_property, 단가)는 서버 응답에서 유도하고 화면에 출력해
  시드와 눈으로 대조할 수 있게 한다. 시드에 고객이나 미터가 늘어도 툴 수정이 필요 없다
- 불일치가 나오면: 소스에 없는 이벤트가 서버에 이미 있으면(이전 실행분, 수동 전송분 등)
  어긋날 수 있다. DB 볼륨은 재시작해도 유지되므로 실제로 자주 생긴다. 진단으로 소스
  건수와 서버 건수를 같이 보여준다

### 종료 코드

- 0: 전부 일치 (send는 정상 완료)
- 1: 불일치 있음
- 2: 실행 오류 (서버 미기동, 소스 없음, 스키마 미확정, send 로그 중간 손상 등)

## sample-events.csv가 쓰는 데이터

`demo/sample-events.csv`(MS2-142 mock 축소판)는 고객 3곳(이슬비랩스, 도담헬스, 한들물류)과
미터 3개(input-tokens 0.007원, output-tokens 0.035원, network-egress 120원)를 쓴다.

**따로 준비할 것은 없다.** 이 데이터는 백엔드의 부트 시드(`R__seed.sql`)에 들어 있어 기동만
하면 적용된다. 예전에는 `demo/seed-customers.sql`과 `seed-metrics.sql`을 psql로 직접 주입해야
했는데, MS2-166에서 부트 시드로 편입하면서 두 파일을 없앴다. 배포 환경에서는 RDS에 직접
붙을 수 없어(MS2-164) psql 주입이라는 경로 자체가 성립하지 않기 때문이다.

기본 시드의 아크메 주식회사와 베타 스튜디오는 이 CSV의 이벤트를 받지 않아 사용량 0, 금액 0
행으로 보인다.

## 처음 실행해 보기 (시연 코스)

레포 루트에서 순서대로 실행한다. 백엔드가 떠 있으면 된다. 동봉된 `demo/sample-events.csv`는 100행(신규 80, 같은 transaction_id
재전송 중복 20)으로, 한 번 보낸 뒤에는 몇 번을 재전송해도 전부 중복이라 DB가
바뀌지 않는다. 부담 없이 반복 실행해도 된다.

```bash
# 1. 도움말
python3 demo/meterdemo.py --help
python3 demo/meterdemo.py send --help

# 2. dry-run: 전송 없이 확인 게이트와 미리보기만 (아무것도 안 바뀜)
python3 demo/meterdemo.py send --csv demo/sample-events.csv --dry-run

# 3. 전송: --yes 없이 실행하면 y 입력 게이트를 거친다.
#    [NEW] [DUP] [400] 태그로 요청:응답이 쌍으로 흐르고 로그 경로가 마지막에 찍힌다
python3 demo/meterdemo.py send --csv demo/sample-events.csv

# 4. 실시간 유입처럼 천천히 (시연용 시간 조절, 100건이라 2분쯤 걸린다)
python3 demo/meterdemo.py send --csv demo/sample-events.csv --yes --interval 0.7 --jitter 0.5

# 5. verify: 3에서 찍힌 로그 경로를 넣는다. 깨끗한 DB에서 보냈다면 전부 "일치"가 나온다
python3 demo/meterdemo.py verify --log demo/logs/send-<타임스탬프>.jsonl
echo $?   # 0 = 전부 일치

# 6. verify를 CSV 소스로: 서버 판정 시뮬레이션(중복 20건 제외)을 보여준다
python3 demo/meterdemo.py verify --csv demo/sample-events.csv

# 7. 오류 처리: traceback 없이 한 줄 안내와 종료 코드 2
python3 demo/meterdemo.py verify --log demo/logs/없는파일.jsonl; echo $?

# 8. 로그 파일 열어보기: 요청:응답 쌍이 JSONL로 어떻게 남는지
python3 -m json.tool --json-lines demo/logs/send-<타임스탬프>.jsonl | head -40

# 9. 단위 테스트 (하위 패키지까지 찾는다)
cd demo && python3 -m unittest && cd ..
```

경계 케이스까지 보려면 두 번째 샘플 `demo/sample-events-edge.csv`(10행, 전부 정상 저장)로
같은 코스를 반복한다. KST 월 경계(8월/9월 귀속), UTC 표기 타임스탬프, 소수 토큰의
예정액 절사, 그리고 "200으로 저장되지만 집계에서 빠지는" 행(token이 문자열, token 키
없음)을 보여준다. verify가 8월과 9월 두 달을 이어서 검증하는 것도 이 샘플에서 보인다.

주의 두 가지.

- verify는 신규 저장이 담긴 로그로 한다. 재전송 로그(전부 중복)를 넣으면
  "저장될 이벤트가 없습니다"라고 안내하고 끝난다
- 전부 "일치" 화면을 보려면 깨끗한 DB에서 시작해야 한다. DB에 이전 이벤트가
  남아 있으면 불일치와 함께 원인(소스 밖 N건)이 표시된다. 아래 인수조건 절차의
  1번(클린 리셋)을 먼저 하면 된다

## 인수조건 확인 절차 (MS2-128)

`demo/sample-events.csv`가 이 절차용 MS2-142 데이터다. 절차:

1. 깨끗한 상태에서 시작: `docker compose down -v` 후 백엔드 재기동 (재마이그레이션과 부트 시드 적용)
2. `send --csv demo/sample-events.csv`: 100건(같은 transaction_id 중복 20건 포함) 전송,
   신규 80 / 중복 20이 태그로 보인다
3. `verify --log <방금 로그>`: 사용량이 80건분 합인지 표로 확인
4. 같은 CSV로 `send` 재실행 (전부 [DUP]가 보인다)
5. 첫 로그로 다시 `verify`: 예정액이 직전과 같은지 확인

수기 검산 앵커 (verify가 자동 계산하지만 눈대중용, 전부 2026년 8월 귀속):

- 이슬비랩스: input 4,820 x 0.007 = 33원 + output 2,820 x 0.035 = 98원 = 131원
- 도담헬스: input 5,010 x 0.007 = 35원
- 한들물류: egress 25.0GB x 120 = 3,000원
- 아크메 주식회사, 베타 스튜디오: 이벤트 없음, 0원 (부트 시드 고객)

단가는 Anthropic 공시가를 1토큰 단위로 역산한 값이라 소수점 아래가 길다. 금액은 라인마다
버림(ROUND_DOWN)이므로 33.74원이 33원이 된다. 캐시 미터 둘은 샘플 CSV에 해당 속성이
없어 0원이고, 브리지가 보내는 이벤트에서만 잡힌다.

## JSONL 로그 포맷 (이 문서가 정본)

파일: `demo/logs/send-YYYYMMDD-HHMMSS.jsonl` (KST 타임스탬프, 실행마다 새 파일)

브리지는 같은 포맷을 쓰되 파일이 다르다. `demo/logs/bridge-YYYYMMDD.jsonl` 하루 한
파일에 이어쓴다. 상주 프로세스라 하루에도 여러 번 재시작되는데 그때마다 새 파일을
만들면 기록이 쪼개져 verify가 하루치를 한 번에 대조하지 못하기 때문이다. 그래서
실행마다 헤더가 하나씩 더 붙고, 읽는 쪽은 **마지막 헤더**를 그 파일의 헤더로 본다.

1행은 실행 헤더, 이후 각 행이 전송 1건이다. request와 response는 와이어에 실린 JSON
텍스트를 재직렬화 없이 그대로 담아 소수 자릿수까지 재현 가능하다.

```
{"v": 1, "type": "run", "started_at": "<RFC3339>", "base_url": "...", "org_id": "...", "csv": "...", "argv": [...]}
{"v": 1, "type": "send", "seq": 1, "sent_at": "<RFC3339>", "request": {...바디 원문...},
 "status": 200, "response": {...응답 원문...}, "outcome": "new", "error": null, "elapsed_ms": 12}
```

- outcome: new(신규 저장) | duplicate(중복, 저장 없음) | rejected(400 거절) |
  error(전송 실패/타임아웃/5xx, 저장 여부 불명 -- status와 response는 null일 수 있다)
- rejected의 response에는 problem+json 원문이 그대로 남는다
- 라인마다 flush하므로 중단돼도 그 앞까지는 유효하다. 읽는 쪽은 읽히지 않는 라인을
  위치와 무관하게 경고와 함께 건너뛰고, 모르는 type은 무시한다 (전방 호환).
  이어쓰는 파일에서는 잘린 라인이 마지막이 아니게 되므로(뒤에 다음 실행 헤더가
  붙는다) 마지막 라인만 봐주면 그날 기록 전체가 검증 불가가 된다. 대신 건너뛴
  줄은 경고에 몇 행인지 함께 남는다
- **중간이 깨진 send 로그로는 verify가 판정하지 않는다** (종료 코드 2). send는 실행
  하나가 파일 하나라 중간이 깨지면 몇 건이 빠졌는지 알 수 없고, 남은 합계가 우연히
  서버와 맞으면 "일치"로 0이 나오기 때문이다. 헤더가 여럿인 브리지 로그는 재시작으로
  잘리는 것이 정상 범위라 경고까지만 한다 (파일 끝의 잘린 라인도 마찬가지다)
- 이어쓸 때 앞 줄이 개행 없이 끊겨 있으면 개행을 먼저 넣는다. 그냥 붙이면 두 레코드가
  한 줄로 엉겨 둘 다 못 읽게 되고, 그 한 줄이 실행 헤더면 verify가 전송 대상을 모른 채
  기본값으로 검증한다

## otel_bridge.py: Claude Code 사용량 보내기 (MS2-169)

우리가 Claude Code를 쓰면서 실제로 태운 토큰을 사용량 이벤트로 흘려보낸다. 만들어 둔
CSV가 아니라 진짜 사용량이 청구 예정액이 되는 것을 배포된 화면에서 확인하는 것이 목적이다.

### 왜 hook이 아니라 OTel인가

원래 계획은 hook에서 이벤트를 보내는 것이었는데, **hook 입력에는 토큰과 비용이 없다.**
공식 문서가 그렇게 밝히고 대신 OpenTelemetry를 쓰라고 안내한다. hook이 주는 것은
`session_id`, `cwd`, `transcript_path`, `tool_name` 정도다.

그래서 역할을 나눴다. **토큰은 OTel에서, 프로젝트 귀속은 hook에서** 받는다.
OTel 이벤트에는 어느 폴더에서 돌았는지가 없고 `session.id`만 실리기 때문에, hook이
"이 세션은 이 폴더"를 알려 줘야 고객을 나눌 수 있다.

```
Claude Code --UserPromptSubmit hook--> 브리지: 세션 abc는 meterengine 폴더
            --OTLP/JSON--------------> 브리지: {session.id: abc, input_tokens: ...}
                                          |
                                          v
                                POST <base_url>/v1/events
```

브리지는 각자 기계에서 도는 로컬 상주 프로세스다. 백엔드와 배포 구성은 건드리지 않는다.
검증 도구가 검증 대상 안에 들어가면 안 되기 때문이다.

`SessionStart`가 아니라 `UserPromptSubmit`을 쓰는 이유는 두 가지다. SessionStart는
`command`와 `mcp_tool` 타입만 지원해서 `http` hook을 걸 수 없고(공식 문서), 세션당 한 번뿐이라
그때 브리지가 꺼져 있으면 그 세션 전체가 폴백으로 간다. UserPromptSubmit은 매 턴 오므로
브리지를 중간에 재시작해도 다음 턴에 매핑이 저절로 복구된다.

### 화면으로 다루기 (console.py)

```
uv run demo/console.py
```

상태가 2초마다 갱신되고, 설정을 고치고, 브리지를 켜고 끄는 것을 한 화면에서 한다.
아래 명령줄로 하는 일과 같은 코드(`bridge/admin.py`)를 쓰므로 동작이 갈라지지 않는다.

프로젝트 목록이 이 화면의 핵심이다. **설정에 적은 것뿐 아니라 브리지가 실제로 본
프로젝트가 함께 뜬다.** 무엇을 실명으로 할지 고르려면 내가 어떤 레포에서 일했는지가
먼저 보여야 하기 때문이다. 콘솔을 켜 둔 사이에 브리지가 처음 본 레포도 다음 갱신
(2초)에 목록 아래로 붙는다. 커서를 놓고 스페이스를 누르면 세 상태를 돈다.

```
● meterengine        실명으로 보냄
○ notes              기타 프로젝트로 합침
✕ 비밀레포            보내지 않음
```

허용 목록이 비면 모든 프로젝트가 실명으로 간다. 목록에서 전부 "합침"으로 바꾸면 고른
것과 정반대가 되므로, 그 상태가 되면 화면이 규칙 줄에 그렇게 알린다.

`uv`가 필요한 것은 이 파일 하나뿐이다. 의존성이 파일 안에 적혀 있어(PEP 723) uv가
알아서 받아 실행하며, demo의 나머지는 그대로 `python3`로 돈다.

### 명령줄로 다루기

```
# 1. 누가 어디로 보낼지 정한다 (~/.meterengine/bridge.json)
python3 demo/otel_bridge.py config --owner "박성종" --allow "meterengine,meterengine-demo"

# 2. ~/.claude/settings.json에 OTel 설정과 hook을 병합한다 (기존 내용은 보존된다)
python3 demo/otel_bridge.py setup

# 3. 띄운다
python3 demo/otel_bridge.py serve          # 포그라운드
python3 demo/otel_bridge.py install        # 또는 launchd에 등록해 자동 시작
python3 demo/otel_bridge.py start / stop   # 껐다 켰다
python3 demo/otel_bridge.py status         # 상태와 누적 건수
```

`base_url` 기본값이 `http://localhost:8080`인 것은 일부러다. **usage_event는 append-only라
잘못 보낸 이벤트를 지울 수 없다.** 로컬에서 확인한 뒤 배포 주소를 손으로 적어 넣는다.

```
python3 demo/otel_bridge.py config --base-url https://meterengine.com
```

`install`은 워크트리에서 실행하면 거부한다. plist에 스크립트의 절대 경로가 박히는데,
워크트리를 지우면 브리지가 죽고 launchd가 살리려다 실패를 반복하기 때문이다.
본 저장소의 `demo/otel_bridge.py`로 등록한다.

### 고객이 정해지는 방식

고객 이름은 `<프로젝트>(<주인>)` 형식이다. 예: `meterengine(박성종)`.

- 프로젝트 이름은 **git 레포 루트 이름**이다. `--show-toplevel`이 아니라 `--git-common-dir`을
  보므로, 워크트리(MS2-169, MS2-157 ...)에서 일해도 전부 `meterengine` 하나로 모인다
- `allow`에 적은 레포만 실명이고, 나머지는 전부 `기타 프로젝트` 하나로 합쳐진다.
  개인 프로젝트 이름이 공개된 화면에 뜨지 않게 하는 장치다. `allow`가 비면 전부 실명이다
- `deny`에 적은 레포는 아예 보내지 않는다. hook이 세션을 묶을 때 한 번,
  전송 직전에 프로젝트 이름으로 한 번 더 본다. 두 번 보는 이유는 세션이 묶인 뒤에
  그 레포를 `deny`로 바꾸는 경우가 있어서다. 그때 hook이 다시 오기 전까지는 세션
  쪽 표시가 아직 옛 상태다
- 고객은 브리지가 알아서 만든다. `GET /v1/customers`로 같은 이름을 찾고 없을 때만
  `POST /v1/customers`로 등록한다. 이 API는 이름 중복을 막지 않아서 조회를 빠뜨리면
  같은 이름의 고객이 계속 늘어난다
- 찾아낸 고객의 id는 `~/.meterengine/state.json`에 담아 둔다. 서버가 그 고객을
  모른다고 답하면(400 `unknown_customer_reference`) 그 자리에서 캐시를 버리고 다음
  이벤트에서 다시 찾는다. 남겨 두면 죽은 id를 계속 보내 그 프로젝트가 영영 거절된다

브리지가 꺼져 있으면 Claude는 그대로 동작하고 사용량만 수집되지 않는다. hook의 연결 실패는
차단하지 않는 오류이기 때문이다(공식 문서).

### 브리지가 여는 포트

`127.0.0.1:4318`에만 붙고 인증은 없다. 대신 **브라우저가 보낸 요청은 받지 않는다.**
`Origin`이 붙어 있거나 `Sec-Fetch-Site`가 `none`이 아니거나 `Host`가 이 기계를
가리키지 않으면 403이다.

이 검사가 없으면 사용자가 열어 둔 아무 페이지나 이 포트로 POST할 수 있다.
`Content-Type: text/plain`이면 CORS 사전 요청 없이 곧바로 나가고, 응답을 읽지 못해도
브리지는 이미 처리한 뒤다. 지어낸 토큰 수가 `usage_event`에 들어가면 append-only라
지울 수 없고, `/meterengine/session`으로 남의 세션 귀속까지 바꿀 수 있다.

세션 매핑은 하루 동안만 붙들고 있다가 버린다. 프롬프트를 칠 때마다 hook이 오는데,
그때 상태 파일 전체를 다시 쓰므로 끝난 세션이 쌓이면 hook이 그만큼 느려진다
(hook 타임아웃은 5초다). 잘못 버려도 다음 프롬프트에서 다시 묶인다.

### 보내는 이벤트와 properties

과금이나 구분에 쓰이는 메타만 보낸다. 프롬프트와 응답 본문 쪽(`user_prompt`,
`assistant_response`)은 보내지 않는다.

| OTel 이벤트 | event_type | 지금 집계되나 |
|---|---|---|
| `api_request` | `llm_request` | 된다 (아래 미터 4종) |
| `tool_result` | `tool_call` | 아니다. 미터가 없어 저장만 된다 |
| `api_error` | `llm_error` | 아니다 |
| `api_refusal` | `llm_refusal` | 아니다 |

미터가 없는 이벤트도 저장은 된다. 나중에 미터를 만들면 이미 쌓인 이벤트가 그때부터 함께
집계된다. raw event를 먼저 모으는 설계 그대로다.

properties에는 개인정보를 뺀 나머지를 평평하게 담는다. 키의 점은 언더스코어로 바꾼다
(`agent.name` -> `agent_name`). 시드 미터의 `target_property`가 전부 언더스코어이고,
다차원 가격 정책이 `dimension_properties`에 키를 선언할 때 점 있는 키를 다루지 않아도 된다.

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

**담지 않는 것**: `user.email`, `user.id`, `user.account_uuid`, `user.account_id`,
`organization.id`(우리 도입사가 아니라 Anthropic 조직 ID다). 공개된 화면에 뜨는
데이터라서다.

`model`, `speed`, `effort`, `query_source`가 들어 있으므로 다차원 단가는
`price_policy.dimension_properties`에 그 키를 선언하고 조합별 `price_rate`를 추가하는
것만으로 켜진다. 브리지는 고치지 않아도 된다.

수치로 쓸 키는 반드시 JSON number로 넣는다. 같은 이름이라도 이벤트마다 OTLP 타입이 달라서
(`duration_ms`가 `api_request`에서는 정수, `tool_result`에서는 문자열 `"145"`로 온다)
그대로 두면 미터가 조용히 집계에서 뺀다.

### 캐시 토큰

Claude Code 요청 한 건을 실제로 재 보면 이렇다.

```
input_tokens 2   output_tokens 75
cache_read_tokens 33661   cache_creation_tokens 23672
```

입력과 출력만 재면 그 요청의 청구 예정액이 11원이라 화면에서 아무것도 확인할 수 없다.
토큰 수로도 비용으로도 대부분이 캐시 쪽이라, 캐시를 빼면 잴 것이 사실상 없다. 그래서
시드에 `cache-read-tokens`와 `cache-creation-tokens` 미터를 추가했다.

### 단가

Anthropic 공시가를 그대로 역산한다. 기준은 Claude Opus 5, 1 MTok = 100만 토큰,
1달러 1,400원이다.

| 미터 | 공시가 | 계산 | 원/토큰 |
|---|---|---|---|
| input-tokens | $5 / MTok | 5 x 1400 / 1,000,000 | 0.007 |
| cache-creation-tokens | $6.25 / MTok | 6.25 x 1400 / 1,000,000 | 0.00875 |
| cache-read-tokens | $0.50 / MTok | 0.5 x 1400 / 1,000,000 | 0.0007 |
| output-tokens | $25 / MTok | 25 x 1400 / 1,000,000 | 0.035 |

캐시 쓰기는 5분 캐시 기준이다. OTel의 `cache_creation_tokens`가 5분과 1시간을 구분하지
않아서 기본값인 쪽을 쓴다.

**모델별 단가는 아직 켜지지 않는다.** `properties`에 `model`이 실려 있고 가격표에도
모델별 단가가 있지만, `DraftInvoiceService`가 단가를 얻는 통로인
`PriceRateRepository.findBaseUnitPrices`가 `dimension_values = '{}'` 행만 읽는다.
차원별 조회는 MS2-178이 붙인다. 그래서 지금 시드에는 기본 단가 한 행만 두었다.
모델별 행을 넣어도 읽히지 않을 뿐 아니라, 기본 단가 행을 지우면 그 미터가
인보이스에서 통째로 빠진다 (같은 메서드가 `containsKey`로 거른다).

금액은 실제 가격이라 작다. Opus 5로 요청 한 번에 수백 원 수준이고, 라인마다 절사하므로
입력 토큰처럼 적은 항목은 0원이 되기도 한다. 그것이 실제 과금에서 일어나는 일이다.

이제 `llm_request` 이벤트 하나가 미터 넷에 잡힌다. 이벤트 하나가 여러 미터에 걸리는 것을
보여주는 자리이기도 하다.

### 보낸 것을 검증하기

브리지는 send와 **같은 JSONL 포맷**으로 남기므로 기존 검증기가 그대로 돈다.

```
python3 demo/meterdemo.py verify --log demo/logs/bridge-20260824.jsonl
```

Python이 Decimal로 독립 계산한 값과 서버의 사용량/청구 예정액이 일치하는지 표로 보여준다.
서버에 소스 밖 이벤트(이전 CSV 전송분 등)가 있으면 그 고객은 불일치로 나오고, 몇 건이
소스 밖인지 진단에 찍힌다.

## 파일 구성

세 층으로 나눠 둔다. `core`는 양쪽이 함께 쓰고, `csvdemo`와 `bridge`는 서로를 모른다.

```
demo/
  meterdemo.py      CSV 데모/검증 진입점 (send, verify)
  otel_bridge.py    브리지 명령줄 진입점 (serve, config, setup, install, start, stop, status)
  console.py        브리지 화면 진입점 (uv로 실행)

  core/             양쪽이 함께 쓰는 것
    model.py        KST 상수, Event, RFC3339 파싱, 와이어 바디 조립
    api_client.py   백엔드 HTTP 래퍼 (problem+json 파싱 포함)
    jsonl_log.py    전송 기록 JSONL 쓰기/읽기, outcome 판정
    files.py        JSON 파일 원자적 쓰기 (상태 파일과 Claude 설정)

  csvdemo/          CSV를 흘려보내고 대조하는 쪽
    csvio.py        CSV 소스 읽기
    expected.py     기대값 독립 계산 (중복 제거, 월 귀속, 합산, 절사)
    render.py       콘솔 출력 (태그, 표, 한글 폭 보정)
    send_cmd.py / verify_cmd.py

  bridge/           Claude Code 사용량을 보내는 쪽
    const.py        경로와 주소 (진입점 셋이 같은 값을 봐야 한다)
    otel_map.py     OTLP 페이로드를 Event로 변환 (부수효과 없음)
    state.py        설정, 세션 매핑, 고객 해석
    server.py       OTLP와 hook을 받는 수집 서버
    admin.py        켜고 끄고 설정하는 일. 출력하지 않고 값을 돌려준다

  sample-events.csv       MS2-142 mock 축소판 100행 (신규 80, 중복 20)
  sample-events-edge.csv  경계 케이스 (KST 월 경계, UTC 표기, 소수 토큰, 집계 제외 행)
  logs/                   전송 기록 (gitignore)
```

진입점을 최상위에 두는 이유는 `python3 demo/meterdemo.py`로 실행할 때 `demo/`가 곧
import 경로가 되기 때문이다. 덕분에 하위 패키지들이 `sys.path`를 건드리지 않는다.

테스트는 각 패키지 안에 있다: `cd demo && python3 -m unittest discover`

콘솔 테스트(`test_console.py`)만 textual이 있어야 돌고, 없으면 건너뛴다. 전부 돌리려면
`uv run --with textual python3 -m unittest`를 쓴다. 콘솔만 uv로 실행하는 구조 그대로다.
