package com.meterengine.customer.dto;

import com.meterengine.customer.entity.Customer;
import com.meterengine.global.validation.StorableText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCustomerRequest(
    @NotBlank @Size(max = Customer.NAME_MAX_LENGTH) @StorableText String name) {}
