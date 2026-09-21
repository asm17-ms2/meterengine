package com.meterengine.customer.dto;

import com.meterengine.customer.entity.Customer;
import java.util.List;

public record ListCustomersResponse(List<CustomerResponse> customers) {

  public static ListCustomersResponse from(List<Customer> customers) {
    return new ListCustomersResponse(customers.stream().map(CustomerResponse::from).toList());
  }
}
