# 도메인 모델 / ERD

도메인 개념도를 ERD 수준으로 구체화해 관리한다 (MS2-26). 전 범위를 한 번에 확정하지 않는다.

## 규칙

- 첫 슬라이스 전 확정 대상은 되돌리기 어려운 결정뿐이다: 멱등 처리(transaction_id), 시각 필드(occurred_at/received_at), raw 이벤트 불변성
- 엔티티별 상세화는 슬라이스 1 범위부터 하고, 이후 슬라이스마다 버전을 올린다
- MVP에서 제외된 개념은 확장 여지만 남기고 모델에서 제외한다
- 문서 상단에 버전과 반영 범위를 명시한다

## 현재 상태

- `erd.md`: S1 ERD v2 (정본). mermaid 다이어그램 + 제약 표 + 스토리별 이관 항목
- S1 확정분의 DB 반영은 `backend/src/main/resources/db/migration/V1__create_s1_core_tables.sql`
