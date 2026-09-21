package com.meterengine.metric.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
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
      @Schema(description = "이벤트 properties의 값을 합산한 수량. 소수 자릿수를 그대로 싣는다") BigDecimal quantity) {

    static BillableMetricUsageCustomer from(CustomerUsage customerUsage) {
      return new BillableMetricUsageCustomer(
          customerUsage.customerId(), customerUsage.customerName(), customerUsage.quantity());
    }
  }
}
