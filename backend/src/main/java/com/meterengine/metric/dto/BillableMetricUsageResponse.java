package com.meterengine.metric.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record BillableMetricUsageResponse(
    String code,
    String name,
    @JsonProperty("event_type") String eventType,
    String aggregation,
    @JsonProperty("target_property") String targetProperty,
    List<BillableMetricUsageCustomer> customers) {

  public static BillableMetricUsageResponse from(BillableMetricUsage billableMetricUsage) {
    return new BillableMetricUsageResponse(
        billableMetricUsage.billableMetric().getCode(),
        billableMetricUsage.billableMetric().getName(),
        billableMetricUsage.billableMetric().getEventType(),
        billableMetricUsage.billableMetric().getAggregation(),
        billableMetricUsage.billableMetric().getTargetProperty(),
        billableMetricUsage.customers().stream().map(BillableMetricUsageCustomer::from).toList());
  }

  public record BillableMetricUsageCustomer(
      @JsonProperty("customer_id") UUID customerId,
      @Schema(description = "집계 시점의 고객 이름") @JsonProperty("customer_name") String customerName,
      @Schema(description = "이벤트 properties의 값을 합산한 수량. 소수 자릿수를 그대로 싣는다. 조합별 수량의 합과 같다")
          BigDecimal quantity,
      @Schema(
              description =
                  "속성 값 조합별 수량. dimension_values 오름차순이고, 이벤트가 없는 고객은 빈 배열이다. 미터에 dimension_properties가 없으면 dimension_values가 빈 객체인 요소 하나다")
          @JsonProperty("dimension_usages")
          List<BillableMetricUsageDimensionUsage> dimensionUsages) {

    static BillableMetricUsageCustomer from(CustomerUsage customerUsage) {
      return new BillableMetricUsageCustomer(
          customerUsage.customerId(),
          customerUsage.customerName(),
          customerUsage.quantity(),
          customerUsage.dimensionUsages().stream()
              .map(BillableMetricUsageDimensionUsage::from)
              .toList());
    }
  }

  public record BillableMetricUsageDimensionUsage(
      @Schema(
              description =
                  "미터의 dimension_properties마다 이벤트 properties에서 뽑은 값. 이벤트에 그 키가 없으면 값이 null이다")
          @JsonProperty("dimension_values")
          Map<String, Object> dimensionValues,
      @Schema(description = "그 조합의 이벤트 properties 값을 합산한 수량") BigDecimal quantity) {

    static BillableMetricUsageDimensionUsage from(DimensionUsage dimensionUsage) {
      return new BillableMetricUsageDimensionUsage(
          dimensionUsage.dimensionValues(), dimensionUsage.quantity());
    }
  }
}
