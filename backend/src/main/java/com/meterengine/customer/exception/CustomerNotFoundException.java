package com.meterengine.customer.exception;

import java.util.UUID;

/**
 * 고칠 또는 지울 고객을 찾지 못했을 때 (MS2-155). 404로 매핑된다.
 *
 * <p>두 경우가 한 예외로 묶인다. 그런 고객이 없거나, 다른 도입사 소속이거나. 구별해 답하지 않는 이유는 수집 API의 {@code
 * com.meterengine.event.exception.UnknownCustomerException}과 같다. 남의 도입사에 그 고객이 있다는 사실을 흘리지 않는다.
 *
 * <p>지운 고객은 행 자체가 사라지므로 "없는 고객"과 구별되지 않는다. 그래서 같은 고객을 두 번 DELETE 하면 두 번째는 404다. DELETE를 여러 번 불러도 같은
 * 결과를 기대하는 쪽에서는 뜻밖일 수 있어 컨트롤러 문서에 적어 두었다.
 *
 * <p><b>수집 API와 code가 다르다.</b> 저쪽은 400에 {@code unknown_customer_reference}, 이쪽은 404에 {@code
 * customer_not_found}다. 고객이 요청 본문이나 쿼리의 값이 아니라 경로가 가리키는 리소스 자체라 상태 코드가 갈리고, code 하나는 (HTTP 상태, 의미)
 * 하나만 가리킨다는 것이 MS2-150이 정한 규칙이다. {@code customer_not_found}는 그 규칙에 따라 MS2-150이 이 자리에 쓰라고 비워 둔 이름이다
 * ({@code ErrorCodes.UNKNOWN_CUSTOMER_REFERENCE} javadoc 참조).
 */
public class CustomerNotFoundException extends RuntimeException {

  public CustomerNotFoundException(UUID organizationId, UUID customerId) {
    super(
        "no customer %s in organization %s; it may not exist or belong to another organization"
            .formatted(customerId, organizationId));
  }
}
