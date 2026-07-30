# API 명세

핵심 API를 OpenAPI 스펙으로 명세한다 (MS2-27). MVP 전 범위를 개발 전에 명세하지 않고, 슬라이스 범위만큼 명세한 뒤 구현에서 배운 것을 반영해 버전을 올린다.

## 규칙

- 스펙 파일은 이 폴더에 둔다 (예: `openapi.yaml`). 파일 상단 `info.version`과 설명에 반영 범위(어느 슬라이스까지)를 명시한다
- 전 슬라이스 공통 규약을 먼저 확정한다: 멱등 규약, 에러 코드 체계, 인증 방식, 버저닝
- 스펙 변경은 구현과 같은 PR에서 리뷰한다
- 결정 근거는 `openapi.yaml` 안의 주석에 둔다. 별도 설명 문서를 만들지 않는다. 코드와 근거가 떨어지면 코드만 고쳐지고 근거는 낡는다. 주석에는 무엇을 하는지가 아니라 왜 그렇게 정했는지, 무엇을 탈락시켰는지, 언제 재검토하는지를 적는다

## 정본 정책 (진실 원천의 수명)

API 표면에 따라 정본의 수명이 다르다. 스펙 파일에도 각 경로가 어느 표면에 속하는지 명시해, "이 YAML 누가 고치나" 논쟁이 생기지 않게 한다.

- 고객 대면 API (이벤트 수집 등 외부 고객이 붙는 표면): 이 폴더의 수기 명세가 계속 정본이다(spec-first 유지). springdoc 생성물은 구현이 명세를 따르는지 대조하는 검증용으로만 쓴다. 공개 API가 곧 제품인 특성상, 계약이 코드 사정으로 흔들리면 안 되는 표면이다
- 콘솔 내부 API (관리자 화면 등 양쪽을 모두 우리가 통제하는 표면): 개발 초기에는 spec-first로 시작하되, 엔드포인트 구현이 끝나면 springdoc 생성물을 정본으로 승격한다. 자주 바뀌는 표면이라 수기 명세의 유지 비용이 더 크다

## 현재 상태

v1 (슬라이스 1) 작성 완료, 2026-07-29.

- 범위: 이벤트 수집 단건과 배치(배치는 명세만, 구현은 S1 제외), 미터 정의, 단가 설정, 고객 등록과 조회, 집계 조회, draft 인보이스 조회. 경로 8개 / 오퍼레이션 10개
- 전제: MS2-26 불변 결정 목록과 ERD v1, MS2-28 ADR 0001~0005를 반영했다
- 인보이스 확정, 부가세, 결제, 세금계산서, 크레딧, 구독은 이 버전에 없다 (S2 이후)

### 공통 규약 요약

| 항목 | 결정 |
| --- | --- |
| 인증 | `Authorization: Bearer <API_KEY>`. 키가 도입사를 암묵 식별 |
| 식별자 | API 표면은 `external_customer_id`와 `code` 단일 축. 내부 UUID 비노출 |
| 에러 | RFC 9457 Problem Details (`application/problem+json`) + snake_case `code` 확장 |
| 멱등 | 제어면은 `Idempotency-Key` 헤더, 이벤트는 바디 `transaction_id`. 중복은 200(성공) |
| 금액 | 원 단위 정수. 단가는 `(unit_price, unit_size)` 정수 쌍 |
| 시각 | RFC 3339. `occurred_at`(필수)와 `received_at`(서버) 분리. UTC 저장, KST 귀속 |
| 버저닝 | URL 경로 `/v1/` |

### ERD에 없어 이 명세에서 정한 것

ERD(MS2-26)는 데이터 구조를 정하고, API 표면은 MS2-27이 정한다. 아래는 ERD가 명시적으로 이관했거나 ERD 범위 밖이라 여기서 판단한 항목이다. 근거는 모두 `openapi.yaml` 주석에 있다.

| 항목 | 결정 | 지위 |
| --- | --- | --- |
| properties 키 정책 | 스키마 검증은 안 하되 키 50개, 이름 1~64자 상한. 저장은 아무 JSON 값이나 하되 집계 대상은 최상위 키의 문자열/숫자/불리언/null만. 중첩은 저장하되 집계 대상 아님 | ERD가 이관 |
| 중복 재전송 응답 코드 | 201(신규) / 200(기존 반환) | ERD가 이관 |
| 멱등 키 위치 | 제어면은 헤더, 데이터면은 바디 필드 | ERD가 이관 |
| 시각 입력 형식 | RFC 3339. 과거 무제한, 미래 5분 | ERD가 이관 |
| ORGANIZATION 인증 컬럼 | `api_keys` 별도 테이블 요구사항 7개 컬럼 | ERD가 이관, ERD v2 반영 필요 |
| `unresolved` / `unresolved_events_count` | 미해소 참조를 응답에 노출 | ERD 범위 밖, MS2-27 판단 |
| 멱등키 불일치 응답 | 422 | ERD 범위 밖, 근거 약함(아래) |
| `unit_price` 필드명 | DB는 `unit_price_krw`, API는 통화 접미사를 뺀 `unit_price` | API 표면 명명 |
| 403 (키 스코프 부족) | 401(누구인지 모름)과 분리. 전 오퍼레이션에 정의 | API 표면 |
| 409 중복 생성 | 이미 등록된 `external_customer_id`/`code`로 생성 시 `resource_already_exists`. 멱등 처리중(`idempotency_conflict`)과 `type`/`code`로 구분 | API 표면 |
| 조회 기간 기본값 | 생략하면 당월(KST). 두 파라미터는 짝이며 하나만 주면 422 | API 표면, MS2-41 확정 시 청구 기간 정의로 교체 |

근거가 약한 항목이 하나 있다. 멱등키가 같은데 페이로드가 다를 때의 422는 만료된 IETF Internet-Draft를 근거로 삼았다. Stripe는 자기 문서끼리 코드가 어긋나 기준이 못 됐다. 상세는 `openapi.yaml`의 공통 규약 4.2절에 적었다.

## 검사와 미리보기

```bash
cd docs/api

# 린트 2종. 잡는 규칙이 서로 달라 둘 다 돌린다
npm run lint        # Redocly + Scalar 순차
npm run lint:redocly
npm run lint:scalar

# 리뷰용 HTML 생성 (자체 완결 파일이라 리뷰어는 Node 없이 열 수 있다)
npm run docs

# 목 서버. 백엔드 없이 계약대로 응답한다 (MS2-46 선행 개발용)
npm run mock
```

`npm install`은 필요 없다. **이 폴더는 의존성을 선언하지 않는다.** 스크립트가 `npx`로
검사 도구를 버전 고정해 실행하므로 `node_modules`가 생기지 않는다.
`package-lock.json`은 13줄짜리 빈 껍데기이며, 있어야 `npm audit`이 ENOLOCK 에러 없이
동작한다.

이렇게 한 이유는, 검사 도구를 devDependencies로 선언하면 그 도구의 의존성 트리
(560여 개, 취약점 16건)를 **리포가 자기 의존성으로 떠안게 되기 때문**이다. 그 취약점은
전부 Scalar CLI의 upstream(`@fastify/static`, `brace-expansion` 계열)이라 우리가 고칠
수 없다. Redocly와 Scalar는 문서를 검사하고 미리보기를 만드는 일회성 CLI이지 이 리포의
구성 요소가 아니다. 도구 버전은 스크립트에 고정(`@2.41.1`, `@2.0.1`)해 재현성을 지킨다.

`npm audit` 결과는 취약점 0건이다.

린트를 2종 돌리는 이유는 잡는 규칙이 다르기 때문이다. 결함을 일부러 심어 대조한 결과, Redocly 기본 설정은 `tags` 누락과 미정의 tag 참조를 못 잡고 Scalar는 잡는다. 반대로 Scalar는 전부 warning이라 CI를 막지 못한다. Scalar가 넓게 훑어 후보를 찾고, Redocly가 확정된 규칙을 error로 강제하는 역할 분담이다.

`redocly.yaml`은 기준선을 `all`로 두고, 끄는 규칙마다 이유를 주석에 적었다. `recommended`로는 "경고 0건"이 나오지만 그건 명세가 좋아서가 아니라 검사가 약해서다.

Scalar CLI는 Node 24 이상이 필요하다. 버전이 낮으면 `lint:scalar`와 `mock`이 실행되지 않는다. 생성물(HTML)은 `.gitignore`로 제외되어 있다.

## MS2-31에서 정할 것

- CI에서 `cd docs/api && npm run lint` 실행. `npm ci`는 필요 없다(의존성 없음)
- `redocly.yaml`과 `package.json` 위치. 지금은 스펙 옆에 두어 상대 경로가 맞는다. 리포 루트로 옮길지는 CI 구성 시 판단한다
- 리포 루트에 `.node-version`(24) 추가 검토. Scalar CLI가 Node 24를 요구하는데 팀원 환경이 낮으면 그 자리에서 막힌다. 개발 환경 범위라 이 PR에 넣지 않았다

처리 시점은 PR #11 논의에서 합의했다. **두 PR 중 나중에 머지되는 쪽에 넣거나, 둘 다 머지된 뒤 별도 PR로 처리한다.** #11이 열려 있는 동안에는 서로의 변경을 확인할 수 없어 한쪽에서 미리 넣으면 충돌만 는다.
