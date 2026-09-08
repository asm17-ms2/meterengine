package com.meterengine.customer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.customer.entity.Customer;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomerResponse(
    @JsonProperty("id") UUID id,
    @JsonProperty("name") String name,
    @JsonProperty("created_at") OffsetDateTime createdAt) {

  public static CustomerResponse from(Customer customer) {
    return new CustomerResponse(customer.getId(), customer.getName(), customer.getCreatedAt());
  }
}
