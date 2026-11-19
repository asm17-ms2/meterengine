package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record BillableMetricPriceResponse(
    @Schema(description = "과금 미터 코드") @JsonProperty("billable_metric_code")
        String billableMetricCode,
    @Schema(description = "단가를 가르는 이벤트 속성 키(구분 키)입니다. 속성에 따라 단가가 갈리지 않는 과금 미터는 빈 배열입니다.")
        @JsonProperty("dimension_properties")
        List<String> dimensionProperties,
    @Schema(
            description =
                "구분 값별 단가입니다. dimension_values 오름차순이고, 단가가 하나도 없으면 빈 배열이라 그 과금 미터는 청구 예정액에 청구 항목이"
                    + " 나오지 않습니다.")
        @JsonProperty("price_rates")
        List<BillableMetricPricePriceRate> priceRates) {

  public static BillableMetricPriceResponse of(
      String billableMetricCode,
      List<String> dimensionProperties,
      List<BillableMetricPricePriceRate> priceRates) {
    return new BillableMetricPriceResponse(billableMetricCode, dimensionProperties, priceRates);
  }
}
