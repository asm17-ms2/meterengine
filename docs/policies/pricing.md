# pricing 정책

가격 정책 등록과 미터별 가격 조회가 쓰는 값이다. 단가 등록과 다차원 계산은 아직 이 파일이 정하는 값이 없다.

## 정책 등록

등록이 받는 것과 미터당 정책 개수의 정본은 `backend/openapi.yaml`의 `createPricePolicy` description이다.

| 항목 | 값 | 코드 위치 | 근거 |
|---|---|---|---|
| 확인과 INSERT 사이의 경합 | 같은 409로 바꾼다 | `PricePolicyServiceTest.확인과_INSERT_사이의_경합도_AlreadyExists로_바뀐다` | PR #43 |
| 저장 방식 | 항상 새 행으로 persist한다 | `PricePolicy.isNew` | PR #43 |

왜 단가를 받지 않는가: 단가는 유효 기간 같은 자체 수명을 가질 예정이라 등록 시점이 정책과 다르다. 단가가 없는 미터를 청구 예정액이 어떻게 다루는지는 invoice 정책의 몫이다.

왜 덮어쓰지 않는가: 청구 예정액이 읽는 축 선언과 단가를 등록 요청이 조용히 바꾸게 된다. save()가 기존 키를 만나면 UPDATE로 덮어쓰므로 항상 persist로 두고, 중복은 제약 위반으로 터뜨려 409로 바꾼다.

## 등록 검증

형식 검증과 도메인 검증의 정본은 `backend/openapi.yaml`의 `createPricePolicy` 400 description이다.

왜 `validation_error`와 가르는가: 중복 키와 빈 키는 필드 하나의 형식이 아니라 필드 사이의 관계라 어느 한 필드를 짚을 수 없다.

왜 앱이 검증하는가: 선언은 단가 조합의 키 집합을 해석하는 기준이고, 어긋난 선언은 DB가 잡지 못한 채 계산이 닿지 못하는 단가 행을 만든다.

## 미터 확인

없는 미터와 다른 도입사의 미터를 구별하지 않는 것의 정본은 `backend/openapi.yaml`의 `billable_metric_not_found` description이다.

| 항목 | 값 | 코드 위치 | 근거 |
|---|---|---|---|
| 확인 순서 | 미터 존재 확인이 선언 검증과 정책 중복 확인보다 먼저다 | `PricePolicyService.create` | PR #43 |

왜 구별하지 않는가: 구별해 답하면 다른 도입사에 그 미터가 있다는 사실이 새어 나간다.

왜 미등록 도입사도 404인가: 미터 존재 확인이 먼저 걸려 organization FK 위반에 닿는 경로가 없다. 그래서 400 `unknown_organization`이 나올 자리가 없다.

## 단가

기본 단가 조합과 기본 단가가 없는 미터의 정본은 `backend/openapi.yaml`의 `BillableMetricPricePolicyResponse` 스키마다.

| 항목 | 값 | 코드 위치 | 근거 |
|---|---|---|---|
| dimension_values의 자바 타입 | jsonb 텍스트 그대로의 String | `PriceRate.dimensionValues` | PR #41 |

왜 `{}`인가: 무차원 미터에도 단가가 붙을 조합이 하나 필요하다. 이 조합은 `PriceRate.BASE_COMBINATION`이다. 어느 조합에도 맞지 않는 이벤트를 이 단가로 계산한다는 합의가 있었으나 다차원 계산이 아직 없어 코드가 적용하지 않는다.

왜 String인가: 이 컬럼이 PK의 일부라 식별자의 동등성이 jsonb가 정규화한 텍스트로 정의돼야 영속성 컨텍스트가 같은 행을 같은 엔티티로 본다. Map이면 키 순서가 다른 같은 조합이 다른 식별자가 된다. 조합을 구조로 다뤄야 하면 그때 파싱 계층을 얹는다.

## 미터별 가격 조회

| 항목 | 값 | 코드 위치 | 근거 |
|---|---|---|---|
| 요소 단위 | 미터. 정책이 없는 미터도 실리고 dimension_properties가 null이다 | `BillableMetricPriceResponse.of`, `PricePolicyIntegrationTest.정책이_없는_미터는_dimension_properties가_JSON_null로_실린다` | PR #76 |
| 싣는 것 | 미터 code, 정책의 축 선언, 기본 단가. 미터 이름과 집계 기준은 싣지 않는다 | `BillableMetricPriceResponse` | PR #76 |
| 순서와 페이지 | 미터 code 오름차순, 페이지 나누지 않음 | `BillableMetricRepository.findByOrganizationIdOrderByCodeAsc`, `PricePolicyIntegrationTest.목록은_미터_code_오름차순이다` | PR #76 |

왜 정책 없는 미터도 싣는가: 화면이 정책을 붙일 미터를 고르려면 정책 없는 미터도 보여야 한다. 정책 있는 것만 내면 프론트엔드가 미터 목록과 차집합을 내게 되어 화면에 판단 로직이 생긴다.

왜 미터 이름을 싣지 않는가: 미터 목록을 내는 경로가 미터 조회와 사용량 조회에 이어 셋이 된다.

## 저장 구조

| 항목 | 값 | 코드 위치 | 근거 |
|---|---|---|---|
| 저장소 접근 | JPA | `PricePolicyRepository`, `PriceRateRepository` | PR #43 리뷰 |
| 복합 PK 클래스 | record가 아니라 클래스 | `PricePolicyId`, `PriceRateId` | 초기 관례 |

왜 JPA인가: pricing 리포지토리를 한 방식으로 통일한다.

왜 클래스인가: JPA 스펙이 IdClass에 public no-arg 생성자를 요구한다.
