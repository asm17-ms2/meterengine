package com.meterengine.invoice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
      @Schema(description = "확정할 때 저장한 조합. 이 조합의 단가를 수량에 곱했다. 미터에 dimension_properties가 없었으면 빈 객체다")
          @JsonProperty("dimension_values")
          Map<String, Object> dimensionValues,
      BigDecimal quantity,
      @JsonProperty("unit_price") BigDecimal unitPrice,
      long amount) {}
}
