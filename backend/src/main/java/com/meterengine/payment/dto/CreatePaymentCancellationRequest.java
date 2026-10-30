package com.meterengine.payment.dto;

import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreatePaymentCancellationRequest(
    @Schema(description = "취소할 금액. 원 단위 양의 정수. 빼면 남은 결제 금액 전부를 취소한다") @Positive Long amount,
    @Schema(description = "취소 사유. 토스페이먼츠에 그대로 넘긴다", maxLength = 200)
        @NotBlank @Size(max = 200) @StorableText
        String reason) {}
