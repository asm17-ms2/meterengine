package com.meterengine.customer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.customer.entity.Customer;
import java.util.List;

public record ListCustomersResponse(@JsonProperty("customers") List<CustomerResponse> customers) {

  public static ListCustomersResponse from(List<Customer> customers) {
    return new ListCustomersResponse(customers.stream().map(CustomerResponse::from).toList());
  }
}
