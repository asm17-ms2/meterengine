package com.meterengine.customer.dto;

import com.meterengine.customer.entity.Customer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCustomerRequest(@NotBlank @Size(max = Customer.NAME_MAX_LENGTH) String name) {}
