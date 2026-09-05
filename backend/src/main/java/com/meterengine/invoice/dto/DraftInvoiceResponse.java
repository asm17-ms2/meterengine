package com.meterengine.invoice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DraftInvoiceResponse(
    String month,
    @JsonProperty("calculated_at") OffsetDateTime calculatedAt,
    @JsonProperty("total_amount") long totalAmount,
    List<DraftInvoiceCustomerEntry> customers) {

  public record DraftInvoiceCustomerEntry(
      @JsonProperty("customer_id") UUID customerId,
      @JsonProperty("customer_name") String customerName,
      long amount,
      List<MetricLineItem> lines) {}

  public record MetricLineItem(
      @JsonProperty("billable_metric_code") String billableMetricCode,
      @JsonProperty("target_property") String targetProperty,
      BigDecimal quantity,
      @JsonProperty("unit_price") BigDecimal unitPrice,
      long amount) {}
}
