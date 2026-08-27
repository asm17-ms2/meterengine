package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.pricing.entity.PricePolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record MetricPricePolicyResponse(
    @JsonProperty("metric_code") String metricCode,
    @Schema(
            nullable = true,
            description = "가격을 가르는 이벤트 속성 키의 선언. 정책이 아직 없는 미터는 null이고, 정책이 있는 무차원 미터는 빈 배열이다")
        @JsonProperty("dimension_properties")
        List<String> dimensionProperties) {

  public static MetricPricePolicyResponse of(String metricCode, PricePolicy policy) {
    return new MetricPricePolicyResponse(
        metricCode, policy == null ? null : policy.getDimensionProperties());
  }
}
