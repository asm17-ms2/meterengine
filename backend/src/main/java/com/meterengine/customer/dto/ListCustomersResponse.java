package com.meterengine.customer.dto;

import com.meterengine.customer.entity.Customer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ListCustomersResponse(
    @Schema(description = "고객 목록입니다. 이름 오름차순이고 없으면 빈 배열입니다.") List<CustomerResponse> customers) {

  public static ListCustomersResponse from(List<Customer> customers) {
    return new ListCustomersResponse(customers.stream().map(CustomerResponse::from).toList());
  }
}
