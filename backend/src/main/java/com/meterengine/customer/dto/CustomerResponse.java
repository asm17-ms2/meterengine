package com.meterengine.customer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.customer.entity.Customer;
import java.util.UUID;

/**
 * 고객 하나의 응답 표현 (MS2-155).
 *
 * <p>등록과 수정의 응답 본문이자 목록의 원소다. 세 곳이 같은 모양을 쓰므로 화면이 방금 만든 고객과 목록에서 읽은 고객을 구별해 다룰 필요가 없다.
 *
 * <p>필드 이름 {@code customer_id}는 사용량, 청구 예정액, 이벤트 조회 응답이 이미 쓰는 이름이다. 여기서만 {@code id}로 두면 화면이 같은 값을 두
 * 이름으로 다루게 된다.
 */
public record CustomerResponse(
    @JsonProperty("customer_id") UUID customerId, @JsonProperty("name") String name) {

  public static CustomerResponse from(Customer customer) {
    return new CustomerResponse(customer.getId(), customer.getName());
  }
}
