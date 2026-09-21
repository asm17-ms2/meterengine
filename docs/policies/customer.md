# customer 정책

고객 등록, 수정, 삭제, 목록 조회가 쓰는 값이다.

## 이름

길이 상한과 빈 이름 거절, 이름 중복 허용, 고칠 수 있는 필드의 정본은 `backend/openapi.yaml`의 `createCustomer`와 `updateCustomer` description이다.

왜 중복을 허용하나: 스키마에 유니크 제약이 없고, 같은 이름의 계열사나 부서를 따로 관리하는 것을 막을 근거가 지금 없다. 구별은 화면이 함께 보여주는 id의 몫이다.

## 목록 정렬과 응답 모양

정렬과 페이지를 나누지 않는 것, 응답 최상위 모양, 고객이 없을 때의 정본은 `backend/openapi.yaml`의 `listCustomers` description과 `ListCustomersResponse` 스키마다.

| 항목 | 값 | 코드 위치 | 근거 |
|---|---|---|---|
| 이름 순서를 정하는 곳 | `customer.name`의 collation | `CustomerIntegrationTest.목록은_한국어_사전순이다` | PR #45 |

왜 id를 두 번째 키로 두나: 정렬이 없으면 반환 순서가 보장되지 않아 같은 데이터에도 화면의 행 순서가 매번 달라지고, 동명이인이 있으면 이름만으로는 순서가 흔들린다. 파생 쿼리라 정렬은 DB가 하고 자바 계층에서 다시 정렬하지 않는다.

왜 페이지를 나누지 않나: "그 도입사의 전부"가 이 응답의 정의다. 사용량과 청구 예정액 응답이 이미 고객 전원을 담고 있어서 여기만 나누면 같은 집합을 화면마다 다른 크기로 보게 된다. 이벤트 조회가 page/size를 갖는 것은 원시 로그가 끝없이 쌓이기 때문이라 경우가 다르다.

왜 객체로 감싸나: 고객 수는 도입사의 매출처 규모를 따라 자란다. 페이지가 필요해지는 날 최상위가 배열이면 `total`이나 `page`를 얹을 자리가 없어 응답 모양을 통째로 바꾸게 된다.

## 등록 시각

응답에 실리는 곳의 정본은 `backend/openapi.yaml`의 `CustomerResponse` 스키마다.

| 항목 | 값 | 코드 위치 | 근거 |
|---|---|---|---|
| 값을 만드는 쪽 | DB. 앱은 되읽기만 한다 | `Customer.createdAt`의 `@Generated(EventType.INSERT)`, `CustomerIntegrationTest.등록하면_201과_발급된_id와_등록시각이_오고_목록에_보인다` | PR #44 |
| 기본값 | `clock_timestamp()` | `V3__add_customer_created_at.sql` | PR #44 |
| 수정 | UPDATE 문에 싣지 않는다 | `Customer.createdAt`의 `updatable = false` | PR #44 |

왜 값을 만드는 자리를 자바에 두지 않나: setter도 초기화식도 없고 생성자에서도 받지 않아서, 애노테이션이 어떤 이유로든 안 먹으면 INSERT가 `null`을 보내고 DB가 거절한다. 이 구조가 애노테이션 오류를 조용한 오작동이 아니라 빨간불로 만드는 전제라, 여기에 setter나 초기화식을 더하면 그 전제가 깨진다.

왜 `insertable = false`가 아닌가: 그쪽도 컬럼을 INSERT에서 빼지만 되읽지 않아서, 등록 응답의 `created_at`만 `null`로 나가고 수정과 목록은 정상인 상태가 된다.

남은 빈칸: `openapi.yaml`의 `CustomerResponse`는 `created_at`을 `required`로 두지 않았는데, 고객 화면은 늘 온다고 보고 문자열로 받는다 (`frontend/src/lib/api/customers.ts`). 계약과 소비자가 갈려 있어 `required` 여부를 정해야 한다.

## 엔티티와 도입사 스코프

id를 서버가 발급하는 것과 남의 도입사 고객을 미등록과 구별하지 않는 것의 정본은 `backend/openapi.yaml`의 `createCustomer` description과 `customer_not_found` description이다.

| 항목 | 값 | 코드 위치 | 근거 |
|---|---|---|---|
| 조회 조건 | organization_id를 함께 넣는다 | `CustomerRepository`의 메서드 이름, `CustomerIntegrationTest.다른_도입사_고객은_고칠_수_없다` | 초기 관례 |
| organization_id 매핑 | `@ManyToOne`이 아니라 UUID 컬럼 | `Customer.organizationId` | 초기 관례 |
| 도입사 존재 확인 | 하지 않는다. FK가 거절하면 400으로 바꾼다 | `CustomerServiceTest.등록되지_않은_도입사로_DB가_거절하면_400_예외로_바뀐다` | 초기 관례 |
| 엔티티 동일성 | `equals`/`hashCode`를 두지 않는다 | `Customer` | 초기 관례 |

왜 id를 받지 않나: 도입사가 id를 정하게 하면 남의 도입사 고객의 id를 넘겨 무슨 일이 벌어지는지 시험할 수 있다.

왜 구별하지 않나: 남의 도입사에 그 고객이 있다는 사실을 흘리지 않는다.

왜 UUID 컬럼인가: 도입사 자체를 다루는 코드가 아직 없어 Organization 엔티티가 없고, 조회가 늘 도입사 하나로 좁혀지는 구조라 테넌트 스코프를 조건으로 명시하는 편이 격리를 눈에 보이게 한다.

왜 미리 확인하지 않나: 조회와 INSERT 사이가 다시 경합 구간이라 어차피 FK를 신뢰해야 한다.

왜 동일성 규칙을 두지 않나: 이 엔티티로 컬렉션 비교나 병합을 하지 않아서, JPA 엔티티의 동일성 규칙을 정하는 논의를 지금 끌어올 필요가 없다.

## 삭제

이벤트가 있는 고객을 지우지 않는 것과 행을 실제로 지우는 것의 정본은 `backend/openapi.yaml`의 `deleteCustomer` description이다.

| 항목 | 값 | 코드 위치 | 근거 |
|---|---|---|---|
| 확인과 DELETE 사이의 경합 | 앱이 잠그지 않고 DB에 맡긴다 | `CustomerDeleteConcurrencyTest.이벤트가_커밋되기_전에_들어온_삭제는_대기하다_FK_위반으로_끝난다` | PR #39 |
| 확인을 통과한 뒤 DB가 거절할 때 | 확인 단계와 같은 예외로 바꾼다 | `CustomerServiceTest.확인_뒤에_DB가_거절하면_같은_409_예외로_바뀐다` | PR #39 |

왜 행 잠금(`SELECT ... FOR UPDATE`)을 걸지 않나: 지키려는 것은 "이벤트가 있는 고객은 사라지지 않는다"인데 물리 DELETE에서는 DB가 `event_customer_same_org` 복합 FK로 그것을 직접 막는다. 이벤트를 넣는 트랜잭션이 고객 행에 FK 검사용 공유 잠금(FOR KEY SHARE)을 잡으므로 그 사이의 DELETE는 기다리고, 그 트랜잭션이 커밋되면 기다리던 DELETE는 FK 위반으로 실패한다. 앱이 따로 잠글 이유가 없고, 그 성질이 실제로 성립하는지는 위 동시성 테스트가 Postgres에 물어본다. 그 테스트는 커넥션을 직접 두 개 열고 `@Transactional`을 붙이지 않는다. 붙이면 테스트 전체가 한 트랜잭션이라 두 트랜잭션이 겹치는 상황 자체를 만들 수 없다.
