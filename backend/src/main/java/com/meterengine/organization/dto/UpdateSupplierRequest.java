package com.meterengine.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.validation.StorableText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateSupplierRequest(
    @NotBlank @StorableText
        @Pattern(regexp = "[0-9]{10}") @JsonProperty("business_registration_number")
        String businessRegistrationNumber,
    @NotBlank @Size(max = 255) @StorableText @JsonProperty("representative_name")
        String representativeName,
    @NotBlank @Size(max = 1000) @StorableText @JsonProperty("business_address")
        String businessAddress) {}
