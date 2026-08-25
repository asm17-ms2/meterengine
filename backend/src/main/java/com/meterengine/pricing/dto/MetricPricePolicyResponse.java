package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.pricing.entity.PricePolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

public record MetricPricePolicyResponse(
    @JsonProperty("metric_code") String metricCode,
    @Schema(
            nullable = true,
            description = "가격을 가르는 이벤트 속성 키의 선언. 정책이 아직 없는 미터는 null이고, 정책이 있는 무차원 미터는 빈 배열이다")
        @JsonProperty("dimension_properties")
        List<String> dimensionProperties,
    @Schema(
            nullable = true,
            description =
                "무차원 조합('{}')에 붙은 기본 단가. 단가 행이 아직 없으면 null이고, 그 미터는 청구 예정액 계산에서 라인이 빠진다. 0은 무료를 뜻하는 값이라 null과 다르다")
        @JsonProperty("unit_price")
        BigDecimal unitPrice) {

  public static MetricPricePolicyResponse of(
      String metricCode, PricePolicy policy, BigDecimal unitPrice) {
    return policy == null
        ? new MetricPricePolicyResponse(metricCode, null, null)
        : new MetricPricePolicyResponse(metricCode, policy.getDimensionProperties(), unitPrice);
  }
}
