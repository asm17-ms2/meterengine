package com.meterengine.query.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.pricing.dto.BillableMetricPricePriceRate;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record PriceSearchResponse(
    @Schema(description = "미터 코드") @JsonProperty("billable_metric_code") String billableMetricCode,
    @Schema(description = "미터 이름") @JsonProperty("billable_metric_name") String billableMetricName,
    @Schema(description = "미터의 구분 키 목록입니다. 구분 키가 없으면 빈 배열입니다.")
        @JsonProperty("dimension_properties")
        List<String> dimensionProperties,
    @Schema(description = "그 달에 적용하는 구분 값별 단가입니다. 단가가 없으면 빈 배열입니다.") @JsonProperty("price_rates")
        List<BillableMetricPricePriceRate> priceRates,
    @Schema(description = "미터를 보관한 시각(ISO 8601)입니다. 보관하지 않았으면 null입니다.")
        @JsonProperty("archived_at")
        OffsetDateTime archivedAt,
    @Schema(description = "price_rates에 담은 단가 수") @JsonProperty("rate_count") int rateCount) {}
