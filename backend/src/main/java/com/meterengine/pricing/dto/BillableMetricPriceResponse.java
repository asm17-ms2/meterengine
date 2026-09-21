package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.pricing.entity.PricePolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

public record BillableMetricPriceResponse(
    @JsonProperty("billable_metric_code") String billableMetricCode,
    @Schema(
            nullable = true,
            description = "단가를 가르는 이벤트 속성 키. 가격 정책이 없는 미터는 null이고, 속성에 따라 단가가 갈리지 않는 미터는 빈 배열이다")
        @JsonProperty("dimension_properties")
        List<String> dimensionProperties,
    @Schema(
            nullable = true,
            description =
                "속성 조건 없이 적용되는 기본 단가. 없으면 null이고, 그 미터는 청구 예정액에 라인이 나오지 않는다. 0은 무료라는 뜻이라 null과 다르다")
        @JsonProperty("unit_price")
        BigDecimal unitPrice) {

  public static BillableMetricPriceResponse of(
      String billableMetricCode, PricePolicy pricePolicy, BigDecimal unitPrice) {
    return pricePolicy == null
        ? new BillableMetricPriceResponse(billableMetricCode, null, null)
        : new BillableMetricPriceResponse(
            billableMetricCode, pricePolicy.getDimensionProperties(), unitPrice);
  }
}
