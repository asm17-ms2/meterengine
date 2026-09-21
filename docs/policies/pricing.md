# pricing 정책

가격 정책 등록과 미터별 가격 조회가 쓰는 값이다. 단가 등록과 다차원 계산은 아직 이 파일이 정하는 값이 없다.

## 정책 등록

등록이 받는 것과 미터당 정책 개수의 정본은 `backend/openapi.yaml`의 `createPricePolicy` description이다.

## 등록 검증

형식 검증과 도메인 검증의 정본은 `backend/openapi.yaml`의 `createPricePolicy` 400 description이다.

## 단가

기본 단가 조합과 기본 단가가 없는 미터의 정본은 `backend/openapi.yaml`의 `BillableMetricPriceResponse` 스키마다.

## 미터별 가격 조회

정책이 없는 미터를 싣는 것, 싣는 필드, 순서와 페이지의 정본은 `backend/openapi.yaml`의 `listBillableMetricPrices` description이다.
