package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.validation.FourDigitYear;
import com.meterengine.global.validation.StorableJson;
import com.meterengine.global.validation.StorableJsonValidator;
import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
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
    @NotNull @StorableJson @JsonProperty("dimension_values") Map<String, Object> dimensionValues,
    @DecimalMin("0") @Digits(
            integer = StorableJsonValidator.MAX_INTEGER_DIGITS,
            fraction = StorableJsonValidator.MAX_FRACTION_DIGITS)
        @JsonProperty("unit_price")
        BigDecimal unitPrice,
    @JsonProperty("customer_id") UUID customerId,
    @FourDigitYear @JsonProperty("effective_from") YearMonth effectiveFrom,
    @FourDigitYear @JsonProperty("effective_until") YearMonth effectiveUntil,
    @StorableText @Pattern(regexp = "per_unit|graduated|volume") String model,
    @Valid @Size(min = 1, max = 100) List<@NotNull PriceRateTier> tiers) {
  public CreatePriceRateRequest(Map<String, Object> dimensionValues, BigDecimal unitPrice) {
    this(dimensionValues, unitPrice, null, null, null, null, null);
  }

  @AssertTrue(message = "요금 모델에 맞는 단가와 마지막 한도 없는 오름차순 구간이 필요합니다") @Schema(hidden = true)
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
