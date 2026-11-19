package com.meterengine.metric.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record BillableMetricUsageResponse(
    @Schema(description = "과금 미터 코드") String code,
    @Schema(description = "과금 미터 이름") String name,
    @Schema(description = "집계한 이벤트의 type 값") @JsonProperty("event_type") String eventType,
    @Schema(description = "집계 함수(소문자)") String aggregation,
    @Schema(description = "이벤트 properties에서 집계한 키입니다. count 과금 미터는 null입니다.")
        @JsonProperty("target_property")
        String targetProperty,
    @Schema(description = "고객별 사용량입니다. 그 업무 환경의 모든 고객이 이름 오름차순으로 나옵니다.")
        List<BillableMetricUsageCustomer> customers,
    @Schema(description = "집계량을 나눈 양수 단위") @JsonProperty("unit_size") BigDecimal unitSize,
    @Schema(description = "끝수 처리 방식입니다. none, ceil(올림), floor(내림), round(반올림, 사사오입) 가운데 하나입니다.")
        @JsonProperty("rounding_mode")
        String roundingMode,
    @Schema(description = "끝수 처리한 소수 자릿수(0~12)") @JsonProperty("rounding_precision")
        int roundingPrecision,
    @Schema(description = "percentile 집계의 백분위(0~100)입니다. 다른 집계 함수는 null입니다.")
        BigDecimal percentile) {

  public static BillableMetricUsageResponse from(BillableMetricUsage billableMetricUsage) {
    return new BillableMetricUsageResponse(
        billableMetricUsage.billableMetric().getCode(),
        billableMetricUsage.billableMetric().getName(),
        billableMetricUsage.billableMetric().getEventType(),
        billableMetricUsage.billableMetric().getAggregation(),
        billableMetricUsage.billableMetric().getTargetProperty(),
        billableMetricUsage.customers().stream().map(BillableMetricUsageCustomer::from).toList(),
        billableMetricUsage.billableMetric().getUnitSize(),
        billableMetricUsage.billableMetric().getRoundingMode(),
        billableMetricUsage.billableMetric().getRoundingPrecision(),
        billableMetricUsage.billableMetric().getPercentile());
  }

  public record BillableMetricUsageCustomer(
      @Schema(description = "고객 ID(UUID)") @JsonProperty("customer_id") UUID customerId,
      @Schema(description = "지금 고객 이름") @JsonProperty("customer_name") String customerName,
      @Schema(
              description =
                  "고객 전체 이벤트를 집계하고 unit_size 나눗셈과 끝수 처리를 적용한 수량입니다. 구분 값별 수량의 합과 다를 수 있습니다.")
          BigDecimal quantity,
      @Schema(
              description =
                  "구분 값별 수량입니다. dimension_values 오름차순이고, 이벤트가 없는 고객은 빈 배열입니다. 과금 미터에 dimension_properties가 없으면 dimension_values가 빈 객체인 원소 하나입니다.")
          @JsonProperty("dimension_usages")
          List<BillableMetricUsageDimensionUsage> dimensionUsages,
      @Schema(description = "고객 전체 이벤트의 단위 변환 전 집계량") @JsonProperty("raw_quantity")
          BigDecimal rawQuantity,
      @Schema(description = "단위 변환 전 집계량을 분수로 나타낸 분자") @JsonProperty("raw_quantity_numerator")
          BigDecimal rawQuantityNumerator,
      @Schema(description = "단위 변환 전 집계량을 분수로 나타낸 분모(양수)") @JsonProperty("raw_quantity_denominator")
          BigDecimal rawQuantityDenominator) {

    static BillableMetricUsageCustomer from(CustomerUsage customerUsage) {
      return new BillableMetricUsageCustomer(
          customerUsage.customerId(),
          customerUsage.customerName(),
          customerUsage.quantity(),
          customerUsage.dimensionUsages().stream()
              .map(BillableMetricUsageDimensionUsage::from)
              .toList(),
          customerUsage.rawQuantity(),
          customerUsage.rawQuantityNumerator(),
          customerUsage.rawQuantityDenominator());
    }
  }

  public record BillableMetricUsageDimensionUsage(
      @Schema(
              description =
                  "과금 미터의 dimension_properties마다 이벤트 properties에서 뽑은 구분 값입니다. 이벤트에 그 키가 없으면 값이 null입니다.")
          @JsonProperty("dimension_values")
          Map<String, Object> dimensionValues,
      @Schema(description = "그 구분 값의 수량 표시값입니다. 순환소수는 근사치이며 청구에는 정확한 분자와 분모를 씁니다.")
          BigDecimal quantity,
      @Schema(description = "그 구분 값의 단위 변환 전 집계량") @JsonProperty("raw_quantity")
          BigDecimal rawQuantity,
      @Schema(description = "단위 변환 전 집계량을 분수로 나타낸 분자") @JsonProperty("raw_quantity_numerator")
          BigDecimal rawQuantityNumerator,
      @Schema(description = "단위 변환 전 집계량을 분수로 나타낸 분모(양수)") @JsonProperty("raw_quantity_denominator")
          BigDecimal rawQuantityDenominator) {

    static BillableMetricUsageDimensionUsage from(DimensionUsage dimensionUsage) {
      return new BillableMetricUsageDimensionUsage(
          dimensionUsage.dimensionValues(),
          dimensionUsage.quantity(),
          dimensionUsage.rawQuantity(),
          dimensionUsage.rawQuantityNumerator(),
          dimensionUsage.rawQuantityDenominator());
    }
  }
}
