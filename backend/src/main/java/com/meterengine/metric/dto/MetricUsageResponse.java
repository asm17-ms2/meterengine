package com.meterengine.metric.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public record MetricUsageResponse(String month, List<MetricEntry> metrics) {

  public static MetricUsageResponse from(YearMonth month, List<MetricUsage> metricUsages) {
    return new MetricUsageResponse(
        month.toString(), metricUsages.stream().map(MetricEntry::from).toList());
  }

  public record MetricEntry(
      String code,
      String name,
      @JsonProperty("event_type") String eventType,
      String aggregation,
      @JsonProperty("target_property") String targetProperty,
      List<CustomerEntry> customers) {

    static MetricEntry from(MetricUsage usage) {
      return new MetricEntry(
          usage.metric().getCode(),
          usage.metric().getName(),
          usage.metric().getEventType(),
          usage.metric().getAggregation(),
          usage.metric().getTargetProperty(),
          usage.customers().stream().map(CustomerEntry::from).toList());
    }
  }

  public record CustomerEntry(
      @JsonProperty("customer_id") UUID customerId,
      @JsonProperty("customer_name") String customerName,
      BigDecimal quantity) {

    static CustomerEntry from(CustomerUsage usage) {
      return new CustomerEntry(usage.customerId(), usage.customerName(), usage.quantity());
    }
  }
}
