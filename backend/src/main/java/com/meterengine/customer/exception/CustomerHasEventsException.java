package com.meterengine.customer.exception;

import java.util.UUID;

/**
 * 사용량 이벤트가 있는 고객을 지우려 했을 때 (MS2-155). 409로 매핑된다.
 *
 * <p>이벤트는 청구 근거이고 append-only라 지울 수 없다(V1). 고객만 사라지면 그 이벤트는 가리킬 대상이 없는 채로 남아 어느 청구서에도 오르지 않는다. 실제로
 * V1의 복합 FK {@code usage_event_customer_same_org}가 그 상태 자체를 만들지 못하게 막고, 이 예외는 그 거절을 사용자가 읽을 수 있는
 * 형태로 바꾼 것이다.
 *
 * <p>4xx 중에서도 409인 것은, 요청 자체는 형식과 대상 모두 올바르고 지금 그 리소스의 상태가 그 동작을 허용하지 않기 때문이다. 도입사가 요청을 고쳐서 될 일이
 * 아니다.
 *
 * <p><b>이 규칙은 "일단"이다.</b> 이벤트가 있어도 지울 수 있게 하려면 고객 행을 남기면서 감추는 방식(소프트 삭제)으로 가야 하고, 그러면 모든 조회가 "지워진
 * 것은 빼고"라는 조건을 들고 다녀야 한다. 지운 고객의 과거 사용량을 청구서에 계속 실을지도 함께 정해야 한다. 그 요구가 실제로 생기면 그때 다룬다.
 */
public class CustomerHasEventsException extends RuntimeException {

  public CustomerHasEventsException(UUID customerId) {
    super(
        "customer %s has usage events; deleting it would hide billable usage from the invoice"
            .formatted(customerId));
  }
}
