package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.validation.FourDigitYear;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.YearMonth;
import java.util.UUID;

public record CreatePricePlanRequest(
    @Schema(
            description =
                "선택 입력입니다. 넣으면 그 고객에게만 쓰는 고객 요금제이고, 생략하면 고객 요금제가 없는 모든 고객에게 쓰는 공통 요금제입니다.")
        @JsonProperty("customer_id")
        UUID customerId,
    @Schema(description = "적용 시작 월(yyyy-MM, KST)입니다. 생략하면 이번 달이고, 이번 달보다 앞이면 거절합니다.")
        @FourDigitYear
        @JsonProperty("effective_from")
        YearMonth effectiveFrom,
    @Schema(description = "적용 종료 월(yyyy-MM, KST)입니다. 이 달은 포함하지 않습니다. 시작 월보다 뒤여야 하고, 생략하면 계속 적용합니다.")
        @FourDigitYear
        @JsonProperty("effective_until")
        YearMonth effectiveUntil,
    @Schema(description = "월 기본료(원, 0 이상의 정수)입니다. 청구할 때 10원 미만을 버린 값을 쓰고, 그 달 사용량이 있는 고객에게만 붙습니다.")
        @NotNull @Min(0) @JsonProperty("base_fee")
        Long baseFee,
    @Schema(
            description =
                "월 최소 사용료(원, 0 이상의 정수)입니다. 필요 없으면 0을 넣습니다. 기본료를 뺀 사용료 합이 이보다 적으면 차액을 조정 항목으로 더합니다. 청구할 때 10원 미만을 버린 값을 쓰고, 그 달 사용량이 있는 고객에게만 붙습니다.")
        @NotNull @Min(0) @JsonProperty("minimum_amount")
        Long minimumAmount) {}
