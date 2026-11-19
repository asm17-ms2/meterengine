package com.meterengine.metric.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.metric.entity.BillableMetric;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

public record BillableMetricResponse(
    @Schema(description = "과금 미터 코드입니다. 같은 업무 환경 안에서 유일하고 API 경로에 씁니다.") String code,
    @Schema(description = "콘솔과 청구서에 보이는 과금 미터 이름") String name,
    @Schema(description = "집계할 이벤트의 type 값") @JsonProperty("event_type") String eventType,
    @Schema(
            description =
                "집계 함수(소문자)입니다. sum, count, count_distinct, min, max, avg, first, last, median, percentile 가운데 하나입니다.")
        String aggregation,
    @Schema(description = "이벤트 properties에서 집계할 키입니다. count 과금 미터는 null입니다.")
        @JsonProperty("target_property")
        String targetProperty,
    @Schema(description = "단가를 가르는 이벤트 속성 키(구분 키)입니다. 속성에 따라 단가가 갈리지 않으면 빈 배열입니다.")
        @JsonProperty("dimension_properties")
        List<String> dimensionProperties,
    @Schema(description = "보관한 시각(ISO 8601)입니다. 보관하지 않은 과금 미터는 null입니다.")
        @JsonProperty("archived_at")
        java.time.OffsetDateTime archivedAt,
    @Schema(
            description =
                "집계를 시작하는 달(yyyy-MM, KST)입니다. 처음 등록한 과금 미터는 0001-01이고, 복원한 과금 미터는 복원한 다음 달입니다.")
        @JsonProperty("active_from")
        String activeFrom,
    @Schema(description = "집계를 멈추는 달(yyyy-MM, KST)입니다. 보관한 다음 달이고, 보관하지 않은 과금 미터는 null입니다.")
        @JsonProperty("inactive_from")
        String inactiveFrom,
    @Schema(description = "집계량을 나누는 양수 단위") @JsonProperty("unit_size") BigDecimal unitSize,
    @Schema(description = "끝수 처리 방식입니다. none, ceil(올림), floor(내림), round(반올림, 사사오입) 가운데 하나입니다.")
        @JsonProperty("rounding_mode")
        String roundingMode,
    @Schema(description = "끝수 처리할 소수 자릿수(0~12)입니다. rounding_mode가 none이면 0입니다.")
        @JsonProperty("rounding_precision")
        int roundingPrecision,
    @Schema(description = "percentile 집계의 백분위(0~100)입니다. 다른 집계 함수는 null입니다.") BigDecimal percentile,
    @Schema(description = "과금 미터가 바뀔 때마다 오르는 버전입니다. 큰따옴표로 감싸 PATCH의 If-Match에 보냅니다.")
        long version) {

  public static BillableMetricResponse from(BillableMetric billableMetric) {
    return new BillableMetricResponse(
        billableMetric.getCode(),
        billableMetric.getName(),
        billableMetric.getEventType(),
        billableMetric.getAggregation(),
        billableMetric.getTargetProperty(),
        billableMetric.getDimensionProperties(),
        billableMetric.getArchivedAt(),
        billableMetric.getActiveFrom(),
        billableMetric.getInactiveFrom(),
        billableMetric.getUnitSize(),
        billableMetric.getRoundingMode(),
        billableMetric.getRoundingPrecision(),
        billableMetric.getPercentile(),
        billableMetric.getVersion());
  }
}
