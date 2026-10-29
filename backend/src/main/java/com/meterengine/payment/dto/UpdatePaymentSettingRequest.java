package com.meterengine.payment.dto;

import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdatePaymentSettingRequest(
    @Schema(
            description = "결제에 쓸 모드. test 또는 live",
            allowableValues = {"test", "live"})
        @NotBlank @Pattern(regexp = "test|live") @StorableText
        String mode) {}
