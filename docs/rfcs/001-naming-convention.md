---
status: "accepted"
date: 2026-09-04
author: 문인호
domain: process
---

# RFC-001: 이름 규칙을 표준 관례에 맞춰 정한다

## 배경 및 문제 정의

주석과 javadoc을 쓰지 않기로 하면서 이름이 설명 부담을 전부 지게 됐는데 이름 규칙은 없다. 8월 말부터 고치기 시작해 노션 제안서([주입 필드][prop-inject], [클래스명][prop-class], [도메인 예외][prop-exception])과 PR([청구 응답 스키마 이름 충돌 수정][c-1eb7906], [DraftInvoiceService 내부 이름 구체화][c-ea7abd2])이 나왔고, [내부 이름 구체화 PR 리뷰][pr-111]에서는 복수형 vs `List` 접미사가 결론 없이 머지됐다. 제안서는 규칙을 CONTRIBUTING.md에 적기로 했지만 그 사이 RFC-000이 결정 문서를 여기로 옮겼다.

이 RFC는 제안서와 PR의 방향을 하나로 합치고, 제안서가 다루지 않은 항목(컬렉션, 테스트 이름, 파일과 패키지, 프론트)을 채운다. 기준은 하나다. **외부 표준 관례를 따르고, 벗어날 때는 이유를 적는다.** 대조한 근거는 Google Java/TypeScript Style Guide, JLS §6.1, JavaBeans 명세, JDK와 Spring의 실제 API 이름, Google API 설계 가이드(AIP), Zalando API 가이드, Stripe API, React와 Next.js 공식 문서, Vercel 공식 예제(next-learn), Airbnb 가이드, typescript-eslint `naming-convention` 규칙의 옵션 기본값이다. 근거의 종류는 결정 절의 근거 열에, 링크는 문서 끝 참고 문서에 있다. 표준이 없는 항목은 이름을 고를 자유도가 가장 작은 쪽을 택했다.

## 검토한 선택지

항목마다 대안을 늘어놓는 대신 "왜 그쪽인가"로 묶었다. 대안은 괄호 안이다.

**표준이 답해서 그대로 따른 것.**

- DTO 어순은 동사 먼저 `CreateCustomerRequest`, `ListCustomersResponse` (대안: 대상 먼저 `CustomerListResponse`). [AIP-133][aip-133]과 [AIP-132][aip-132]. 단건 `<리소스>Response` 접미사만 팀 선택이다. 지금 코드의 `CustomerListResponse`는 `ListCustomersResponse`가 되고, `SaveCustomerRequest`는 `CreateCustomerRequest`와 `UpdateCustomerRequest`로 갈린다
- 만드는 동사는 `create` (대안: `register`). [AIP-133][aip-133], [Stripe][stripe], Spring Security [`UserDetailsManager`][ss-udm]의 `createUser`/`updateUser`/`deleteUser`, JDK [`Files.createFile`][jdk-files], [Kill Bill][killbill]. `register`는 콜백이나 드라이버를 등록소에 거는 동작에 쓰이는 낱말이다. 지금 코드의 `BillableMetricService.register()`, `PricePolicyService.register()`가 `create()`가 된다
- 컬렉션은 복수형, 접미사 없음 (대안: `List` 접미사). [Zalando 규칙 120][zalando-120], [AIP-140][aip-140], [Google TS][gts]. 리스트와 타입명이 헷갈리면 요소 타입을 단수 개념으로 다시 짓는다. [내부 이름 구체화 리뷰][pr-111]의 `metricQuantitiesByCustomerList` 대 `metricQuantitiesByCustomers`에서 뒤쪽이다
- 응답 안 요소는 `<응답 리소스><JSON 필드명 단수>` (대안: `springdoc.use-fqn=true`는 패키지 경로가 새고, `@Schema(name)`은 잊으면 조용히 병합된다). Stripe와 Google 클라이언트가 중첩으로 얻는 한정을 접두어로 얻는다. 사용량 응답과 청구 응답에 `CustomerEntry` 중첩 record가 둘 있어 springdoc이 바깥 클래스명을 버리고 openapi.yaml에서 하나로 합쳤던 것([1eb7906][c-1eb7906])을 `DraftInvoiceCustomer`처럼 접두어로 막는다
- 주입 필드는 타입명 (대안: 복수 도메인 명사 `customers`, PetClinic 방식). [Spring Framework 레퍼런스][sf-di]의 DI 예제가 `movieFinder`이고, [PetClinic][petclinic] 안에서도 `owners`와 `vetRepository`가 섞였다. 주입 필드는 역할이 곧 타입이라 타입명이 곧 역할명이다

**표준이 침묵해서 자유도가 작은 쪽을 고른 것.**

- 컴포넌트는 여럿이면 복수 (대안: 접미사별 고정). [next-learn][next-learn]은 복수, [react-admin][react-admin]은 단수. 이름이 "무엇이 여럿인가"를 말하게 한다
- 초기값 상수는 `FORM_IDLE` (대안: `initialFormState`, React 문서 관례). 둘 다 표준 안이고, `IDLE`은 초기값이자 되돌아가는 값이라 더 정확하다
- 도메인 없는 공용 코드는 `global.<역할>`, 최상위 루트에는 진입점만 (대안: 최상위 루트에 그냥 두기, PetClinic의 `system`). [Spring Boot 레퍼런스][sb-structure]는 진입점을 루트 패키지에 두라고만 하고, 예제 배치의 루트에는 진입점뿐이다. [PetClinic][petclinic]은 루트에 진입점만 두고 설정과 공용 컨트롤러를 `system`에 모은다. 패키지 이름은 표준이 없어 오류 처리 RFC가 쓰려는 `global`에 맞춘다. 역할 이름은 그 역할을 들여오는 RFC가 정하며 이 RFC는 `config`만 정한다

**표준과 다르게 가는 것.** 이유가 서지 않으면 표준으로 돌아간다.

- 한국어 테스트 메서드명. [Google Java 5.1][gj-5.1](ASCII만)과 [네이버 핵데이][naver]에 어긋난다. 테스트 이름이 곧 명세라 한국어가 가장 정확하고, 우아한테크코스 [prolog][prolog]와 [2023-zipgo][zipgo]가 쓴다. 변수와 헬퍼로 번지지 않게 `@Test`와 `@Nested`에만 허용한다
- 컴포넌트 파일 PascalCase. Next.js 생태계 다수([next-learn][next-learn], [shadcn/ui][shadcn])와 [Google TS][gts]는 kebab이다. [Airbnb React][airbnb-react]를 따르며, 파일명만으로 컴포넌트가 구분되는 이점을 택한다
- 열거값 lowercase snake_case. [Zalando 규칙 240][zalando-240]과 [AIP-126][aip-126]은 UPPER다. 이미 나간 에러 코드와 통일한다

## 결정

선택한 것: 외부 표준 관례를 따르고, 벗어날 때는 이유를 적는다. 위 검토한 선택지에서 대안으로 적은 방식은 쓰지 않는다. 이유: 표준이 답하는 항목은 표준을 따르고, 표준이 침묵하는 항목은 팀이 다르게 지을 여지가 가장 작은 쪽을 골랐다.

항목별 이름 표와 원칙은 규칙이라 이 문서가 아니라 규칙 파일이 정본이다. 코드 개명은 이 RFC가 아니라 별도 티켓에서 한다.

### 결과

- 좋은 점: 리뷰에서 이름 얘기가 준다. 주석 없이 이름만으로 "없을 수 있는가"(`find`/`get`), "저장하는가"(`preview`/`aggregate`), "무엇의 테스트인가"(관점 접미사), "여럿인가"(복수 단수)를 안다
- 나쁜 점: 표준과 다르게 가는 항목(한국어 테스트명, 열거값 lowercase, 컴포넌트 파일 PascalCase)을 외부에서 온 사람이 낯설어한다. 이 문서가 이유를 적어 두는 자리다
- 나쁜 점: 이름이 길어진다(`UpdateBillableMetricRequest`). 이름 길이 상한을 두지 않는 것으로 감수한다
- 나쁜 점: 약칭 금지가 계약과 스키마까지 번져 개명이 엔티티가 사는 도메인 밖으로 나간다. `BillableMetric`의 약칭 `metric`이 URL `/v1/metrics`와 `{metricCode}`, 가격 정책과 청구 응답의 JSON `metric_code`, `price_policy`와 `price_rate`와 `invoice_line`의 `metric_code` 컬럼, 프론트 라우트 `/metrics`, demo CLI가 읽는 `metric_code`에 퍼져 있다. 개명은 MS2-263에서 하되 계약 변경이라 백엔드와 프론트와 demo를 같은 슬라이스에서 맞춘다

## 참고 문서

본문의 링크가 가리키는 곳이다.

[gj-5.1]: https://google.github.io/styleguide/javaguide.html#s5.1-identifier-names
[gts]: https://google.github.io/styleguide/tsguide.html#identifiers
[sb-structure]: https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html
[sf-di]: https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html
[ss-udm]: https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/provisioning/UserDetailsManager.html
[petclinic]: https://github.com/spring-projects/spring-petclinic
[jdk-files]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/file/Files.html
[aip-126]: https://google.aip.dev/126
[aip-132]: https://google.aip.dev/132
[aip-133]: https://google.aip.dev/133
[aip-140]: https://google.aip.dev/140
[zalando-120]: https://opensource.zalando.com/restful-api-guidelines/#120
[zalando-240]: https://opensource.zalando.com/restful-api-guidelines/#240
[stripe]: https://docs.stripe.com/api
[killbill]: https://killbill.github.io/slate/
[next-learn]: https://github.com/vercel/next-learn
[airbnb-react]: https://github.com/airbnb/javascript/tree/master/react#naming
[naver]: https://naver.github.io/hackday-conventions-java/
[prolog]: https://github.com/woowacourse/prolog
[zipgo]: https://github.com/woowacourse-teams/2023-zipgo
[shadcn]: https://github.com/shadcn-ui/ui
[react-admin]: https://github.com/marmelab/react-admin
[prop-inject]: https://app.notion.com/p/3cd0899b32b881e6982ace620f340449
[prop-class]: https://app.notion.com/p/3cd0899b32b881aa84e0cb60941bc3b6
[prop-exception]: https://app.notion.com/p/3cd0899b32b881328dcbd7a73bed819d
[c-ea7abd2]: https://github.com/asm17-ms2/meterengine/commit/ea7abd2
[c-1eb7906]: https://github.com/asm17-ms2/meterengine/commit/1eb7906
[pr-111]: https://github.com/asm17-ms2/meterengine/pull/111
