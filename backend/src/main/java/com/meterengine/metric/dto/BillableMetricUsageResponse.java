package com.meterengine.metric.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
      @JsonProperty("customer_name") String customerName,
      BigDecimal quantity) {

    static BillableMetricUsageCustomer from(CustomerUsage customerUsage) {
      return new BillableMetricUsageCustomer(
          customerUsage.customerId(), customerUsage.customerName(), customerUsage.quantity());
    }
  }
}
