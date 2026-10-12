package com.meterengine.invoice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
    UUID id,
    @JsonProperty("customer_id") UUID customerId,
    @JsonProperty("customer_name") String customerName,
    String month,
    @JsonProperty("finalized_at") OffsetDateTime finalizedAt,
    @JsonProperty("supply_amount") long supplyAmount,
    @JsonProperty("tax_amount") long taxAmount,
    @JsonProperty("total_amount") long totalAmount,
    List<InvoiceLineResponse> lines) {

  public record InvoiceLineResponse(
      @JsonProperty("billable_metric_code") String billableMetricCode,
      @JsonProperty("target_property") String targetProperty,
      BigDecimal quantity,
      @JsonProperty("unit_price") BigDecimal unitPrice,
      long amount) {}
}
