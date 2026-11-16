package com.meterengine.customer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.customer.entity.Customer;
import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateCustomerRequest(
    @Schema(description = "고객 이름입니다. 비울 수 없고 255자까지입니다.")
        @NotBlank @Size(max = Customer.NAME_MAX_LENGTH) @StorableText
        String name,
    @Schema(
            description =
                "청구 담당 이메일입니다. 저장만 하며 MeterEngine은 이 주소로 메일을 보내지 않습니다. 빼거나 null로 보내면 저장된 값을 지웁니다.")
        @Email @Pattern(
            regexp = Customer.BILLING_EMAIL_PATTERN,
            message = Customer.BILLING_EMAIL_FORMAT_MESSAGE)
        @Size(max = Customer.BILLING_EMAIL_MAX_LENGTH) @StorableText
        @JsonProperty("billing_email")
        String billingEmail) {}
