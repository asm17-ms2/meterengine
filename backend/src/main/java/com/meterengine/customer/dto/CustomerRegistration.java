package com.meterengine.customer.dto;

import com.meterengine.customer.entity.Customer;

public record CustomerRegistration(Customer customer, boolean created) {}
