# 이벤트 멱등성

수집 이벤트의 중복 판정 규칙. 제어면 API(등록 등)의 멱등키 규약은 `docs/api/openapi.yaml` 공통 규약이 정본이다.

## 규칙

| 항목 | 값 | 근거 / 변경 가능성 |
|---|---|---|
| 유일성 범위 | (organization_id, transaction_id) | ADR-0006. 강화 방향 변경 불가, 구독 도입 시 완화만 가능 |
| 비교 규칙 | 바이트 단위 정확 일치 (대소문자 구분, 해석과 정규화 없음) | ADR-0006. 비구분으로 변경 시 기존 데이터 충돌 가능 |
| 빈 값 | 금지 | ADR-0006 |
| 길이 상한 | 255자 | ADR-0006. 인덱스 비대 방지, 완화는 가능하나 사유 필요 |
| 보존 기간 | 무기한 (기간 윈도우 없음) | ADR-0006. 파티셔닝 도입 시점에 강제 재검토 |
| 중복 시 저장 | first-write-wins, 저장본 불변 | ADR-0008의 따름정리 |
| 중복 시 응답 | API 명세 참조 | `docs/api/openapi.yaml` (MS2-27) |

## 경계

- 같은 transaction_id로 내용이 다른 재전송이 와도 저장본은 바뀌지 않는다
- payload 해시 컬럼은 두지 않는다. 원문이 보존되므로 필요 시 backfill 가능 (ADR-0008)
- 배치 수집은 S1 구현 범위 밖이다 (명세만 존재, ADR-0003)

## 관련

- ADR-0006 이벤트 멱등키, ADR-0008 raw 이벤트 불변성
- 스키마: `docs/erd/erd.md` USAGE_EVENT 유니크 제약, `backend/src/main/resources/db/migration/V1__create_s1_core_tables.sql`
- 구현/테스트: 수집 API 구현 시(MS2-38) 링크를 채운다
