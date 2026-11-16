package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.pricing.entity.PricePlan;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record PricePlanResponse(
    @Schema(description = "요금제 버전 ID(UUID)") UUID id,
    @Schema(description = "고객 요금제의 고객 ID(UUID)입니다. 공통 요금제는 null입니다.") @JsonProperty("customer_id")
        UUID customerId,
    @Schema(description = "적용 시작 월(yyyy-MM, KST)") @JsonProperty("effective_from")
        String effectiveFrom,
    @Schema(description = "적용 종료 월(yyyy-MM, KST)입니다. 이 달은 포함하지 않습니다. 기한이 없으면 null입니다.")
        @JsonProperty("effective_until")
        String effectiveUntil,
    @Schema(description = "월 기본료(원)입니다. 청구할 때 10원 미만을 버린 값을 쓰고, 그 달 사용량이 있는 고객에게만 붙습니다.")
        @JsonProperty("base_fee")
        long baseFee,
    @Schema(
            description =
                "월 최소 사용료(원)입니다. 기본료를 뺀 사용료 합이 이보다 적으면 차액을 조정 항목으로 더합니다. 청구할 때 10원 미만을 버린 값을 쓰고, 그 달 사용량이 있는 고객에게만 붙습니다.")
        @JsonProperty("minimum_amount")
        long minimumAmount,
    @Schema(description = "보관한 시각(ISO 8601)입니다. 보관하지 않은 버전은 null입니다.") @JsonProperty("archived_at")
        java.time.OffsetDateTime archivedAt,
    @Schema(description = "보관해 적용을 멈추는 달(yyyy-MM, KST)입니다. 보관하지 않은 버전은 null입니다.")
        @JsonProperty("archived_from")
        String archivedFrom) {
  public static PricePlanResponse from(PricePlan plan) {
    return new PricePlanResponse(
        plan.getId(),
        plan.getCustomerId(),
        plan.getEffectiveFrom(),
        plan.getEffectiveUntil(),
        plan.getBaseFee(),
        plan.getMinimumAmount(),
        plan.getArchivedAt(),
        plan.getArchivedFrom());
  }
}
