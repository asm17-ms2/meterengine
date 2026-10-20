package com.meterengine.customer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.customer.entity.Customer;
import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(
    @NotBlank @Size(max = Customer.NAME_MAX_LENGTH) @StorableText String name,
    @Schema(description = "선택 입력. 확정한 인보이스의 청구 메일을 받을 주소. 없으면 빼거나 null")
        @Email @Pattern(
            regexp = Customer.BILLING_EMAIL_PATTERN,
            message = Customer.BILLING_EMAIL_FORMAT_MESSAGE)
        @Size(max = Customer.BILLING_EMAIL_MAX_LENGTH) @StorableText
        @JsonProperty("billing_email")
        String billingEmail) {}
