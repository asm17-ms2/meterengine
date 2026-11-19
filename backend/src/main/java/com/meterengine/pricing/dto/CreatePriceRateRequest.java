package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.validation.FourDigitYear;
import com.meterengine.global.validation.StorableJson;
import com.meterengine.global.validation.StorableJsonValidator;
import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CreatePriceRateRequest(
    @Schema(
            description =
                "구분 값입니다. 키 집합이 과금 미터의 dimension_properties와 같아야 하고, 구분 키가 없는 과금 미터는 빈 객체입니다. 값은 문자열, 숫자, 불리언만 됩니다.")
        @NotNull @StorableJson
        @JsonProperty("dimension_values")
        Map<String, Object> dimensionValues,
    @Schema(description = "과금 수량 1당 단가(원)입니다. per_unit에만 넣으며 0 이상이고 소수도 됩니다.")
        @DecimalMin("0") @Digits(
            integer = StorableJsonValidator.MAX_INTEGER_DIGITS,
            fraction = StorableJsonValidator.MAX_FRACTION_DIGITS)
        @JsonProperty("unit_price")
        BigDecimal unitPrice,
    @Schema(description = "선택 입력입니다. 넣으면 그 고객에게만 쓰는 고객 단가이고, 생략하면 공통 단가입니다.")
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
    @StorableText
        @Pattern(regexp = "per_unit|graduated|volume") @Schema(
            description =
                "단가 모델입니다. per_unit(단일 단가), graduated(누진 구간), volume(총량 구간) 가운데 하나이고 생략하면 per_unit입니다.",
            allowableValues = {"per_unit", "graduated", "volume"})
        String model,
    @Schema(
            description =
                "graduated, volume 모델의 구간(1~100개)입니다. up_to가 커지는 순서로 쓰고 마지막 구간의 up_to는 null로 둡니다. per_unit에는 넣지 않습니다.")
        @Valid @Size(min = 1, max = 100) List<@NotNull PriceRateTier> tiers) {
  public CreatePriceRateRequest(Map<String, Object> dimensionValues, BigDecimal unitPrice) {
    this(dimensionValues, unitPrice, null, null, null, null, null);
  }

  @Schema(hidden = true)
  public boolean isValidPricing() {
    String selected = model == null ? "per_unit" : model;
    if ("per_unit".equals(selected)) return unitPrice != null && tiers == null;
    if (!List.of("graduated", "volume").contains(selected)
        || unitPrice != null
        || tiers == null
        || tiers.isEmpty()) return false;
    BigDecimal previous = BigDecimal.ZERO;
    for (int index = 0; index < tiers.size(); index++) {
      PriceRateTier tier = tiers.get(index);
      if (tier == null || tier.unitPrice() == null || tier.unitPrice().signum() < 0) return false;
      if (index == tiers.size() - 1) return tier.upTo() == null;
      if (tier.upTo() == null || tier.upTo().compareTo(previous) <= 0) return false;
      previous = tier.upTo();
    }
    return false;
  }
}
