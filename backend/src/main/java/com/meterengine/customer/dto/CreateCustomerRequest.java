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
    @Schema(description = "고객 이름입니다. 비울 수 없고 255자까지입니다.")
        @NotBlank @Size(max = Customer.NAME_MAX_LENGTH) @StorableText
        String name,
    @Schema(
            description =
                "청구 담당 이메일입니다. 저장만 하며 MeterEngine은 이 주소로 메일을 보내지 않습니다. 선택 입력이고, 없으면 빼거나 null로 보냅니다.")
        @Email @Pattern(
            regexp = Customer.BILLING_EMAIL_PATTERN,
            message = Customer.BILLING_EMAIL_FORMAT_MESSAGE)
        @Size(max = Customer.BILLING_EMAIL_MAX_LENGTH) @StorableText
        @JsonProperty("billing_email")
        String billingEmail,
    @Schema(
            description =
                "자사 시스템에서 고객을 가리키는 ID입니다. 선택 입력이고 1~255자이며 같은 업무 환경 안에서 고유합니다. 같은 값으로 다시"
                    + " 보내면 새 고객을 만들지 않고 기존 고객을 돌려줍니다. 이때 보낸 name과 billing_email로 바꾸지 않고, 그 고객을 보관했으면 409 customer_archived입니다.")
        @Size(min = 1, max = Customer.EXTERNAL_ID_MAX_LENGTH) @StorableText
        @JsonProperty("external_id")
        String externalId) {}
