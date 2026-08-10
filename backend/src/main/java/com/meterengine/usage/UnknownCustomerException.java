package com.meterengine.usage;

import java.util.UUID;

/**
 * (도입사, 고객) 조합을 찾지 못했을 때 (MS2-130). 400으로 매핑된다.
 *
 * <p>보존하지 않고 거절하는 이유: 도입사가 우리가 발급한 customer.id(UUID)를 그대로 실어 보내는 구조라, 우리가 발급하지 않은 UUID는 나중에 고객을
 * 만들어도 영영 매칭되지 않는다. 이름이나 alias로 보내는 구조였다면 미해소로 보존할 여지가 있다.
 *
 * <p>메시지가 도입사 ID까지 밝히는 이유: 조회가 (organization_id, customer_id) 조합이라 X-Organization-Id를 잘못 보내도 이 예외가
 * 난다. customer_id만 지목하면 멀쩡한 고객 등록을 의심하게 된다. 다만 어느 쪽이 틀렸는지는 알려주지 않는다 (CustomerRepository javadoc
 * 참조).
 */
class UnknownCustomerException extends RuntimeException {

  UnknownCustomerException(UUID organizationId, UUID customerId) {
    super(
        "no customer %s in organization %s; check both the X-Organization-Id header and customer_id"
            .formatted(customerId, organizationId));
  }
}
