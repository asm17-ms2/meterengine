package com.meterengine.customer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.customer.entity.Customer;
import java.util.List;

/**
 * 고객 목록 응답 (MS2-155).
 *
 * <p><b>페이지를 나누지 않는다.</b> 이벤트 조회가 page/size를 갖는 것은 원시 로그가 끝없이 쌓이기 때문이고, 사용량과 청구 예정액이 갖지 않는 것은 "그
 * 도입사의 전부"가 응답의 정의이기 때문이다. 고객 목록은 뒤쪽이다. 게다가 그 두 응답이 이미 고객 전원을 담고 있어서, 여기만 페이지를 나누면 같은 집합을 화면마다 다른
 * 크기로 보게 된다.
 *
 * <p><b>배열을 그대로 내지 않고 객체로 감싸는 이유.</b> 고객 수는 이벤트 수와 달리 도입사의 매출처 규모를 따라 자란다. 지금은 페이지가 필요 없지만 필요해지는 날
 * 최상위가 배열이면 {@code total}이나 {@code page}를 얹을 자리가 없어 응답 모양을 통째로 바꾸게 된다. 객체로 두면 그때 필드만 는다.
 *
 * @param customers 이름 오름차순. 한 명도 없으면 빈 배열이다
 */
public record CustomerListResponse(@JsonProperty("customers") List<CustomerResponse> customers) {

  public static CustomerListResponse from(List<Customer> customers) {
    return new CustomerListResponse(customers.stream().map(CustomerResponse::from).toList());
  }
}
