package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record BillableMetricPricePriceRate(
    @Schema(description = "이 단가를 쓰는 구분 값입니다. 구분 키가 없는 과금 미터는 빈 객체입니다.")
        @JsonProperty("dimension_values")
        Map<String, Object> dimensionValues,
    @Schema(description = "per_unit 모델의 과금 수량 1당 단가(원)입니다. graduated, volume 모델에서는 null입니다.")
        @JsonProperty("unit_price")
        BigDecimal unitPrice,
    @Schema(description = "단가 버전 ID(UUID)") UUID id,
    @Schema(description = "고객 단가의 고객 ID(UUID)입니다. 공통 단가는 null입니다.") @JsonProperty("customer_id")
        UUID customerId,
    @Schema(description = "적용 시작 월(yyyy-MM, KST)") @JsonProperty("effective_from")
        String effectiveFrom,
    @Schema(description = "적용 종료 월(yyyy-MM, KST)입니다. 이 달은 포함하지 않습니다. 기한이 없으면 null입니다.")
        @JsonProperty("effective_until")
        String effectiveUntil,
    @Schema(
            description = "단가 모델입니다. per_unit은 단일 단가, graduated는 누진 구간, volume은 총량 구간입니다.",
            allowableValues = {"per_unit", "graduated", "volume"})
        String model,
    @JsonRawValue
        @Schema(
            implementation = PriceRateTier[].class,
            description = "graduated, volume 모델의 구간입니다. per_unit이면 null입니다.")
        String tiers,
    @Schema(description = "보관한 시각(ISO 8601)입니다. 보관하지 않은 버전은 null입니다.") @JsonProperty("archived_at")
        java.time.OffsetDateTime archivedAt,
    @Schema(description = "보관해 적용을 멈추는 달(yyyy-MM, KST)입니다. 보관하지 않은 버전은 null입니다.")
        @JsonProperty("archived_from")
        String archivedFrom) {}
