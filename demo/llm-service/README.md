# Lumen LLM 서비스 예제

도입사 고객이 모델을 골라 응답을 만들고 본인의 사용량과 청구를 확인하는 앱이다. 이벤트 전송 도구인 `demo/web`과 별도 서버로 실행한다.
모델 응답과 문자 기반 토큰은 로컬 시연용이다. 사용량 수집, 단가와 청구 조회는 실제 MeterEngine API를 사용한다.
자체 SQLite에 세션, 대화와 전송 대기를 보존한다. MeterEngine DB에 직접 접근하지 않으며, 실행 묶음을 다른 서버로 복사할 수 있다.

## 로컬 실행

Node 24.22.1, pnpm 11.18.0과 실행 중인 MeterEngine이 필요하다. 저장소 루트에서 실행한다.

```sh
cd demo
pnpm install --frozen-lockfile
pnpm --filter @meterengine/llm-service build
```

초기화 스크립트는 로컬 API에 별도 도입사, 고객 3명, 입출력 토큰과 요청 수 미터, 모델별 단가와 서버 API 키를 만든다. 운영자 이메일과 암호를 각각 `METERENGINE_OPERATOR_EMAIL`, `METERENGINE_OPERATOR_PASSWORD` 환경변수로 주입한다. API와 콘솔 주소는 `METERENGINE_API_BASE_URL`, `METERENGINE_CONSOLE_URL`로 지정한다. 기본값은 각각 `http://127.0.0.1:38147`, `http://localhost:33147`이다.

```sh
node llm-service/scripts/seed-demo.ts --output /private/tmp/lumen/config.json
node llm-service/scripts/start.mjs --config /private/tmp/lumen/config.json
```

기본 주소는 `http://localhost:3200`이다. 초기화 결과와 이어서 실행할 상태는 권한 600의 비공개 파일로 저장한다. 기존 설정 파일은 덮어쓰지 않으며 실패한 초기화는 같은 출력 경로로 다시 실행할 수 있다.

## 서버 설정

JSON 설정 파일은 다음 환경변수의 문자열 맵이다. `.env.example`은 컨테이너 환경변수 형식이다.

| 이름 | 용도 |
| --- | --- |
| `LUMEN_APP_ORIGIN` | 브라우저가 접근할 정확한 origin. 다른 서버로 옮길 때 변경한다. |
| `LUMEN_DATABASE_PATH` | 자체 SQLite의 절대 경로. 서버 재기동에도 보존하는 볼륨을 쓴다. |
| `LUMEN_ACCOUNTS` | `{id,name,email,customerId}` 객체의 JSON 배열. 시연 계정과 MeterEngine 고객을 연결한다. |
| `METERENGINE_API_BASE_URL` | 서버에서 접근하는 MeterEngine API 주소. |
| `METERENGINE_ENVIRONMENT` | `test` 또는 `live`. 생략하면 API 키의 환경을 사용하며 초기화 스크립트의 기본값은 `test`다. |
| `METERENGINE_API_KEY` | 수집과 조회용 `read`, `ingest` 키. 브라우저에 전달하지 않는다. |
| `METERENGINE_BILLING_API_KEY` | 카드 등록 링크 발급용 선택 키. 현재 API 계약의 `admin` 권한이 필요하며 본인 고객의 링크에만 사용한다. 실제 E2E는 이 키로 이번 달 인보이스 확정도 검증한다. |
| `METERENGINE_CONSOLE_URL` | 카드 등록을 진행할 MeterEngine 화면 주소. |
| `LUMEN_AUTO_FLUSH_INTERVAL_MS` | 주기 재전송 간격. 기본 3000ms, 0이면 주기 작업만 끈다. 생성 직후와 직접 재전송은 실행한다. |

카드 등록은 지정한 업무 환경의 PG 설정을 사용한다. 환경을 생략한 시연 연결은 테스트 카드 등록만 허용한다. 이 앱에서 결제 실행이나 인보이스 확정은 하지 않는다. 실제 E2E만 검증을 위해 이번 달 인보이스를 되돌릴 수 없게 확정하므로, 같은 달에 다시 돌리려면 새 출력 경로로 시드하고 앱을 다시 띄운다.

SQLite는 API 주소, 업무 환경과 서버 키에 고정된다. 연결 설정을 바꿀 때는 기존 파일을 보존하고 별도 `LUMEN_DATABASE_PATH`를 지정한다. 연결 범위가 없는 기존 요청 파일도 자동 이행하지 않는다.

## 별도 서버로 옮기기

production build 후 실행 묶음을 새 폴더로 내보낸다.

```sh
node llm-service/scripts/export-standalone.mjs --output /private/tmp/lumen-bundle
node /private/tmp/lumen-bundle/start.mjs --config /private/tmp/lumen/config.json
```

묶음, Node 24.22.1, 비공개 설정과 SQLite 볼륨으로 실행한다. 원래 저장소나 MeterEngine의 소스, DB 접속 정보는 필요하지 않다. SDK는 묶음에 포함되며 MeterEngine에는 HTTP로 접속한다.

다른 포트로 검사할 때 origin도 함께 바꾼다. 외부에서 직접 접속받으려면 `--host 0.0.0.0`도 지정한다.

```sh
node /private/tmp/lumen-bundle/start.mjs --config /private/tmp/lumen/config.json --port 3201 --origin http://localhost:3201
```

컨테이너 빌드의 context는 `demo`다.

```sh
docker build -f llm-service/Dockerfile -t lumen-llm-demo .
docker run --rm -p 3200:3200 --env-file llm-service/.env -v lumen-data:/data lumen-llm-demo
```

컨테이너의 API 주소는 컨테이너에서 접근 가능한 주소여야 한다. 로컬 호스트의 MeterEngine을 쓸 때는 Docker 환경에 맞춰 `host.docker.internal` 등을 지정한다. HTTPS 프록시를 쓸 때는 외부 origin과 Host를 보존한다.

## 동작과 경계

- 시연 계정 선택은 고객 인증을 모사한다. 실제 고객용 인증 시스템으로 주장하지 않는다. 고객 ID는 브라우저 입력으로 선택하지 않고 서버 세션에서 결정한다.
- 프롬프트와 응답은 이 앱에만 저장한다. MeterEngine에는 고객과 거래 ID, 발생 시각, 모델, 토큰 계수와 출처만 전송한다.
- 요청 ID가 같으면 같은 결과를 돌려주고 다른 내용이면 거절한다. 수집 실패와 재기동 뒤에도 원래 거래 ID와 발생 시각으로 재전송한다.
- MeterEngine이 다시 보내도 같은 이유로 거절하는 사용량(보관했거나 없는 고객, 받는 기간이 지난 이벤트, 잘못된 값)은 자동 재전송을 멈춘다. 원인을 고친 뒤 사용량 다시 보내기로 직접 보낸다.
- 전송 대기와 청구 조회 실패는 실제 반영 완료, 사용량 0과 구분한다. 월별 예상액과 확정 인보이스도 나누어 보여 준다.
- 조회 월은 한국 시간의 월초 이상, 다음 달 월초 미만이다. 사용량, 청구, 요청 기록은 `?month=YYYY-MM` 선택을 유지하고 플레이그라운드는 이번 달을 보여 준다.
- 기본 모델은 외부 API를 호출하지 않는다. 실제 추론은 `GenerationProvider` 구현을 교체하는 경계다.
- 내장 SQLite API는 [Node 24.22.1 문서](https://github.com/nodejs/node/blob/v24.22.1/doc/api/sqlite.md)를 기준으로 사용한다. 이 예제의 전용 저장소이며 MeterEngine의 DB 분리 방식을 정하지 않는다.

## 검증

```sh
pnpm --filter @meterengine/llm-service typecheck
pnpm --filter @meterengine/llm-service lint
pnpm --filter @meterengine/llm-service test
```

실제 E2E는 별도 시연 도입사와 SQLite를 사용한다. `LUMEN_E2E_ENV_FILE`에 비공개 JSON 설정 경로를, `LUMEN_E2E_URL`에 해당 앱 주소를 지정한다. `LUMEN_E2E_STANDALONE_URL`이 있으면 같은 자체 DB를 연결한 복사본의 세션과 요청 영속성도 확인한다. 화면과 결과 파일은 `LUMEN_E2E_ARTIFACT_DIR`(기본은 OS 임시 폴더 아래 `meterengine-llm-tenant`)에 남는다.
