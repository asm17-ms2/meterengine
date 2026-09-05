package com.meterengine.customer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.customer.entity.Customer;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 고객 하나의 응답 표현 (MS2-155).
 *
 * <p>등록과 수정의 응답 본문이자 목록의 원소다. 세 곳이 같은 모양을 쓰므로 화면이 방금 만든 고객과 목록에서 읽은 고객을 구별해 다룰 필요가 없다.
 *
 * <p>{@code created_at}은 세 곳 모두에 실린다 (MS2-171). 레코드가 하나라 등록만 빼거나 목록만 넣는 선택지가 없고, 나눌 근거도 없다. 화면이 이것을
 * 필수로 다뤄도 되는지({@code required}) 는 아직 정하지 않았다. 소비자인 고객 화면(MS2-154)이 미착수라 판단 근거가 없다.
 */
public record CustomerResponse(
    @JsonProperty("id") UUID id,
    @JsonProperty("name") String name,
    @JsonProperty("created_at") OffsetDateTime createdAt) {

  public static CustomerResponse from(Customer customer) {
    return new CustomerResponse(customer.getId(), customer.getName(), customer.getCreatedAt());
  }
}
