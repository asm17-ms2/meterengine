package com.meterengine.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SupplierResponse(
    @JsonProperty("business_registration_number") String businessRegistrationNumber,
    @JsonProperty("representative_name") String representativeName,
    @JsonProperty("business_address") String businessAddress) {}
