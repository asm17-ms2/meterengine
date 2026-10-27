package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record BillableMetricPriceResponse(
    @JsonProperty("billable_metric_code") String billableMetricCode,
    @Schema(description = "단가를 가르는 이벤트 속성 키. 속성에 따라 단가가 갈리지 않는 미터는 빈 배열이다")
        @JsonProperty("dimension_properties")
        List<String> dimensionProperties,
    @Schema(
            description =
                "속성 값 조합별 단가. dimension_values 오름차순이고, 단가가 하나도 없으면 빈 배열이라 그 미터는 청구 예정액에 라인이 나오지"
                    + " 않는다")
        @JsonProperty("price_rates")
        List<BillableMetricPricePriceRate> priceRates) {

  public static BillableMetricPriceResponse of(
      String billableMetricCode,
      List<String> dimensionProperties,
      List<BillableMetricPricePriceRate> priceRates) {
    return new BillableMetricPriceResponse(billableMetricCode, dimensionProperties, priceRates);
  }
}
