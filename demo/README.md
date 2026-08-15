# demo: 수집-조회 데모/검증 툴 (MS2-128)

이벤트를 실제 HTTP로 백엔드에 흘려보내고(send), 보낸 요청과 서버 응답을 1:1로 대조해 보여주며,
소스 기반으로 독립 계산한 기대값과 서버의 사용량/청구 예정액 응답이 일치하는지 확인한다(verify).
스토리 MS2-121의 인수조건을 사람이 눈으로 확인하는 시연 겸 검증 도구다.

- Python 3.9 이상, 표준 라이브러리만 사용한다. venv 없이 바로 실행된다 (macOS 기본 python3 가능)
- 백엔드는 로컬에서 떠 있어야 한다: IntelliJ에서 MeterEngineApplication 실행 또는 backend/에서 ./gradlew bootRun
  (spring-boot-docker-compose가 postgres 컨테이너를 자동 기동하고 Flyway가 시드를 적용한다)

## 사용법

```
python3 demo/meterdemo.py send --csv <이벤트 CSV> [--interval 0.5] [--jitter 0.3] [--yes] [--dry-run]
python3 demo/meterdemo.py verify --log demo/logs/send-<타임스탬프>.jsonl [--month 2026-08]
python3 demo/meterdemo.py verify --csv <이벤트 CSV>
python3 demo/meterdemo.py convert ...   (준비 중)
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

#### CSV 스키마 (잠정)

이 스키마는 MS2-142(Mock 데이터)와 맞출 인터페이스로 아직 확정 전이다. 확정되면
이 절과 csvio 모듈을 확정 내용으로 교체한다. 헤더 행 필수, 컬럼 순서는 무관하다.

| 컬럼 | 내용 |
|---|---|
| transaction_id | 멱등 키 (같은 값 재전송은 중복 처리) |
| customer_id | 고객 UUID (시드가 발급한 customer.id) |
| event_type | 미터의 event_type (시드: chat_completion) |
| timestamp | RFC 3339, 오프셋 포함 (occurred_at이 된다) |
| properties | JSON 객체 문자열. CSV 셀 안에서는 큰따옴표를 두 번 쓴다 |
| note (선택) | 행 설명, 콘솔 표시 전용. 서버로 전송하지 않으며 없거나 비면 표시 생략 |

```csv
transaction_id,customer_id,event_type,timestamp,properties,note
demo-001,a728e7b6-d82b-4f3c-a960-a66a02794c1d,chat_completion,2026-08-05T10:00:00+09:00,"{""token"": 501, ""model"": ""claude""}",
demo-bad,9f31c2aa-0000-0000-0000-000000000000,chat_completion,2026-08-05T11:00:00+09:00,"{""token"": 120}",미등록 고객
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
  시드와 눈으로 대조할 수 있게 한다. 시드가 늘어도(MS2-142) 툴 수정이 필요 없다
- 불일치가 나오면: 소스에 없는 이벤트가 서버에 이미 있으면(이전 실행분, 수동 전송분 등)
  어긋날 수 있다. DB 볼륨은 재시작해도 유지되므로 실제로 자주 생긴다. 진단으로 소스
  건수와 서버 건수를 같이 보여준다

### 종료 코드

- 0: 전부 일치 (send는 정상 완료)
- 1: 불일치 있음
- 2: 실행 오류 (서버 미기동, 소스 없음, 스키마 미확정 등)

## 처음 실행해 보기 (시연 코스)

레포 루트에서 순서대로 실행한다. 백엔드가 떠 있어야 한다. 동봉된
`demo/sample-events.csv`는 시드 고객 기준 7행(신규 4, 같은 transaction_id 중복 1,
400 거절 2)으로, 한 번 보낸 뒤에는 몇 번을 재전송해도 전부 중복/거절이라
DB가 바뀌지 않는다. 부담 없이 반복 실행해도 된다.

```bash
# 1. 도움말
python3 demo/meterdemo.py --help
python3 demo/meterdemo.py send --help

# 2. dry-run: 전송 없이 확인 게이트와 미리보기만 (아무것도 안 바뀜)
python3 demo/meterdemo.py send --csv demo/sample-events.csv --dry-run

# 3. 전송: --yes 없이 실행하면 y 입력 게이트를 거친다.
#    [NEW] [DUP] [400] 태그로 요청:응답이 쌍으로 흐르고 로그 경로가 마지막에 찍힌다
python3 demo/meterdemo.py send --csv demo/sample-events.csv

# 4. 실시간 유입처럼 천천히 (시연용 시간 조절)
python3 demo/meterdemo.py send --csv demo/sample-events.csv --yes --interval 0.7 --jitter 0.5

# 5. verify: 3에서 찍힌 로그 경로를 넣는다. 깨끗한 DB에서 보냈다면 전부 "일치"가 나온다
python3 demo/meterdemo.py verify --log demo/logs/send-<타임스탬프>.jsonl
echo $?   # 0 = 전부 일치

# 6. verify를 CSV 소스로: 서버 판정 시뮬레이션(거절 2건 예측)을 보여준다
python3 demo/meterdemo.py verify --csv demo/sample-events.csv

# 7. 오류 처리: traceback 없이 한 줄 안내와 종료 코드 2
python3 demo/meterdemo.py verify --log demo/logs/없는파일.jsonl; echo $?

# 8. 로그 파일 열어보기: 요청:응답 쌍이 JSONL로 어떻게 남는지
python3 -m json.tool --json-lines demo/logs/send-<타임스탬프>.jsonl | head -40

# 9. 단위 테스트
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

MS2-142 데이터가 나오면 그 CSV로 진행한다 (스키마 확정 시 위 잠정 스키마 절 교체). 절차:

1. 깨끗한 상태에서 시작: `docker compose down -v` 후 백엔드 재기동 (재마이그레이션과 시드 적용)
2. `send --csv <MS2-142 데이터>`: 100건(같은 transaction_id 중복 20건 포함) 전송,
   신규 80 / 중복 20이 태그로 보인다
3. `verify --log <방금 로그>`: 사용량이 80건분 합인지, 400 거절분이 예정액에 빠졌는지 표로 확인
4. 같은 CSV로 `send` 재실행 (전부 [DUP]가 보인다)
5. `verify --log <두 번째 로그>` 대신 첫 로그로 다시 `verify`: 예정액이 직전과 같은지 확인

## JSONL 로그 포맷 (이 문서가 정본)

파일: `demo/logs/send-YYYYMMDD-HHMMSS.jsonl` (KST 타임스탬프, 실행마다 새 파일)

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
- 라인마다 flush하므로 중단돼도 그 앞까지는 유효하다. 읽는 쪽은 잘린 마지막 라인을
  경고와 함께 건너뛰고, 모르는 type은 무시한다 (전방 호환)

## 파일 구성

- meterdemo.py: 엔트리 (argparse, 종료 코드)
- model.py: KST 상수, Event, RFC3339 파싱, 와이어 바디 조립
- csvio.py: CSV 소스 읽기 (잠정 스키마)
- sample-events.csv: 시연 코스용 최소 샘플 (중복과 400 거절, 정식 데모 데이터는 MS2-142)
- sample-events-edge.csv: 경계 케이스 샘플 (KST 월 경계, UTC 표기, 소수 토큰,
  집계 제외 행 -- 문자열 token과 token 없음)
- api_client.py: 백엔드 HTTP 래퍼 (problem+json 파싱 포함)
- jsonl_log.py: 로그 쓰기/읽기
- expected.py: 기대값 독립 계산 (중복 제거, 월 귀속, 합산, 절사)
- render.py: 콘솔 출력 (태그, 표, 한글 폭 보정)
- send_cmd.py / verify_cmd.py: 서브커맨드 흐름
- test_*.py: 단위 테스트. `cd demo && python3 -m unittest`
