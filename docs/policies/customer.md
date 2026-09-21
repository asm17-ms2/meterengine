# customer 정책

고객 등록, 수정, 삭제, 목록 조회가 쓰는 값이다.

## 이름

길이 상한과 빈 이름 거절, 이름 중복 허용, 고칠 수 있는 필드의 정본은 `backend/openapi.yaml`의 `createCustomer`와 `updateCustomer` description이다.

## 목록 정렬과 응답 모양

정렬과 페이지를 나누지 않는 것, 응답 최상위 모양, 고객이 없을 때의 정본은 `backend/openapi.yaml`의 `listCustomers` description과 `ListCustomersResponse` 스키마다.

| 항목 | 값 | 코드 위치 |
|---|---|---|
| 이름 비교 | 한국어 사전순 | `customer.name`의 collation, `CustomerIntegrationTest.목록은_한국어_사전순이다` |

## id 발급

id를 서버가 발급하는 것의 정본은 `backend/openapi.yaml`의 `createCustomer` description이다.

## 삭제

이벤트가 있는 고객을 지우지 않는 것과 지운 고객을 되돌릴 수 없는 것의 정본은 `backend/openapi.yaml`의 `deleteCustomer` description이다.
