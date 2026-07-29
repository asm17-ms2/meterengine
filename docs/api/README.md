# API 명세

핵심 API를 OpenAPI 스펙으로 명세한다 (MS2-27). MVP 전 범위를 개발 전에 명세하지 않고, 슬라이스 범위만큼 명세한 뒤 구현에서 배운 것을 반영해 버전을 올린다.

## 규칙

- 스펙 파일은 이 폴더에 둔다 (예: `openapi.yaml`). 파일 상단 `info.version`과 설명에 반영 범위(어느 슬라이스까지)를 명시한다
- 전 슬라이스 공통 규약을 먼저 확정한다: 멱등 규약, 에러 코드 체계, 인증 방식, 버저닝
- 스펙 변경은 구현과 같은 PR에서 리뷰한다
- **결정 근거는 `openapi.yaml` 안의 주석에 둔다.** 별도 설명 문서를 만들지 않는다 — 코드와 근거가 떨어지면 코드만 고쳐지고 근거는 낡는다. 주석에는 *무엇을 하는지*가 아니라 **왜 그렇게 정했는지 · 무엇을 탈락시켰는지 · 언제 재검토하는지**를 적는다

## 현재 상태

**v1 (슬라이스 1) 작성 완료** — 2026-07-29.

- 범위: 이벤트 수집 단건/배치(배치는 명세만, 구현은 S1 제외), 미터 정의, 단가 설정, 집계 조회, draft 인보이스 조회. 엔드포인트 9개
- 전제: MS2-26 불변 결정 목록 + ERD v1, MS2-28 ADR 0001~0005를 반영했다
- 인보이스 확정·부가세·결제·세금계산서·크레딧·구독은 이 버전에 없다 (S2 이후)

### 공통 규약 요약

| 항목 | 결정 |
|---|---|
| 인증 | `Authorization: Bearer <API_KEY>`. 키가 도입사를 암묵 식별 |
| 식별자 | **API 표면은 `external_customer_id`·`code` 단일 축.** 내부 UUID 비노출 |
| 에러 | RFC 9457 Problem Details (`application/problem+json`) + snake_case `code` 확장 |
| 멱등 | 제어면 = `Idempotency-Key` 헤더 / 이벤트 = 바디 `transaction_id`. 중복은 **200(성공)** |
| 금액 | 원 단위 정수. 단가는 `(unit_amount, unit_size)` 정수 쌍 |
| 시각 | RFC 3339. `occurred_at`(필수) / `received_at`(서버) 분리. UTC 저장·KST 귀속 |
| 버저닝 | URL 경로 `/v1/` |

### ERD가 MS2-27로 이관한 항목의 반영 위치

| 이관 항목 | 반영 위치 |
|---|---|
| properties 키 정책 | `EventInput.properties` (키 개수·이름 길이·값 타입·중첩·대소문자) |
| 이벤트 요청 스키마, 시각 입력 형식 | `EventInput`, 공통 규약 6절 |
| 중복 재전송 응답 코드 | `POST /events`의 201/200 (공통 규약 4.3) |
| ORGANIZATION의 인증(API 키) 컬럼 | `securitySchemes` 위 주석 — `api_keys` 별도 테이블 요구사항 |

## 검사와 미리보기

```bash
cd docs/api

# 린트 2종 — 잡는 규칙이 서로 달라 둘 다 돌린다
npx @redocly/cli lint openapi.yaml            # 경로 모호성, 예시-스키마 불일치
npx @scalar/cli document lint openapi.yaml    # description 누락 등 (spectral)

# 리뷰용 HTML 생성 (자체 완결 파일 — 리뷰어는 Node 없이 열 수 있다)
npx @redocly/cli build-docs openapi.yaml -o api-preview.html

# 목 서버 — 백엔드 없이 계약대로 응답한다 (MS2-46 선행 개발용)
npx @scalar/cli document mock openapi.yaml --port 8787 --watch
```

Scalar CLI는 **Node 24 이상**이 필요하다 (리포 `.node-version` 참조).
생성물(`api-preview.html`)은 커밋하지 않는다.

## MS2-31에서 정할 것

- CI에 린트 2종 연결
- `redocly.yaml` 위치 — 지금은 스펙 옆(`docs/api/`)에 두어 상대 경로가 맞는다. CI 실행 편의상 리포 루트로 옮길지는 CI 구성 시 판단한다
- `.gitignore`에 생성물(`api-preview.html`) 추가
- `package.json`에 `mock` 스크립트 (MS2-46이 백엔드 없이 화면을 개발할 수 있게)
